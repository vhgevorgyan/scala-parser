package scala.parser.core.interpreter;

import java.util.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.statistics.*;

import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.reduction.*;
import scala.parser.core.types.*;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorPBT {

    private static final int MAX_DEPTH = 200;
    private static final int MAX_EVAL = 500;

    // ── Generators ───────────────────────────────────────────────

    private static ExpressionNode genClosed(Random rand) {
        return switch (rand.nextInt(5)) {
            case 0 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 1 -> new BoolNode(0, 0, rand.nextBoolean());
            case 2 -> new CharLiteralNode((char) (32 + rand.nextInt(95)), 0, 0);
            case 3 -> new UnitLiteralNode(0, 0);
            case 4 -> {
                String x = "x" + rand.nextInt(100);
                yield new LambdaNode(x, genClosed(rand), null, 0, 0);
            }
            default -> new LiteralNode("0", 0, 0);
        };
    }

    // Generates an expression with up to `depth` nested applications
    private static ExpressionNode genNestedApp(Random rand, int depth) {
        if (depth <= 0 || rand.nextBoolean()) {
            return genClosed(rand);
        }
        String x = "x" + rand.nextInt(100);
        ExpressionNode body = genClosed(rand);
        ExpressionNode lam = new LambdaNode(x, body, null, 0, 0);
        ExpressionNode arg = genClosed(rand);
        return new ApplicationNode(lam, arg, 0, 0);
    }

    // Generates a random expression with a chance of blocks/lets
    private static ExpressionNode genRichExpr(Random rand, int depth) {
        if (depth <= 0) return genClosed(rand);
        return switch (rand.nextInt(8)) {
            case 0, 1 -> genClosed(rand);
            case 2 -> {
                String x = "x" + rand.nextInt(100);
                yield new LambdaNode(x, genRichExpr(rand, depth - 1), null, 0, 0);
            }
            case 3 -> {
                // Application of generated function to generated arg
                String x = "x" + rand.nextInt(100);
                ExpressionNode body = genRichExpr(rand, depth - 1);
                ExpressionNode lam = new LambdaNode(x, body, null, 0, 0);
                ExpressionNode arg = genRichExpr(rand, depth - 1);
                yield new ApplicationNode(lam, arg, 0, 0);
            }
            case 4 -> {
                var stmts = new ArrayList<ASTNode>();
                int n = 1 + rand.nextInt(3);
                for (int i = 0; i < n; i++) {
                    if (rand.nextBoolean()) {
                        stmts.add(new LetStatementNode(
                            "v" + rand.nextInt(1000),
                            genRichExpr(rand, depth - 1),
                            false, null, 0, 0));
                    } else {
                        stmts.add(genRichExpr(rand, depth - 1));
                    }
                }
                yield new BlockNode(stmts, 0, 0);
            }
            case 5 -> {
                yield new IfNode(genRichExpr(rand, depth - 1),
                    genRichExpr(rand, depth - 1), genRichExpr(rand, depth - 1));
            }
            case 6 -> {
                String op = List.of("+", "-", "*", "/", "==", "<", ">", "&&", "||")
                    .get(rand.nextInt(9));
                yield new BinaryExprNode(
                    genRichExpr(rand, depth - 1), op,
                    genRichExpr(rand, depth - 1), 0, 0);
            }
            case 7 -> {
                yield new WhileNode(
                    new BoolNode(0, 0, false),
                    new UnitLiteralNode(0, 0), 0, 0);
            }
            default -> genClosed(rand);
        };
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isWellTyped(ExpressionNode expr) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(expr)));
        return errors.isEmpty();
    }

    private Value evalSafe(ExpressionNode expr) {
        try {
            var e = new Evaluator(MAX_EVAL);
            return e.evalProgram(new ProgramNode(List.of(expr)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ExpressionNode reduceSafe(ExpressionNode expr) {
        try {
            return SmallStepReducer.reduce(expr);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean valuesMatch(Value v, ExpressionNode n) {
        if (v instanceof NumberValue nv && n instanceof LiteralNode l)
            return nv.value == Integer.parseInt(l.value);
        if (v instanceof BoolValue bv && n instanceof BoolNode bn)
            return bv.value == bn.value;
        if (v instanceof UnitValue && n instanceof UnitLiteralNode)
            return true;
        if (v instanceof StringValue sv && n instanceof StringLiteralNode sn)
            return sv.value.equals(sn.value);
        if (v instanceof CharValue cv && n instanceof CharLiteralNode cn)
            return cv.value == cn.value;
        if (v instanceof TupleValue tv && n instanceof TupleNode tn) {
            if (tv.values.size() != tn.elements.size()) return false;
            for (int i = 0; i < tv.values.size(); i++) {
                if (!valuesMatch(tv.values.get(i), tn.elements.get(i))) return false;
            }
            return true;
        }
        return false;
    }

    // ── Properties ───────────────────────────────────────────────

    @Property(tries = 500)
    void wellTypedClosesTerminates(@ForAll("genApp") ExpressionNode raw) {
        if (!isWellTyped(raw)) return;
        Value v = evalSafe(raw);
        assertNotNull(v, "Well-typed closed expression must evaluate to a Value, got null for: " + raw);
    }

    @Property(tries = 500)
    void bigStepMatchesSmallStep(@ForAll("genApp") ExpressionNode raw) {
        if (!isWellTyped(raw)) return;
        if (SmallStepReducer.isValue(raw)) return;

        Value big = evalSafe(raw);
        ExpressionNode small = reduceSafe(raw);
        if (big == null || small == null) return;
        if (!SmallStepReducer.isValue(small)) return;

        assertTrue(valuesMatch(big, small) ||
                (big instanceof ClosureValue && small instanceof LambdaNode),
            "Big-step " + big + " must match small-step " + small + " for " + raw);
    }

    @Property(tries = 200)
    void blockLetEvaluates(@ForAll("genBlock") ExpressionNode raw) {
        if (!isWellTyped(raw)) return;
        Value v = evalSafe(raw);
        assertNotNull(v, "Block must evaluate: " + raw);
    }

    // ── Providers ────────────────────────────────────────────────

    @Provide
    Arbitrary<ExpressionNode> genApp() {
        return Arbitraries.randomValue(rand -> genNestedApp(rand, 3));
    }

    @Provide
    Arbitrary<ExpressionNode> genBlock() {
        return Arbitraries.randomValue(rand -> {
            var stmts = new ArrayList<ASTNode>();
            int n = 1 + rand.nextInt(4);
            for (int i = 0; i < n - 1; i++) {
                String name = "x" + rand.nextInt(100);
                stmts.add(new LetStatementNode(name,
                    genClosed(rand), false, null, 0, 0));
            }
            stmts.add(genClosed(rand));
            return new BlockNode(stmts, 0, 0);
        });
    }
}
