package scala.parser.core.reduction;

import java.util.*;
import java.util.function.*;
import net.jqwik.api.*;
import static org.junit.jupiter.api.Assertions.*;
import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.ast.CaseNode.*;
import scala.parser.core.interpreter.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.*;

/**
 * Coverage-Guided PBT: generators target undetected mutation classes
 * to ensure the pipeline catches them (type error, runtime error, or output change).
 */
public class CoverageGuidedPBT {

    private static final Random SHARED = new Random(42);

    // ── SwapIfBranches: if (c) a else b  with a != b ──────────────

    @Provide
    Arbitrary<ProgramNode> ifWithDistinctBranches() {
        return Arbitraries.randomValue(rand -> {
            int a = rand.nextInt(100), b;
            do { b = rand.nextInt(100); } while (b == a);
            String src = "if (true) " + a + " else " + b;
            return parseOrNull(src);
        }).filter(Objects::nonNull);
    }

    @Property(tries = 200)
    void swapIfBranchesChangesOutput(@ForAll("ifWithDistinctBranches") ProgramNode prog) {
        var orig = evaluate(prog);
        var swapped = swapIfBranches(prog);
        if (swapped == null) return;
        var mut = evaluate(swapped);
        assertNotNull(orig);
        assertNotNull(mut);
        assertFalse(valuesEqual(orig, mut),
            "Swapped if-branches must produce different output: " + prog);
    }

    // ── SwapOperator: non-commutative binops ──────────────────────

    @Provide
    Arbitrary<ProgramNode> nonCommutativeBinop() {
        return Arbitraries.randomValue(rand -> {
            int a = rand.nextInt(1, 50), b = rand.nextInt(1, 50);
            boolean isDiv = rand.nextBoolean();
            // Avoid a / 1 == a * 1 (same output after swap)
            if (isDiv && b == 1) b = 2;
            String src = isDiv ? (a + " / " + b) : (a + " - " + b);
            return parseOrNull(src);
        }).filter(Objects::nonNull);
    }

    @Property(tries = 200)
    void swapOperatorChangesOutput(@ForAll("nonCommutativeBinop") ProgramNode prog) {
        var orig = evaluate(prog);
        var swapped = swapOperator(prog);
        if (swapped == null) return;
        var mut = evaluate(swapped);
        assertNotNull(orig, "Original must evaluate: " + prog);
        assertNotNull(mut, "Mutated must evaluate: " + swapped);
        assertFalse(valuesEqual(orig, mut),
            "Swapped operator must produce different output: " + prog);
    }

    // ── RemoveLetBinding: val x = e; body where x used ────────────

    @Provide
    Arbitrary<ProgramNode> letBindingUsedInBody() {
        return Arbitraries.randomValue(rand -> {
            int val = rand.nextInt(100);
            String src = "val x = " + val + "; x + 1";
            return parseOrNull(src);
        }).filter(Objects::nonNull);
    }

    @Provide
    Arbitrary<ProgramNode> letBindingChained() {
        return Arbitraries.randomValue(rand -> {
            int a = rand.nextInt(100), b = rand.nextInt(100);
            return switch (rand.nextInt(4)) {
                case 0 -> parseOrNull("val x = " + a + "; val y = x + " + b + "; y");
                case 1 -> parseOrNull("val x = " + a + "; val y = " + b + "; x + y");
                case 2 -> parseOrNull("val x = " + a + "; val y = x * 2; val z = y + " + b + "; z");
                default -> parseOrNull("val f = (n: Int) => n + " + a + "; f(" + b + ") + " + a);
            };
        }).filter(Objects::nonNull);
    }

    @Property(tries = 200)
    void removeLetBindingCausesError(@ForAll("letBindingUsedInBody") ProgramNode prog) {
        var removed = removeLetBinding(prog);
        if (removed == null) return;
        var mut = evaluate(removed);
        assertNull(mut,
            "Removing a used let-binding must cause an error (undefined variable)");
    }

    @Property(tries = 200)
    void removeChainedLetBindingCausesError(@ForAll("letBindingChained") ProgramNode prog) {
        var removed = removeLetBinding(prog);
        if (removed == null) return;
        var mut = evaluate(removed);
        assertNull(mut,
            "Removing a chained let-binding must cause an error (cascading undefined)");
    }

    // ── UndefinedVariable: replace used ident with bad name ───────

    @Provide
    Arbitrary<ProgramNode> exprWithIdentifier() {
        return Arbitraries.randomValue(rand -> {
            int v = rand.nextInt(100);
            String src = rand.nextBoolean() ? ("val y = " + v + "; y") : ("val y = " + v + "; y + 1");
            return parseOrNull(src);
        }).filter(Objects::nonNull);
    }

