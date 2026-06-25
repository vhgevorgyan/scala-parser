package scala.parser.core.reduction;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ast.*;

public class SmallStepReducer {

    public static boolean isValue(ExpressionNode expr) {
        if (expr instanceof LambdaNode) return true;
        if (expr instanceof LiteralNode) return true;
        if (expr instanceof BoolNode) return true;
        if (expr instanceof StringLiteralNode) return true;
        if (expr instanceof UnitLiteralNode) return true;
        if (expr instanceof TupleNode tup) {
            return tup.elements.stream().allMatch(SmallStepReducer::isValue);
        }
        // Constructor call: K(v₁, ..., vₙ) via curried application
        if (isConstructorValue(expr)) return true;
        return false;
    }

    private static boolean isConstructorValue(ExpressionNode expr) {
        // Match K(v₁, ..., vₙ) as app(K(v₁, ...), vₙ) with K identifier
        if (expr instanceof ApplicationNode app) {
            return isValue(app.argument) && isConstructorValue(app.function);
        }
        // Base: K as identifier
        return expr instanceof IdentifierNode;
    }

    public static ExpressionNode step(ExpressionNode expr) {
        if (isValue(expr)) throw new RuntimeException("Already a value: " + expr);

        // Beta: (λx. e₁) v → [x ↦ v] e₁
        if (expr instanceof ApplicationNode app) {
            if (app.function instanceof LambdaNode lam && isValue(app.argument)) {
                return Substitution.subst(lam.param, app.argument, lam.body);
            }
            // App-Left
            if (!(app.function instanceof LambdaNode) && !isValue(app.function)) {
                return new ApplicationNode(step(app.function), app.argument, app.line, app.column);
            }
            // App-Right
            if (!isValue(app.argument)) {
                return new ApplicationNode(app.function, step(app.argument), app.line, app.column);
            }
        }

        // If
        if (expr instanceof IfNode i) {
            if (!isValue(i.condition)) {
                return new IfNode(step(i.condition), i.thenBranch, i.elseBranch);
            }
            if (i.condition instanceof BoolNode b) {
                if (b.value) return i.thenBranch;
                return i.elseBranch != null ? i.elseBranch : new UnitLiteralNode(i.line, i.column);
            }
        }

        // Binary operators
        if (expr instanceof BinaryExprNode bin) {
            if (!isValue(bin.left)) {
                return new BinaryExprNode(step(bin.left), bin.operator, bin.right, bin.line, bin.column);
            }
            if (!isValue(bin.right)) {
                return new BinaryExprNode(bin.left, bin.operator, step(bin.right), bin.line, bin.column);
            }
            return reduceOp(bin);
        }

        // Unary operators
        if (expr instanceof UnaryExprNode un) {
            if (!isValue(un.operand)) {
                return new UnaryExprNode(un.operator, step(un.operand), un.line, un.column);
            }
            return reduceUnary(un);
        }

        // Ascription: (e : τ) → e
        if (expr instanceof AscriptionNode asc) {
            return asc.expr;
        }

        // Tuple: reduce leftmost non-value
        if (expr instanceof TupleNode tup) {
            List<ExpressionNode> newEls = new ArrayList<>(tup.elements);
            for (int i = 0; i < newEls.size(); i++) {
                if (!isValue(newEls.get(i))) {
                    newEls.set(i, step(newEls.get(i)));
                    return new TupleNode(newEls, tup.line, tup.column);
                }
            }
        }

        // Let: let x = v in body  — requires body from surrounding context
        // (not directly reducible as expression alone; handled by evaluation)

        // Case / Pattern Match
        if (expr instanceof CaseNode c) {
            if (!isValue(c.expr)) {
                return new CaseNode(step(c.expr), c.branches, c.line, c.column);
            }
            return reduceMatch(c.expr, c.branches, c.line, c.column);
        }

        throw new RuntimeException("Stuck term: " + expr.getClass().getSimpleName() + " = " + expr);
    }

    public static ExpressionNode reduce(ExpressionNode expr) {
        ExpressionNode current = expr;
        int limit = 10000;
        while (!isValue(current) && limit-- > 0) {
            try {
                current = step(current);
            } catch (RuntimeException e) {
                break;
            }
        }
        return current;
    }

