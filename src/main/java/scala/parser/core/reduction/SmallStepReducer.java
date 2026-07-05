package scala.parser.core.reduction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scala.parser.core.ast.*;

public class SmallStepReducer {

    private static final Set<String> BUILTINS = Set.of("succ", "pred", "iszero", "print");

    private static boolean isBuiltin(String name) {
        return BUILTINS.contains(name);
    }

    public static boolean isValue(ExpressionNode expr) {
        if (expr instanceof LambdaNode) return true;
        if (expr instanceof LiteralNode) return true;
        if (expr instanceof BoolNode) return true;
        if (expr instanceof StringLiteralNode) return true;
        if (expr instanceof UnitLiteralNode) return true;
        if (expr instanceof IdentifierNode id && isBuiltin(id.name)) return true;
        if (expr instanceof TupleNode tup) {
            return tup.elements.stream().allMatch(SmallStepReducer::isValue);
        }
        // Constructor call: K(v₁, ..., vₙ) via curried application
        if (isConstructorValue(expr)) return true;
        // Fixpoint is not a value — it must unroll first
        if (expr instanceof FixpointNode) return false;
        return false;
    }

    private static boolean isConstructorValue(ExpressionNode expr) {
        // Match K(v₁, ..., vₙ) as app(K(v₁, ...), vₙ) with K identifier
        if (expr instanceof ApplicationNode app) {
            return isValue(app.argument) && isConstructorValue(app.function);
        }
        // Base: K as identifier (but not a builtin — builtins are not constructors)
        return expr instanceof IdentifierNode id && !isBuiltin(id.name);
    }

    private static ExpressionNode applyBuiltin(String name, ExpressionNode arg) {
        if (name.equals("print")) {
            System.out.println(exprToDisplayString(arg));
            return arg;
        }
        if (arg instanceof LiteralNode l) {
            int n = Integer.parseInt(l.value);
            return switch (name) {
                case "succ" -> lit(n + 1, l);
                case "pred" -> lit(n - 1, l);
                case "iszero" -> bln(n == 0, l);
                default -> throw new RuntimeException("Unknown builtin: " + name);
            };
        }
        throw new RuntimeException("Builtin " + name + " expects a number, got " + arg.getClass().getSimpleName());
    }

