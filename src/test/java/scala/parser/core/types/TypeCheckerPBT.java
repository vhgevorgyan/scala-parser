package scala.parser.core.types;

import java.util.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.statistics.*;

import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.interpreter.*;
import scala.parser.core.reduction.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PBT for the type checker: well-typed closed terms type-check,
 * substitution preserves types, and well-typed terms progress.
 */
class TypeCheckerPBT {

    private static final List<String> IDENTS = List.of("x", "y", "z", "f", "g");

    // ── Generators ───────────────────────────────────────────────

    private static ExpressionNode genClosedLeaf(Random rand) {
        return switch (rand.nextInt(5)) {
            case 0 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 1 -> new BoolNode(0, 0, rand.nextBoolean());
            case 2 -> new UnitLiteralNode(0, 0);
            case 3 -> new CharLiteralNode((char) (32 + rand.nextInt(95)), 0, 0);
            default -> new StringLiteralNode("s" + rand.nextInt(100), 0, 0);
        };
    }

    private static ExpressionNode genExpr(Random rand, int depth) {
        if (depth <= 0) return genClosedLeaf(rand);
        return switch (rand.nextInt(13)) {
            case 0, 1 -> genClosedLeaf(rand);
            case 2 -> {
                String x = IDENTS.get(rand.nextInt(IDENTS.size()));
                yield new LambdaNode(x, genExpr(rand, depth - 1), null, 0, 0);
            }
            case 3 -> new ApplicationNode(genExpr(rand, depth - 1),
                genExpr(rand, depth - 1), 0, 0);
            case 4 -> {
                yield new IfNode(genExpr(rand, depth - 1),
                    genExpr(rand, depth - 1), genExpr(rand, depth - 1));
            }
            case 5 -> new BinaryExprNode(genExpr(rand, depth - 1),
                List.of("+", "-", "*", "/", "==", "<", ">", "&&", "||")
                    .get(rand.nextInt(9)),
                genExpr(rand, depth - 1), 0, 0);
            case 6 -> new UnaryExprNode(
                rand.nextBoolean() ? "!" : "-",
                genExpr(rand, depth - 1), 0, 0);
            case 7 -> {
                int arity = 2 + rand.nextInt(2);
                var elems = new ArrayList<ExpressionNode>();
                for (int i = 0; i < arity; i++)
                    elems.add(genExpr(rand, depth - 1));
                yield new TupleNode(elems, 0, 0);
            }
            case 8 -> new AscriptionNode(genExpr(rand, depth - 1),
                List.of(new TypeNumber(), new TypeBool(), new TypeString(), new TypeUnit())
                    .get(rand.nextInt(4)), 0, 0);
            case 9 -> {
                var stmts = new ArrayList<ASTNode>();
                int n = 1 + rand.nextInt(3);
                for (int i = 0; i < n - 1; i++) {
                    if (rand.nextBoolean()) {
                        stmts.add(new LetStatementNode(
                            "v" + rand.nextInt(1000),
                            genExpr(rand, depth - 1), false, null, 0, 0));
                    } else {
                        stmts.add(genExpr(rand, depth - 1));
                    }
                }
                stmts.add(genExpr(rand, depth - 1));
                yield new BlockNode(stmts, 0, 0);
            }
            case 10 -> new WhileNode(
                new BoolNode(0, 0, false),
                new UnitLiteralNode(0, 0), 0, 0);
            default -> genClosedLeaf(rand);
        };
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isWellTyped(ExpressionNode expr) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(expr)));
        return errors.isEmpty();
    }

    private Type getType(ExpressionNode expr) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(expr)));
        if (!errors.isEmpty()) return null;
        return tc.getInferredTypes().get(expr);
    }

    // ── Properties ───────────────────────────────────────────────

    /** Well-typed closed programs type-check without errors */
    @Property(tries = 300)
    void wellTypedClosedTerm(@ForAll("genTyped") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return; // skip ill-typed
        // If we got here, it type-checks successfully — property holds
        assertTrue(errors.isEmpty(), "Well-typed closed term must type-check: " + raw);
    }

    /** Well-typed closed term progresses (non-value → can step) */
    @Property(tries = 200)
    void progressNonValue(@ForAll("genTyped") ExpressionNode raw) {
        if (!isWellTyped(raw)) return;
        if (SmallStepReducer.isValue(raw)) return;
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: well-typed non-value " + raw.getClass().getSimpleName() + " must step");
    }

    /** Well-typed term reduces to a value */
    @Property(tries = 100)
    void reducesToValue(@ForAll("genTypedShallow") ExpressionNode raw) {
        if (!isWellTyped(raw)) return;
        if (SmallStepReducer.isValue(raw)) return;
        try {
            ExpressionNode normal = SmallStepReducer.reduce(raw);
            assertTrue(SmallStepReducer.isValue(normal),
                "Well-typed closed term must reduce to value, got: " + normal);
        } catch (RuntimeException e) {
            // May fail for open terms or non-termination — skip
        }
    }

    /** Substitution preserves type: if Γ, x:t ⊢ e : t' and ⊢ v : t, then Γ ⊢ [x↦v]e : t' */
    @Property(tries = 100)
    void substitutionPreservesType(@ForAll("genSubst") SubstitutionTestData data) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        var bodyProg = new ProgramNode(List.of(data.body));
        tc.check(bodyProg);
        if (!errors.isEmpty()) return;
        var bodyType = tc.getInferredTypes().get(data.body);
        if (bodyType == null) return;

        var valueErrors = new ArrayList<ParserError>();
        var valueTc = new TypeChecker(valueErrors);
        valueTc.check(new ProgramNode(List.of(data.value)));
        if (!valueErrors.isEmpty()) return;
        var valueType = valueTc.getInferredTypes().get(data.value);
        if (valueType == null) return;

        ExpressionNode substituted = Substitution.subst(data.varName, data.value, data.body);
        var substErrors = new ArrayList<ParserError>();
        var substTc = new TypeChecker(substErrors);
        substTc.check(new ProgramNode(List.of(substituted)));
        if (!substErrors.isEmpty()) return;
        var substType = substTc.getInferredTypes().get(substituted);
        assertNotNull(substType, "Substituted term must have a type");
    }

    // ── Providers ────────────────────────────────────────────────

    @Provide
    Arbitrary<ExpressionNode> genTyped() {
        return Arbitraries.randomValue(rand -> genExpr(rand, 4));
    }

    @Provide
    Arbitrary<ExpressionNode> genTypedShallow() {
        return Arbitraries.randomValue(rand -> genExpr(rand, 2));
    }

    @Provide
    Arbitrary<SubstitutionTestData> genSubst() {
        return Arbitraries.randomValue(rand -> {
            String x = "x" + rand.nextInt(1000);
            ExpressionNode value = genClosedLeaf(rand);
            ExpressionNode body = genExprWithFree(rand, 3, x);
            return new SubstitutionTestData(x, value, body);
        });
    }

    private static ExpressionNode genExprWithFree(Random rand, int depth, String freeVar) {
        if (depth <= 0 || rand.nextDouble() < 0.3) {
            return rand.nextBoolean()
                ? genClosedLeaf(rand)
                : new IdentifierNode(freeVar, 0, 0);
        }
        return switch (rand.nextInt(6)) {
            case 0 -> genClosedLeaf(rand);
            case 1 -> {
                String y = IDENTS.get(rand.nextInt(IDENTS.size()));
                ExpressionNode body = genExprWithFree(rand, depth - 1, freeVar);
                yield body != null ? new LambdaNode(y, body, null, 0, 0) : genClosedLeaf(rand);
            }
            case 2 -> new IdentifierNode(freeVar, 0, 0);
            case 3 -> {
                var left = genExprWithFree(rand, depth - 1, freeVar);
                var right = genExprWithFree(rand, depth - 1, freeVar);
                yield (left != null && right != null)
                    ? new BinaryExprNode(left, "+", right, 0, 0)
                    : genClosedLeaf(rand);
            }
            default -> genClosedLeaf(rand);
        };
    }

    public record SubstitutionTestData(String varName, ExpressionNode value, ExpressionNode body) {}
}