    private static ExpressionNode reduceOp(BinaryExprNode bin) {
        if (bin.left instanceof LiteralNode l && bin.right instanceof LiteralNode r) {
            int a = Integer.parseInt(l.value), b = Integer.parseInt(r.value);
            return switch (bin.operator) {
                case "+" -> lit(a + b, l);
                case "-" -> lit(a - b, l);
                case "*" -> lit(a * b, l);
                case "/" -> lit(a / b, l);
                case "==" -> bln(a == b, l);
                case "!=" -> bln(a != b, l);
                case "<" -> bln(a < b, l);
                case ">" -> bln(a > b, l);
                case "<=" -> bln(a <= b, l);
                case ">=" -> bln(a >= b, l);
                case ".." -> new TupleNode(List.of(bin.left, bin.right), l.line, l.column);
                default -> throw new RuntimeException("Unknown op: " + bin.operator);
            };
        }
        if (bin.left instanceof BoolNode l && bin.right instanceof BoolNode r) {
            return switch (bin.operator) {
                case "&&" -> bln(l.value && r.value, l);
                case "||" -> bln(l.value || r.value, l);
                case "==" -> bln(l.value == r.value, l);
                case "!=" -> bln(l.value != r.value, l);
                default -> throw new RuntimeException("Unknown bool op: " + bin.operator);
            };
        }
        throw new RuntimeException("Cannot reduce " + bin.operator);
    }

    private static ExpressionNode reduceUnary(UnaryExprNode un) {
        if (un.operator.equals("-") && un.operand instanceof LiteralNode l) {
            return lit(-Integer.parseInt(l.value), l);
        }
        if (un.operator.equals("!") && un.operand instanceof BoolNode b) {
            return bln(!b.value, b);
        }
        throw new RuntimeException("Cannot reduce unary " + un.operator);
    }

    private static ExpressionNode reduceMatch(ExpressionNode scrutinee, List<CaseNode.CaseBranch> branches, int line, int col) {
        for (CaseNode.CaseBranch branch : branches) {
            // Wildcard
            if (branch.constructor() == null && branch.literalPattern() == null) {
                return branch.body();
            }
            // Literal pattern
            if (branch.literalPattern() != null) {
                if (astEqual(scrutinee, branch.literalPattern())) {
                    return branch.body();
                }
                continue;
            }
            // Constructor name
            if (branch.constructor() != null) {
                String ctorName = branch.constructor();
                // Check if scrutinee starts with matching constructor (curried form)
                if (scrutinee instanceof ApplicationNode app) {
                    ExpressionNode ctor = getConstructor(app);
                    if (ctor instanceof IdentifierNode id && id.name.equals(ctorName)) {
                        // Extract bindings
                        List<ExpressionNode> args = getConstructorArgs(app);
                        // Substitute bindings into body
                        return SubstBindings(branch, args, branch.body());
                    }
                }
                if (scrutinee instanceof IdentifierNode id && id.name.equals(ctorName)) {
                    // Zero-arg constructor matches
                    return branch.body();
                }
            }
        }
        throw new RuntimeException("Match failed: no branch matches " + scrutinee);
    }

    private static ExpressionNode getConstructor(ExpressionNode expr) {
        if (expr instanceof ApplicationNode app) return getConstructor(app.function);
        return expr;
    }

    private static List<ExpressionNode> getConstructorArgs(ExpressionNode expr) {
        List<ExpressionNode> args = new ArrayList<>();
        ExpressionNode current = expr;
        while (current instanceof ApplicationNode app) {
            args.add(0, app.argument);
            current = app.function;
        }
        return args;
    }

    private static ExpressionNode SubstBindings(CaseNode.CaseBranch branch, List<ExpressionNode> args, ExpressionNode body) {
        ExpressionNode result = body;
        int n = Math.min(branch.bindings().size(), args.size());
        for (int i = 0; i < n; i++) {
            result = Substitution.subst(branch.bindings().get(i).name(), args.get(i), result);
        }
        return result;
    }

    private static boolean astEqual(ExpressionNode a, ExpressionNode b) {
        if (a instanceof LiteralNode la && b instanceof LiteralNode lb) return la.value.equals(lb.value);
        if (a instanceof BoolNode ba && b instanceof BoolNode bb) return ba.value == bb.value;
        if (a instanceof StringLiteralNode sa && b instanceof StringLiteralNode sb) return sa.value.equals(sb.value);
        return false;
    }

    private static LiteralNode lit(int n, ExpressionNode ref) {
        return new LiteralNode(String.valueOf(n), ref.line, ref.column);
    }

    private static BoolNode bln(boolean v, ExpressionNode ref) {
        return new BoolNode(ref.line, ref.column, v);
    }
}