    private static String exprToDisplayString(ExpressionNode expr) {
        if (expr instanceof LiteralNode l) return l.value;
        if (expr instanceof BoolNode b) return String.valueOf(b.value);
        if (expr instanceof StringLiteralNode s) return "\"" + s.value + "\"";
        if (expr instanceof UnitLiteralNode) return "()";
        if (expr instanceof IdentifierNode id) return id.name;
        if (expr instanceof LambdaNode) return "<closure>";
        if (expr instanceof TupleNode t) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < t.elements.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(exprToDisplayString(t.elements.get(i)));
            }
            return sb.append(")").toString();
        }
        return expr.toString();
    }

    public static ExpressionNode step(ExpressionNode expr) {
        if (isValue(expr)) throw new RuntimeException("Already a value: " + expr);

        // Beta: (λx. e₁) v → [x ↦ v] e₁
        if (expr instanceof ApplicationNode app) {
            if (app.function instanceof LambdaNode lam && isValue(app.argument)) {
                return Substitution.subst(lam.param, app.argument, lam.body);
            }
            // Builtin application: succ(5) → 6
            if (app.function instanceof IdentifierNode id && isBuiltin(id.name) && isValue(app.argument)) {
                return applyBuiltin(id.name, app.argument);
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
            throw new RuntimeException("If condition must be Bool, got: " + i.condition);
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

        // Fixpoint: fix(λx. body) → subst x (fix(λx. body)) body
        if (expr instanceof FixpointNode fp) {
            if (fp.body instanceof LambdaNode lam) {
                return Substitution.subst(lam.param, expr, lam.body);
            }
            throw new RuntimeException("Fixpoint body must be a lambda: " + fp.body);
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

        // Let: not directly reducible as expression alone (not an ExpressionNode)

        // Block: let x = e1; ...; en  →  step or substitute
        if (expr instanceof BlockNode block) {
            return reduceBlock(block);
        }

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
            current = step(current);
        }
        return current;
    }

    private static ExpressionNode reduceBlock(BlockNode block) {
        List<ASTNode> stmts = block.statements;
        if (stmts.isEmpty()) {
            return new UnitLiteralNode(block.line, block.column);
        }

        // Find first non-value let, or check last expression
        for (int i = 0; i < stmts.size(); i++) {
            if (stmts.get(i) instanceof LetStatementNode let) {
                ExpressionNode val = let.value;
                if (!isValue(val)) {
                    // Step the let's value
                    List<ASTNode> newStmts = new ArrayList<>(stmts);
                    newStmts.set(i, new LetStatementNode(let.name, step(val), let.isRec, let.declaredType, let.line, let.column));
                    return new BlockNode(newStmts, block.line, block.column);
                }
                // Let has a value — substitute in all subsequent statements
                ExpressionNode substVal = val;
                if (let.isRec && val instanceof LambdaNode lam) {
                    // Wrap in FixpointNode:
                    //   let rec f = λx. body
                    //   becomes f ↦ fix(λf. λx. body) in subsequent statements
                    // FixpointNode unrolls: fix(λf. λx. body) → λx. body[f ↦ fix(λf. λx. body)]
                    // The outer λf introduces the recursive self-reference.
                    substVal = new FixpointNode(
                        new LambdaNode(let.name, val, let.line, let.column),
                        let.line, let.column);
                }
                List<ASTNode> rest = stmts.subList(i + 1, stmts.size());
                List<ASTNode> substituted = new ArrayList<>();
                for (ASTNode s : rest) {
                    substituted.add(substInNode(let.name, substVal, s));
                }
                // If there was a declared type, we checked it during typecheck;
                // substitution replaces the name with the literal type
                return new BlockNode(substituted, block.line, block.column);
            }
            if (stmts.get(i) instanceof MutualLetNode mutual) {
                // Step the first non-value body, or substitute all when all are values
                List<LetStatementNode> defs = mutual.defs;
                boolean anyStepped = false;
                for (int j = 0; j < defs.size(); j++) {
                    ExpressionNode val = defs.get(j).value;
                    if (!isValue(val)) {
                        List<LetStatementNode> newDefs = new ArrayList<>(defs);
                        LetStatementNode old = defs.get(j);
                        newDefs.set(j, new LetStatementNode(old.name, step(val), old.isRec, old.declaredType, old.line, old.column));
                        List<ASTNode> newStmts = new ArrayList<>(stmts);
                        newStmts.set(i, new MutualLetNode(newDefs, mutual.line, mutual.column));
                        return new BlockNode(newStmts, block.line, block.column);
                    }
                }
                // All values: wrap recursive lambdas in FixpointNodes,
                // cross-substitute all mutual names, then substitute in subsequent statements
                // 1) Create self-recursive fixpoints for all names
                Map<String, ExpressionNode> wraps = new HashMap<>();
                List<String> names = defs.stream().map(d -> d.name).toList();
                for (LetStatementNode def : defs) {
                    if (def.isRec && def.value instanceof LambdaNode) {
                        wraps.put(def.name, new FixpointNode(
                            new LambdaNode(def.name, def.value, def.line, def.column),
                            def.line, def.column));
                    } else {
                        wraps.put(def.name, def.value);
                    }
                }
                // 2) Cross-substitute into original lambda bodies (before FixpointNode wrapping),
                //    then wrap each in FixpointNode. This avoids alpha-renaming the fixpoint's
                //    own binding (the freevars of the other fixpoints may contain our name).
                Map<String, ExpressionNode> finalWraps = new HashMap<>();
                for (LetStatementNode def : defs) {
                    if (def.isRec && def.value instanceof LambdaNode) {
                        ExpressionNode val = def.value;
                        for (String otherName : names) {
                            if (!otherName.equals(def.name)) {
                                val = Substitution.subst(otherName, wraps.get(otherName), val);
                            }
                        }
                        finalWraps.put(def.name, new FixpointNode(
                            new LambdaNode(def.name, val, def.line, def.column),
                            def.line, def.column));
                    } else {
                        finalWraps.put(def.name, def.value);
                    }
                }
                List<ASTNode> rest = stmts.subList(i + 1, stmts.size());
                List<ASTNode> substituted = new ArrayList<>();
                for (ASTNode s : rest) {
                    for (LetStatementNode def : defs) {
                        s = substInNode(def.name, finalWraps.get(def.name), s);
                    }
                    substituted.add(s);
                }
                return new BlockNode(substituted, block.line, block.column);
            }
            // Expression statement
            if (stmts.get(i) instanceof ExpressionNode e) {
                if (!isValue(e)) {
                    // Step the expression
                    List<ASTNode> newStmts = new ArrayList<>(stmts);
                    newStmts.set(i, step(e));
                    return new BlockNode(newStmts, block.line, block.column);
                }
                // Expression is a value — if it's the last statement, unwrap
                if (i == stmts.size() - 1) {
                    return e;
                }
                // Otherwise skip it and continue
            }
        }
        // All values — should not reach here if outermost was unwrapped
        throw new RuntimeException("Block completely reduced: " + block);
    }

    private static ASTNode substInNode(String name, ExpressionNode value, ASTNode node) {
        return Substitution.substInBlockStatement(name, value, node);
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
        if (bin.left instanceof StringLiteralNode l && bin.right instanceof StringLiteralNode r) {
            return switch (bin.operator) {
                case "+" -> new StringLiteralNode(l.value + r.value, l.line, l.column);
                case "==" -> bln(l.value.equals(r.value), l);
                case "!=" -> bln(!l.value.equals(r.value), l);
                default -> throw new RuntimeException("Unknown string op: " + bin.operator);
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
        for (int bi = 0; bi < branches.size(); bi++) {
            CaseNode.CaseBranch branch = branches.get(bi);
            boolean patternMatches = false;
            ExpressionNode body = branch.body();
            ExpressionNode guard = branch.guard();

            if (branch.constructor() == null && branch.literalPattern() == null) {
                patternMatches = true;
            } else if (branch.literalPattern() != null) {
                if (astEqual(scrutinee, branch.literalPattern())) {
                    patternMatches = true;
                }
            } else if (branch.constructor() != null) {
                String ctorName = branch.constructor();
                if (scrutinee instanceof ApplicationNode app) {
                    ExpressionNode ctor = getConstructor(app);
                    if (ctor instanceof IdentifierNode id && id.name.equals(ctorName)) {
                        patternMatches = true;
                        List<ExpressionNode> args = getConstructorArgs(app);
                        body = SubstBindings(branch, args, body);
                        if (guard != null) guard = SubstBindings(branch, args, guard);
                    }
                } else if (scrutinee instanceof IdentifierNode id && id.name.equals(ctorName)) {
                    patternMatches = true;
                }
            }

            if (!patternMatches) continue;

            if (branch.asName() != null) {
                body = Substitution.subst(branch.asName(), scrutinee, body);
                if (guard != null) guard = Substitution.subst(branch.asName(), scrutinee, guard);
            }

            if (guard != null) {
                // Try stepping the guard one step
                ExpressionNode steppedGuard;
                try {
                    steppedGuard = step(guard);
                } catch (Exception e) {
                    continue;
                }
                if (!steppedGuard.equals(guard)) {
                    List<CaseNode.CaseBranch> remaining = new ArrayList<>();
                    remaining.add(new CaseNode.CaseBranch(null, List.of(), body, null, null, steppedGuard));
                    remaining.addAll(branches.subList(bi + 1, branches.size()));
                    return new CaseNode(scrutinee, remaining, line, col);
                }
                // Guard is a value — check truthiness
                if (guard instanceof BoolNode bn && !bn.value) {
                    continue;
                }
            }
            return body;
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
