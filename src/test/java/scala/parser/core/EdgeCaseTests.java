package scala.parser.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import scala.parser.core.ast.*;
import scala.parser.core.interpreter.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.reduction.SmallStepReducer;
import scala.parser.core.semantic.Desugarer;
import scala.parser.core.types.*;
import scala.parser.lsp.CompilerPipeline;

class EdgeCaseTests {

    // ── Helpers ──────────────────────────────────────────────

    private ParserResult parse(String src) {
        Tokenizer t = new Tokenizer(src);
        return new Parser(t.tokenize()).parse();
    }

    private EvaluationResult analyze(String src) {
        return new CompilerPipeline().analyze(src);
    }

    private TypeChecker checkOnly(String src) {
        ParserResult r = parse(src);
        TypeChecker tc = new TypeChecker(r.errors);
        tc.check(r.ast);
        return tc;
    }

    private ExpressionNode parseExpr(String src) {
        ParserResult r = parse(src);
        assertTrue(r.errors.isEmpty(), "Parse errors: " + r.errors);
        return (ExpressionNode) r.ast.statements.get(0);
    }

    // ── Empty constructs ─────────────────────────────────────

    @Test
    void emptyBlockParses() {
        ParserResult r = parse("{ }");
        assertTrue(r.errors.isEmpty());
        ExpressionNode e = (ExpressionNode) r.ast.statements.get(0);
        assertInstanceOf(BlockNode.class, e);
        assertTrue(((BlockNode) e).statements.isEmpty());
    }

