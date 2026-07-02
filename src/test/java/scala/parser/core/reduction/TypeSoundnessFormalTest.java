package scala.parser.core.reduction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.interpreter.*;
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

    /** BlockNode reduction: { val x = 42; x } → 42 */
    @Test
    void blockLetReduces() {
        ParserResult r = parse("{ val x = 42; x }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("42", ((LiteralNode) v).value);
    }

    /** BlockNode reduction: { (x) => x; (42) } → 42 */
    @Test
    void blockExprReduces() {
        ParserResult r = parse("{ ((x: Int) => x)(42) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("42", ((LiteralNode) v).value);
    }

    /** BlockNode: single expression unwraps */
    @Test
    void blockSingleExpr() {
        ParserResult r = parse("{ 42 }");
        assertTrue(r.errors.isEmpty());

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
    }

    /** BlockNode: multiple lets substitute correctly */
    @Test
    void blockMultipleLets() {
        ParserResult r = parse("{ val x = 5; val y = x + 3; y }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("8", ((LiteralNode) v).value);
    }

    /** BlockNode: let with lambda body */
    @Test
    void blockLetLambda() {
        ParserResult r = parse("{ val id = (x: Int) => x; id(42) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("42", ((LiteralNode) v).value);
    }

    // ── Case/match reduction tests ──────────────────────────────

    /** Constructor pattern match reduces */
    @Test
    void constructorMatchReduces() {
        ParserResult r = parse("enum Maybe[A] { case Nothing; case Just(x: A) }; Just(42) match { case Just(y) => y }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(1);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("42", ((LiteralNode) v).value);
    }

    /** Wildcard pattern reduces */
    @Test
    void wildcardMatchReduces() {
        ParserResult r = parse("42 match { case _ => 0 }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("0", ((LiteralNode) v).value);
    }

    /** Literal pattern match reduces */
    @Test
    void literalPatternMatchReduces() {
        ParserResult r = parse("0 match { case 0 => 1; case _ => 2 }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("1", ((LiteralNode) v).value);
    }

    /** Nested constructor match reduces: outer value passed through inner constructor */
    @Test
    void nestedConstructorMatchReduces() {
        ParserResult r = parse("enum Box[A] { case Box(x: A) }; Box(1 + 2) match { case Box(x) => x }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(1);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("3", ((LiteralNode) v).value);
    }

    /** Progress: non-value case match can step */
    @Test
    void progressCaseMatch() {
        ParserResult r = parse("(1 + 2) match { case _ => 0 }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        assertFalse(SmallStepReducer.isValue(expr));
        ExpressionNode next = SmallStepReducer.step(expr);
        assertNotNull(next);
    }

    /** Preservation: case match preserves type */
    @Test
    void preservationCaseMatch() {
        ParserResult r = parse("enum Opt[A] { case None; case Some(x: A) }; Some(1 + 2) match { case Some(x) => x }");
        assertTrue(r.errors.isEmpty());
        TypeChecker tc = check(r);
        Type t1 = tc.getInferredTypes().get(r.ast.statements.get(1));

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(1);
        ExpressionNode next = SmallStepReducer.reduce(expr);

        ParserResult r2 = parse(astToString(next));
        TypeChecker tc2 = check(r2);
        Type t2 = tc2.getInferredTypes().get(r2.ast.statements.get(0));
        assertEquals(t1.toString(), t2.toString());
    }

    // ── Recursive def (FixpointNode) tests ──────────────────────

    /** BlockNode: recursive def factorial reduces correctly */
    @Test
    void blockLetRecFactorial() {
        ParserResult r = parse("{ def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1); fact(5) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("120", ((LiteralNode) v).value);
    }

    /** BlockNode: recursive def fibonacci reduces correctly */
    @Test
    void blockLetRecFibonacci() {
        ParserResult r = parse("{ def fib(n: Int): Int = if (n == 0) 0 else if (n == 1) 1 else fib(n - 1) + fib(n - 2); fib(10) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(LiteralNode.class, v);
        assertEquals("55", ((LiteralNode) v).value);
    }

    /** Preservation: recursive def preserves type */
    @Test
    void preservationBlockLetRec() {
        ParserResult r = parse("{ def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1); fact(5) }");
        assertTrue(r.errors.isEmpty());
        TypeChecker tc = check(r);
        Type t1 = tc.getInferredTypes().get(r.ast.statements.get(0));

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode next = SmallStepReducer.reduce(expr);

        ParserResult r2 = parse(astToString(next));
        TypeChecker tc2 = check(r2);
        Type t2 = tc2.getInferredTypes().get(r2.ast.statements.get(0));
        assertEquals(t1.toString(), t2.toString());
    }

    /** Progress: non-value recursive call can step */
    @Test
    void progressRecursiveCall() {
        ParserResult r = parse("{ def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1); fact(3 + 2) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        assertFalse(SmallStepReducer.isValue(expr));
        ExpressionNode next = SmallStepReducer.step(expr);
        assertNotNull(next);
    }

    // ── Mutual recursion tests ───────────────────────────────────

    /** Mutual recursion: even/odd reduce correctly */
    @Test
    void blockMutualEvenOdd() {
        ParserResult r = parse("{ def even(n: Int): Bool = if (n == 0) true else odd(n - 1); def odd(n: Int): Bool = if (n == 0) false else even(n - 1); even(5) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(BoolNode.class, v);
        assertFalse(((BoolNode) v).value);
    }

    /** Mutual recursion: even(0) → true */
    @Test
    void blockMutualEvenZero() {
        ParserResult r = parse("{ def even(n: Int): Bool = if (n == 0) true else odd(n - 1); def odd(n: Int): Bool = if (n == 0) false else even(n - 1); even(0) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(BoolNode.class, v);
        assertTrue(((BoolNode) v).value);
    }

    /** Preservation: mutual recursion preserves type (Bool) */
    @Test
    void preservationBlockMutualEvenOdd() {
        ParserResult r = parse("{ def even(n: Int): Bool = if (n == 0) true else odd(n - 1); def odd(n: Int): Bool = if (n == 0) false else even(n - 1); even(5) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode next = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(next));
        assertInstanceOf(BoolNode.class, next);

        ParserResult r2 = parse(astToString(next));
        assertTrue(r2.errors.isEmpty());
        TypeChecker tc2 = check(r2);
        Type t2 = tc2.getInferredTypes().get(r2.ast.statements.get(0));
        assertEquals("Boolean", t2.toString());
    }

    /** Progress: non-value mutual recursive call can step */
    @Test
    void progressMutualRecursiveCall() {
        ParserResult r = parse("{ def even(n: Int): Bool = if (n == 0) true else odd(n - 1); def odd(n: Int): Bool = if (n == 0) false else even(n - 1); even(3 + 2) }");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        assertFalse(SmallStepReducer.isValue(expr));
        ExpressionNode next = SmallStepReducer.step(expr);
        assertNotNull(next);
    }

    // ── String comparison tests ──────────────────────────────────

    /** String equality comparison reduces */
    @Test
    void stringEquality() {
        ParserResult r = parse("\"hello\" == \"hello\"");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(BoolNode.class, v);
        assertTrue(((BoolNode) v).value);
    }

    /** String inequality comparison reduces */
    @Test
    void stringInequality() {
        ParserResult r = parse("\"hello\" == \"world\"");
        assertTrue(r.errors.isEmpty());
        check(r);

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode v = SmallStepReducer.reduce(expr);
        assertTrue(SmallStepReducer.isValue(v));
        assertInstanceOf(BoolNode.class, v);
        assertFalse(((BoolNode) v).value);
    }

    /** Preservation: string comparison preserves Bool type */
    @Test
    void preservationStringEquality() {
        ParserResult r = parse("\"hello\" == \"hello\"");
        assertTrue(r.errors.isEmpty());
        TypeChecker tc = check(r);
        Type t1 = tc.getInferredTypes().get(r.ast.statements.get(0));

        ExpressionNode expr = (ExpressionNode) r.ast.statements.get(0);
        ExpressionNode next = SmallStepReducer.reduce(expr);

        ParserResult r2 = parse(astToString(next));
        TypeChecker tc2 = check(r2);
        Type t2 = tc2.getInferredTypes().get(r2.ast.statements.get(0));
        assertEquals(t1.toString(), t2.toString());
    }

    // ── Gradual Typing: ? type and runtime type checks ──────────

    @Test
    void dynamicTypeAsAnnotation() {
        ExpressionNode expr = parseAndGet("(42: ?)");
        assertNotNull(expr);
        assertInstanceOf(AscriptionNode.class, expr);
    }

    @Test
    void dynamicTypeParseAndCheck() {
        ParserResult r = parse("val x: ? = 42");
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(r.ast);
        assertTrue(errors.isEmpty(), "val x: ? = 42 must be well-typed: " + errors);
    }

    @Test
    void dynamicTypeAcceptsAny() {
        Value v = eval("(42: ?)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void dynamicTypeAcceptsBool() {
        Value v = eval("(true: ?)");
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void ascriptionToConcreteTypePasses() {
        Value v = eval("(42: Int)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void ascriptionToWrongTypeFails() {
        assertThrows(RuntimeException.class, () -> eval("(42: Boolean)"),
            "Casting Int to Boolean must fail at runtime");
    }

    @Test
    void ascriptionStringToIntFails() {
        assertThrows(RuntimeException.class, () -> eval("(\"hello\": Int)"),
            "Casting String to Int must fail at runtime");
    }

    @Test
    void ascriptionBoolToStringFails() {
        assertThrows(RuntimeException.class, () -> eval("(true: String)"),
            "Casting Boolean to String must fail at runtime");
    }

    @Test
    void dynamicTypeInFunctionType() {
        ParserResult r = parse("val f: (? => ?) = (x: ?) => x");
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(r.ast);
        assertTrue(errors.isEmpty(), "Function with ? types must be well-typed: " + errors);
    }

    @Test
    void ascriptionViaSmallStep() {
        ExpressionNode result = reduce("(42: ?)");
        assertTrue(SmallStepReducer.isValue(result),
            "Reduction of (42: ?) must produce a value");
    }

    @Test
    void ascriptionErasesInReducer() {
        ExpressionNode result = parseAndStep("(42: Int)");
        assertInstanceOf(LiteralNode.class, result,
            "SmallStepReducer must erase ascription, leaving the inner expression");
    }

    // ── Builtins: succ / pred / iszero / print ──────────────────

    @Test
    void builtinSucc() {
        ExpressionNode expr = parseAndStep("succ(5)");
        assertInstanceOf(LiteralNode.class, expr);
        assertEquals("6", ((LiteralNode) expr).value);
    }

    @Test
    void builtinPred() {
        ExpressionNode expr = parseAndStep("pred(5)");
        assertInstanceOf(LiteralNode.class, expr);
        assertEquals("4", ((LiteralNode) expr).value);
    }

    @Test
    void builtinIszeroTrue() {
        ExpressionNode expr = parseAndStep("iszero(0)");
        assertInstanceOf(BoolNode.class, expr);
        assertTrue(((BoolNode) expr).value);
    }

    @Test
    void builtinIszeroFalse() {
        ExpressionNode expr = parseAndStep("iszero(1)");
        assertInstanceOf(BoolNode.class, expr);
        assertFalse(((BoolNode) expr).value);
    }

    @Test
    void builtinPrint() {
        ExpressionNode expr = parseAndStep("print(42)");
        assertInstanceOf(LiteralNode.class, expr);
        assertEquals("42", ((LiteralNode) expr).value);
    }

    @Test
    void builtinSuccNested() {
        ExpressionNode expr = reduce("succ(succ(5))");
        assertInstanceOf(LiteralNode.class, expr);
        assertEquals("7", ((LiteralNode) expr).value);
    }

    @Test
    void builtinSuccWithExpr() {
        ExpressionNode expr = reduce("succ(3 + 4)");
        assertInstanceOf(LiteralNode.class, expr);
        assertEquals("8", ((LiteralNode) expr).value);
    }

    @Test
    void builtinIsValue() {
        assertTrue(SmallStepReducer.isValue(parseIdent("succ")));
        assertTrue(SmallStepReducer.isValue(parseIdent("pred")));
        assertTrue(SmallStepReducer.isValue(parseIdent("iszero")));
        assertTrue(SmallStepReducer.isValue(parseIdent("print")));
    }

    @Test
    void builtinNotConstructorValue() {
        // succ(5) should NOT be a constructor value (succ is not a constructor)
        ExpressionNode app = parseAndGet("succ(5)");
        assertFalse(SmallStepReducer.isValue(app));
    }

    @Test
    void substitutionInNestedBlockLetStatements() {
        // Regression: Substitution.subst for BlockNode skipped LetStatementNode
        // and MutualLetNode, leaving free variables after reduction.
        //   { val g0 = 42; { val y = g0; y } }
        var innerBlock = new BlockNode(List.of(
            new LetStatementNode("y", new IdentifierNode("g0", 0, 0), false, null, 0, 0),
            new IdentifierNode("y", 0, 0)
        ), 0, 0);
        var outerBlock = new BlockNode(List.of(
            new LetStatementNode("g0", new LiteralNode("42", 0, 0), false, null, 0, 0),
            innerBlock
        ), 0, 0);
        var program = new ProgramNode(List.of(outerBlock));

        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        assertTrue(errors.isEmpty(), "Initial block must be well-typed: " + errors);

        assertFalse(SmallStepReducer.isValue(outerBlock), "Block must not be a value");
        ExpressionNode step1 = SmallStepReducer.step(outerBlock);

        var errors2 = new ArrayList<ParserError>();
        new TypeChecker(errors2).check(new ProgramNode(List.of(step1)));
        assertTrue(errors2.isEmpty(),
            "After step1 (subst g0=42), result must be well-typed, got: " + errors2);

        assertFalse(SmallStepReducer.isValue(step1), "Step1 result must not be a value");
        ExpressionNode step2 = SmallStepReducer.step(step1);

        var errors3 = new ArrayList<ParserError>();
        new TypeChecker(errors3).check(new ProgramNode(List.of(step2)));
        assertTrue(errors3.isEmpty(),
            "After step2, result must be well-typed, got: " + errors3);

        ExpressionNode normal = SmallStepReducer.reduce(outerBlock);
        assertNotNull(normal, "Reduction must produce a result");
        assertTrue(SmallStepReducer.isValue(normal),
            "Full reduction must produce a value, got: " + normal);
    }

    private ExpressionNode parseIdent(String name) {
        return new IdentifierNode(name, 0, 0);
    }

    private ExpressionNode parseAndGet(String source) {
        ParserResult r = parse(source);
        return (ExpressionNode) r.ast.statements.get(0);
    }

    private ExpressionNode parseAndStep(String source) {
        ExpressionNode expr = parseAndGet(source);
        return SmallStepReducer.step(expr);
    }

    private ExpressionNode reduce(String source) {
        ExpressionNode expr = parseAndGet(source);
        return SmallStepReducer.reduce(expr);
    }

    private Value eval(String source) {
        ParserResult r = parse(source);
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(r.ast);
        if (!errors.isEmpty())
            throw new RuntimeException("Type error: " + errors.get(0).message);
        return new Evaluator().evalProgram(r.ast);
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
        if (expr instanceof FixpointNode fp) {
            return "fix(" + astToString(fp.body) + ")";
        }
        if (expr instanceof CaseNode c) {
            return astToString(c.expr) + " match { ... }";
        }
        return expr.toString();
    }
}