    @Provide
    Arbitrary<ProgramNode> exprWithIdentComplex() {
        return Arbitraries.randomValue(rand -> {
            int v = rand.nextInt(100), w = rand.nextInt(100);
            return switch (rand.nextInt(5)) {
                case 0 -> parseOrNull("val x = " + v + "; val y = x + " + w + "; y * 2");
                case 1 -> parseOrNull("val x = " + v + "; if (x > 0) x else " + w);
                case 2 -> parseOrNull("val f = (n: Int) => n + " + v + "; f(" + w + ")");
                case 3 -> parseOrNull("val x = " + v + "; val y = " + w + "; x + y");
                default -> parseOrNull("val x = " + v + "; (x: Int)");
            };
        }).filter(Objects::nonNull);
    }

    @Property(tries = 200)
    void undefinedVariableCausesError(@ForAll("exprWithIdentifier") ProgramNode prog) {
        var broken = replaceIdentWithUndefined(prog);
        if (broken == null) return;
        var mut = evaluate(broken);
        assertNull(mut,
            "Replacing identifier with undefined name must cause an error");
    }

    @Property(tries = 200)
    void undefinedVariableInComplexExpr(@ForAll("exprWithIdentComplex") ProgramNode prog) {
        var broken = replaceIdentWithUndefined(prog);
        if (broken == null) return;
        var mut = evaluate(broken);
        assertNull(mut,
            "Replacing identifier with undefined name in complex expr must cause an error");
    }

    // ── Generic: mutation-specific PBT ───────────────────────────

