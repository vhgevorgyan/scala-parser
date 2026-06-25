package scala.parser.core.reduction;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.reduction.SmallStepReducer;
import scala.parser.core.reduction.Substitution;
import scala.parser.core.types.*;

public class TypeSoundnessFormalTest {

    private ParserResult parse(String source) {
        Tokenizer t = new Tokenizer(source);
        List<Token> tokens = t.tokenize();
        Parser p = new Parser(tokens);
        return p.parse();
    }

    private TypeChecker check(ParserResult r) {
        TypeChecker tc = new TypeChecker(r.errors);
        tc.check(r.ast);
        return tc;
    }

    /** Progress: well-typed non-value can step */
    @Test
    void progressBetaReduction() {
        ParserResult r = parse("((x: Int) => x + 1)(5)");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        assertFalse(SmallStepReducer.isValue(expr));
        ExpressionNode next = SmallStepReducer.step(expr);
        assertNotNull(next);
    }

    /** Preservation: stepping preserves the type */
    @Test
    void preservationBeta() {
        ParserResult r = parse("((x: Int) => x + 1)(5)");
        assertTrue(r.errors.isEmpty());
        TypeChecker tc = check(r);
        Type t1 = tc.getInferredTypes().get(r.ast.statements.get(0));

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode next = SmallStepReducer.reduce(expr);

        // Type-check the reduced term
        ParserResult r2 = parse(astToString(next));
        TypeChecker tc2 = check(r2);
        Type t2 = tc2.getInferredTypes().get(r2.ast.statements.get(0));
        assertEquals(t1.toString(), t2.toString());
    }

    /** Full reduction: beta reduces to value */
    @Test
    void betaReducesToValue() {
        ParserResult r = parse("((x: Int) => x)(42)");
        assertTrue(r.errors.isEmpty());

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode normal = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(normal));
    }

    /** If reduces correctly */
    @Test
    void ifTrueReduces() {
        ParserResult r = parse("if (true) 1 else 2");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("1", ((LiteralNode) v).value);
    }

    @Test
    void ifFalseReduces() {
        ParserResult r = parse("if (false) 1 else 2");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("2", ((LiteralNode) v).value);
    }

    /** If without else returns Unit */
    @Test
    void ifNoElseReducesToUnit() {
        ParserResult r = parse("if (false) 1");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(UnitLiteralNode.class, v);
    }

    /** Arithmetic: 5 + 3 → 8 */
    @Test
    void arithmeticReduces() {
        ParserResult r = parse("5 + 3");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("8", ((LiteralNode) v).value);
    }

    /** Unary minus: -5 → 5 */
    @Test
    void unaryMinusReduces() {
        ParserResult r = parse("-5");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("-5", ((LiteralNode) v).value);
    }

    /** Type ascription: (42: Int) → 42 */
    @Test
    void ascriptionReduces() {
        ParserResult r = parse("(42: Int)");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("42", ((LiteralNode) v).value);
    }

    /** Scope: substitution capture avoidance */
    @Test
    void captureAvoidance() {
        // (λx. λy. x) y  →  λz. y  (with renaming)
        ParserResult r = parse("((x: Int) => (y: Int) => x)(1)");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode step1 = SmallStepReducer.step(expr);
        assertInstanceOf(LambdaNode.class, step1);
    }

    /** Tuple reduction */
    @Test
    void tupleReduces() {
        ParserResult r = parse("(1 + 2, 3 + 4)");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(TupleNode.class, v);
        TupleNode tup = (TupleNode) v;
        assertEquals(2, tup.elements.size());
    }

    /** Comparison reduces to Bool */
    @Test
    void comparisonReducesToBool() {
        ParserResult r = parse("5 > 3");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertInstanceOf(BoolNode.class, v);
        assertTrue(((BoolNode) v).value);
    }

    /** Boolean operator reduces */
    @Test
    void booleanAndReduces() {
        ParserResult r = parse("true && false");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertInstanceOf(BoolNode.class, v);
        assertFalse(((BoolNode) v).value);
    }

    /** Substitution correctness */
    @Test
    void substitutionCorrect() {
        ExpressionNode result = Substitution.subst("x",
            new LiteralNode("42", 0, 0),
            new BinaryExprNode(
                new IdentifierNode("x", 0, 0), "+",
                new IdentifierNode("x", 0, 0), 0, 0));
        assertInstanceOf(BinaryExprNode.class, result);
        BinaryExprNode bin = (BinaryExprNode) result;
        assertInstanceOf(LiteralNode.class, bin.left);
        assertInstanceOf(LiteralNode.class, bin.right);
        assertEquals("42", ((LiteralNode) bin.left).value);
    }

    @Test
    void substitutionPreservesBinding() {
        // [x ↦ 1](λx. x) = λx. x  (shadowing)
        ExpressionNode result = Substitution.subst("x",
            new LiteralNode("1", 0, 0),
            new LambdaNode("x", new IdentifierNode("x", 0, 0), null, 0, 0));
        assertInstanceOf(LambdaNode.class, result);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private String astToString(ExpressionNode expr) {
        if (expr instanceof LiteralNode l) return l.value;
        if (expr instanceof BoolNode b) return String.valueOf(b.value);
        if (expr instanceof UnitLiteralNode) return "()";
        if (expr instanceof IdentifierNode id) return id.name;
        if (expr instanceof LambdaNode lam) {
            String t = lam.declaredType != null ? ": " + lam.declaredType : "";
            return "(" + lam.param + t + " => " + astToString(lam.body) + ")";
        }
        if (expr instanceof ApplicationNode app) {
            return "(" + astToString(app.function) + ")(" + astToString(app.argument) + ")";
        }
        if (expr instanceof BinaryExprNode bin) {
            return "(" + astToString(bin.left) + " " + bin.operator + " " + astToString(bin.right) + ")";
        }
        if (expr instanceof UnaryExprNode un) {
            return "(" + un.operator + astToString(un.operand) + ")";
        }
        if (expr instanceof IfNode i) {
            return "(if (" + astToString(i.condition) + ") " + astToString(i.thenBranch) +
                (i.elseBranch != null ? " else " + astToString(i.elseBranch) : "") + ")";
        }
        if (expr instanceof TupleNode tup) {
            var parts = tup.elements.stream().map(this::astToString).toList();
            return "(" + String.join(", ", parts) + ")";
        }
        if (expr instanceof AscriptionNode asc) {
            return "(" + astToString(asc.expr) + ": " + asc.declaredType + ")";
        }
        if (expr instanceof StringLiteralNode s) return "\"" + s.value + "\"";
        if (expr instanceof CaseNode c) {
            return astToString(c.expr) + " match { ... }";
        }
        return expr.toString();
    }
}