    @Test
    void emptyBlockTypeChecks() {
        var r = analyze("{ }");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void emptyBlockReducesToUnit() {
        BlockNode empty = new BlockNode(List.of(), 1, 1);
        ExpressionNode result = SmallStepReducer.reduce(empty);
        assertInstanceOf(UnitLiteralNode.class, result);
    }

    @Test
    void emptyBlockEvaluatesToNull() {
        var r = analyze("{ }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertNull(r.value());
    }

    @Test
    void emptyProgramParses() {
        ParserResult r = parse("");
        assertTrue(r.errors.isEmpty());
        assertTrue(r.ast.statements.isEmpty());
    }

    @Test
    void emptyProgramEvaluatesToNull() {
        var r = analyze("");
        assertTrue(r.parserResult().errors.isEmpty());
        assertNull(r.value());
    }

    // ── Nested blocks ────────────────────────────────────────

    @Test
    void nestedEmptyBlock() {
        var r = analyze("{ { } }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertNull(r.value());
    }

    @Test
    void nestedBlockWithValue() {
        var r = analyze("{ { 42 } }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(42, ((NumberValue) r.value()).value);
    }

    @Test
    void deeplyNestedBlock() {
        var r = analyze("{ { { { 7 } } } }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(7, ((NumberValue) r.value()).value);
    }

    // ── Block with expressions only (no lets) ────────────────

    @Test
    void blockWithOnlyExpressions() {
        var r = analyze("{ 1; 2; 3 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(3, ((NumberValue) r.value()).value);
    }

    @Test
    void blockWithMixedStatements() {
        var r = analyze("{ val x = 10; x + 1; x + 2 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(12, ((NumberValue) r.value()).value);
    }

    // ── Semicolons ───────────────────────────────────────────

    @Test
    void leadingSemicolons() {
        var r = analyze("{ ;;; 42 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(42, ((NumberValue) r.value()).value);
    }

    @Test
    void trailingSemicolon() {
        var r = analyze("{ 42; }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(42, ((NumberValue) r.value()).value);
    }

    @Test
    void doubleSemicolonsBetweenStatements() {
        var r = analyze("val x = 1;; val y = 2; x + y");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(3, ((NumberValue) r.value()).value);
    }

    // ── Mutual recursion boundary ────────────────────────────

    @Test
    void twoAdjacentDefsFormMutualLetNode() {
        ParserResult r = parse("def f(x: Int): Int = x; def g(x: Int): Int = x; f(0)");
        assertTrue(r.errors.isEmpty());
        // f and g should be grouped into MutualLetNode
        ASTNode stmt = r.ast.statements.get(0);
        assertInstanceOf(MutualLetNode.class, stmt);
        assertEquals(2, ((MutualLetNode) stmt).defs.size());
    }

    @Test
    void singleDefRemainsLetStatement() {
        ParserResult r = parse("def f(x: Int): Int = x; f(0)");
        assertTrue(r.errors.isEmpty());
        ASTNode stmt = r.ast.statements.get(0);
        assertInstanceOf(LetStatementNode.class, stmt);
        assertTrue(((LetStatementNode) stmt).isRec);
    }

    @Test
    void nonAdjacentDefsDontMerge() {
        ParserResult r = parse("def f(x: Int): Int = x; val y = 1; def g(x: Int): Int = x; f(0)");
        assertTrue(r.errors.isEmpty());
        // Statements: [def f, val y, def g, f(0)]
        ASTNode first = r.ast.statements.get(0);
        ASTNode second = r.ast.statements.get(1);
        ASTNode third = r.ast.statements.get(2);
        // First and third are single defs → LetStatementNode; second is val → LetStatementNode
        assertInstanceOf(LetStatementNode.class, first);
        assertInstanceOf(LetStatementNode.class, second);
        assertInstanceOf(LetStatementNode.class, third);
        assertTrue(((LetStatementNode) first).isRec);
        assertFalse(((LetStatementNode) second).isRec);
        assertTrue(((LetStatementNode) third).isRec);
    }

    // ── Mutual recursion success ─────────────────────────────

    @Test
    void mutualRecursionEvaluatesEven() {
        var r = analyze("def even(n: Int): Boolean = if (n == 0) true else odd(n - 1);\n" +
                        "def odd(n: Int): Boolean = if (n == 0) false else even(n - 1);\n" +
                        "even(6)");
        assertTrue(r.parserResult().errors.isEmpty());
        assertTrue(((BoolValue) r.value()).value);
    }

    @Test
    void mutualRecursionEvaluatesOdd() {
        var r = analyze("def even(n: Int): Boolean = if (n == 0) true else odd(n - 1);\n" +
                        "def odd(n: Int): Boolean = if (n == 0) false else even(n - 1);\n" +
                        "even(7)");
        assertTrue(r.parserResult().errors.isEmpty());
        assertFalse(((BoolValue) r.value()).value);
    }

    @Test
    void threeWayMutualRecursion() {
        // f0(n) = if n==0 then 0 else f2(n-1)
        // f1(n) = if n==0 then 1 else f0(n-1)
        // f2(n) = if n==0 then 2 else f1(n-1)
        // f0(3): f0(3) → f2(2) → f1(1) → f0(0) → 0
        var r = analyze(
            "def f0(n: Int): Int = if (n == 0) 0 else f2(n - 1);\n" +
            "def f1(n: Int): Int = if (n == 0) 1 else f0(n - 1);\n" +
            "def f2(n: Int): Int = if (n == 0) 2 else f1(n - 1);\n" +
            "f0(3)"
        );
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(0, ((NumberValue) r.value()).value);
    }

    // ── If without else ──────────────────────────────────────

    @Test
    void ifWithoutElseParses() {
        ParserResult r = parse("if (true) 1");
        assertTrue(r.errors.isEmpty());
        IfNode ifn = (IfNode) r.ast.statements.get(0);
        assertNull(ifn.elseBranch);
    }

    @Test
    void ifWithoutElseViaDesugarer() {
        ExpressionNode e = parseExpr("if (true) 1");
        assertNull(((IfNode) e).elseBranch);
        Desugarer.desugarExpr(e);
        assertNotNull(((IfNode) e).elseBranch);
        assertInstanceOf(UnitLiteralNode.class, ((IfNode) e).elseBranch);
    }

    @Test
    void ifWithoutElseEvaluates() {
        // After desugaring: if (true) 1 else ()
        var r = analyze("if (true) 1");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(1, ((NumberValue) r.value()).value);
    }

    @Test
    void ifWithoutElseFalseBranchEvaluatesToUnit() {
        var r = analyze("if (false) 1");
        assertTrue(r.parserResult().errors.isEmpty());
        assertInstanceOf(UnitValue.class, r.value());
    }

    @Test
    void ifWithoutElseInBlock() {
        var r = analyze("{ val x = if (true) 1; x }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(1, ((NumberValue) r.value()).value);
    }

    // ── Unit literal ─────────────────────────────────────────

    @Test
    void unitLiteralParses() {
        ParserResult r = parse("()");
        assertTrue(r.errors.isEmpty());
        assertInstanceOf(UnitLiteralNode.class, r.ast.statements.get(0));
    }

    @Test
    void unitLiteralTypeChecks() {
        var r = analyze("()");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void unitLiteralEvaluates() {
        var r = analyze("()");
        assertTrue(r.parserResult().errors.isEmpty());
        assertInstanceOf(UnitValue.class, r.value());
    }

    @Test
    void unitIsValue() {
        ExpressionNode u = new UnitLiteralNode(1, 1);
        assertTrue(SmallStepReducer.isValue(u));
    }

    @Test
    void unitAnnotationTypeChecks() {
        var r = analyze("val x: Unit = ()");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void unitAnnotationMismatch() {
        var r = analyze("val x: Unit = 42");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    // ── TypeAliasNode in blocks ──────────────────────────────

    @Test
    void typeAliasParses() {
        // TypeAliasNode only at top level, not inside blocks
        ParserResult r = parse("type T = Int; val x: T = 42");
        assertTrue(r.errors.isEmpty());
        assertInstanceOf(TypeAliasNode.class, r.ast.statements.get(0));
    }

    // ── Multiple enum decls ──────────────────────────────────

    @Test
    void twoEnumsIndependently() {
        var r = analyze("enum A { case A1; case A2 }\nenum B { case B1; case B2 }\nval x = A1\nval y = B1\nx");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void enumBetweenVals() {
        var r = analyze("val x = 1\nenum E { case C }\nval y = 2\nval z = C\nx + y");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(3, ((NumberValue) r.value()).value);
    }

    // ── Desugarer nesting ────────────────────────────────────

    @Test
    void desugarNestedIfWithoutElse() {
        ExpressionNode e = parseExpr("if (true) if (false) 1 else 2");
        IfNode outer = (IfNode) e;
        assertNull(outer.elseBranch);
        Desugarer.desugarExpr(e);
        assertNotNull(outer.elseBranch);
        assertInstanceOf(UnitLiteralNode.class, outer.elseBranch);
        // Inner if already has else, should be unchanged
        IfNode inner = (IfNode) outer.thenBranch;
        assertNotNull(inner.elseBranch);
        assertInstanceOf(LiteralNode.class, inner.elseBranch);
    }

    @Test
    void desugarIfWithoutElseInLambda() {
        ExpressionNode e = parseExpr("(x: Int) => if (x == 0) 1");
        Desugarer.desugarExpr(e);
        LambdaNode lam = (LambdaNode) e;
        IfNode ifn = (IfNode) lam.body;
        assertNotNull(ifn.elseBranch);
        assertInstanceOf(UnitLiteralNode.class, ifn.elseBranch);
    }

    @Test
    void desugarIdempotent() {
        ExpressionNode e = parseExpr("if (true) 1 else 2");
        Desugarer.desugarExpr(e);
        IfNode ifn = (IfNode) e;
        assertInstanceOf(LiteralNode.class, ifn.elseBranch);
        assertEquals("2", ((LiteralNode) ifn.elseBranch).value);
    }

    // ── Division by zero type-checks (runtime error) ─────────

    @Test
    void divisionByZeroTypeChecks() {
        // Division by zero is a runtime error, not a type error
        ParserResult r = parse("val x: Int = 5 / 0");
        assertTrue(r.errors.isEmpty());
        TypeChecker tc = new TypeChecker(r.errors);
        tc.check(r.ast);
        assertTrue(r.errors.isEmpty());
    }

    // ── Tuple type checking ──────────────────────────────────

    @Test
    void tupleTypeChecks() {
        var r = analyze("(1, true, \"hi\")");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void tupleAnnotated() {
        var r = analyze("val p: (Int, Boolean) = (1, true)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void tupleAnnotationMismatch() {
        var r = analyze("val p: (Int, Boolean) = (1, 2)");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    // ── String operation type checking ───────────────────────

    @Test
    void stringEqTypeChecks() {
        var r = analyze("\"a\" == \"b\"");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void stringConcatTypeChecks() {
        var r = analyze("\"a\" + \"b\"");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void stringPlusNumberTypeError() {
        var r = analyze("\"a\" + 1");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    // ── Block edge cases ─────────────────────────────────────

    @Test
    void blockWithSingleLetNoExpression() {
        // A block with only val statements and no trailing expression
        var r = analyze("{ val x = 42; val y = x }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(42, ((NumberValue) r.value()).value);
    }

    @Test
    void blockLetShadowsOuterValInExpr() {
        var r = analyze("val x = 1; { val x = 2; x }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(2, ((NumberValue) r.value()).value);
    }

    // ── Case / match edge cases ──────────────────────────────

    @Test
    void wildcardMatchOnUnit() {
        var r = analyze("() match { case _ => 42 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(42, ((NumberValue) r.value()).value);
    }

    @Test
    void literalMatchNumeric() {
        var r = analyze("0 match { case 0 => 10; case _ => 20 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(10, ((NumberValue) r.value()).value);
    }

    @Test
    void literalMatchBoolean() {
        var r = analyze("true match { case true => 1; case false => 0 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(1, ((NumberValue) r.value()).value);
    }

    // ── Zero-arg constructor edge cases ──────────────────────

    @Test
    void matchOnNil() {
        var r = analyze("Nil match { case Nil => 0; case _ => 1 }");
        assertTrue(r.parserResult().errors.isEmpty());
        assertEquals(0, ((NumberValue) r.value()).value);
    }
}