    @Property(tries = 50)
    void swapOperatorChangesOutputBulk(@ForAll("nonCommutativeBinop") ProgramNode prog) {
        var mutated = swapOperator(prog);
        if (mutated == null) return;
        var origVal = evaluate(prog);
        var mutVal = evaluate(mutated);
        assertNotNull(origVal, "Original must evaluate: " + prog);
        assertNotNull(mutVal, "Mutated must evaluate: " + mutated);
        assertFalse(valuesEqual(origVal, mutVal),
            "SwapOperator must produce different output");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static ProgramNode parseOrNull(String src) {
        try {
            Tokenizer t = new Tokenizer(src);
            Parser p = new Parser(t.tokenize());
            ParserResult pr = p.parse();
            if (!pr.errors.isEmpty()) return null;
            return pr.ast;
        } catch (Exception e) {
            return null;
        }
    }

    private static Value evaluate(ProgramNode prog) {
        if (prog == null) return null;
        try {
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(prog);
            if (!errors.isEmpty()) return null;
            return new Evaluator().evalProgram(prog);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean valuesEqual(Value a, Value b) {
        return valueToString(a).equals(valueToString(b));
    }

    private static String valueToString(Value v) {
        if (v instanceof NumberValue n) return Integer.toString(n.value);
        if (v instanceof BoolValue b) return Boolean.toString(b.value);
        if (v instanceof StringValue s) return s.value;
        if (v instanceof UnitValue) return "()";
        if (v instanceof TupleValue t) {
            var parts = t.values.stream().map(CoverageGuidedPBT::valueToString).toList();
            return "(" + String.join(", ", parts) + ")";
        }
        if (v instanceof ConstructorValue c) {
            var args = c.args.stream().map(CoverageGuidedPBT::valueToString).toList();
            return c.name + (args.isEmpty() ? "" : "(" + String.join(", ", args) + ")");
        }
        return v.toString();
    }

    private static ProgramNode swapIfBranches(ProgramNode prog) {
        return transformAST(prog, node -> {
            if (node instanceof IfNode i && i.elseBranch != null) {
                return new IfNode(i.condition, i.elseBranch, i.thenBranch);
            }
            return null;
        });
    }

    private static ProgramNode swapOperator(ProgramNode prog) {
        return transformAST(prog, node -> {
            if (node instanceof BinaryExprNode b) {
                String op = b.operator;
                String swapped = switch (op) {
                    case "+" -> "-"; case "-" -> "+";
                    case "*" -> "/"; case "/" -> "*";
                    case "==" -> "!="; case "!=" -> "==";
                    case "<" -> ">"; case ">" -> "<";
                    case "<=" -> ">="; case ">=" -> "<=";
                    case "&&" -> "||"; case "||" -> "&&";
                    default -> null;
                };
                if (swapped != null)
                    return new BinaryExprNode(b.left, swapped, b.right, b.line, b.column);
            }
            return null;
        });
    }

    private static ProgramNode removeLetBinding(ProgramNode prog) {
        return transformAST(prog, node -> {
            if (node instanceof BlockNode block) {
                for (int i = 0; i < block.statements.size(); i++) {
                    if (block.statements.get(i) instanceof LetStatementNode) {
                        var newStmts = new ArrayList<>(block.statements);
                        newStmts.remove(i);
                        if (newStmts.isEmpty()) return new UnitLiteralNode(0, 0);
                        // If last statement is a LetStatementNode, wrap in unit
                        if (newStmts.get(newStmts.size() - 1) instanceof LetStatementNode) {
                            newStmts.add(new UnitLiteralNode(0, 0));
                        }
                        return new BlockNode(newStmts, block.line, block.column);
                    }
                }
            }
            return null;
        });
    }

    private static ProgramNode replaceIdentWithUndefined(ProgramNode prog) {
        return transformAST(prog, node -> {
            if (node instanceof IdentifierNode id && !id.name.equals("succ")
                && !id.name.equals("pred") && !id.name.equals("iszero") && !id.name.equals("print")) {
                return new IdentifierNode("__UNDEFINED__" + SHARED.nextInt(10000), id.line, id.column);
            }
            return null;
        });
    }

    private static ProgramNode transformAST(ProgramNode root, Function<ASTNode, ASTNode> mutation) {
        var newStmts = new ArrayList<ASTNode>();
        boolean mutated = false;
        for (var stmt : root.statements) {
            var result = transformRec(stmt, mutation);
            if (result != stmt) mutated = true;
            newStmts.add(result);
        }
        return mutated ? new ProgramNode(newStmts) : null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends ASTNode> T transformRec(T node, Function<ASTNode, ASTNode> mutation) {
        ASTNode mutated = mutation.apply(node);
        if (mutated != null) return (T) mutated;

        if (node instanceof BlockNode b) {
            var newStmts = new ArrayList<ASTNode>();
            boolean changed = false;
            for (var s : b.statements) {
                var ns = transformRec(s, mutation);
                newStmts.add(ns);
                if (ns != s) changed = true;
            }
            return changed ? (T) new BlockNode(newStmts, b.line, b.column) : node;
        }
        if (node instanceof ProgramNode p) {
            var newStmts = new ArrayList<ASTNode>();
            boolean changed = false;
            for (var s : p.statements) {
                var ns = transformRec(s, mutation);
                newStmts.add(ns);
                if (ns != s) changed = true;
            }
            return changed ? (T) new ProgramNode(newStmts) : node;
        }
        if (node instanceof IfNode i) {
            var cond = transformRec(i.condition, mutation);
            var thenB = transformRec(i.thenBranch, mutation);
            var elseB = i.elseBranch != null ? transformRec(i.elseBranch, mutation) : null;
            if (cond != i.condition || thenB != i.thenBranch || elseB != i.elseBranch)
                return (T) new IfNode(cond, thenB, elseB);
        }
        if (node instanceof BinaryExprNode b) {
            var left = transformRec(b.left, mutation);
            var right = transformRec(b.right, mutation);
            if (left != b.left || right != b.right)
                return (T) new BinaryExprNode(left, b.operator, right, b.line, b.column);
        }
        if (node instanceof UnaryExprNode u) {
            var op = transformRec(u.operand, mutation);
            if (op != u.operand)
                return (T) new UnaryExprNode(u.operator, op, u.line, u.column);
        }
        if (node instanceof ApplicationNode a) {
            var fn = transformRec(a.function, mutation);
            var arg = transformRec(a.argument, mutation);
            if (fn != a.function || arg != a.argument)
                return (T) new ApplicationNode(fn, arg, a.line, a.column);
        }
        if (node instanceof LambdaNode l) {
            var body = transformRec(l.body, mutation);
            if (body != l.body)
                return (T) new LambdaNode(l.param, body, l.declaredType, l.line, l.column);
        }
        if (node instanceof TupleNode t) {
            var newEls = t.elements.stream().map(e -> transformRec(e, mutation)).toList();
            boolean changed = false;
            for (int i = 0; i < newEls.size(); i++) {
                if (newEls.get(i) != t.elements.get(i)) { changed = true; break; }
            }
            if (changed) return (T) new TupleNode(newEls, t.line, t.column);
        }
        if (node instanceof AscriptionNode a) {
            var expr = transformRec(a.expr, mutation);
            if (expr != a.expr)
                return (T) new AscriptionNode(expr, a.declaredType, a.line, a.column);
        }
        return node;
    }
}
