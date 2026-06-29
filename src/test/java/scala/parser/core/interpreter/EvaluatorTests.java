package scala.parser.core.interpreter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import scala.parser.lsp.CompilerPipeline;

class EvaluatorTests {

    private Value eval(String src) {
        return new CompilerPipeline().analyze(src).value();
    }

    @Test
    void numberLiteral() {
        Value v = eval("42");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void booleanTrue() {
        Value v = eval("true");
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void booleanFalse() {
        Value v = eval("false");
        assertInstanceOf(BoolValue.class, v);
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void addition() {
        Value v = eval("1 + 2");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void subtraction() {
        Value v = eval("5 - 3");
        assertEquals(2, ((NumberValue) v).value);
    }

    @Test
    void multiplication() {
        Value v = eval("3 * 4");
        assertEquals(12, ((NumberValue) v).value);
    }

    @Test
    void division() {
        Value v = eval("10 / 3");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void precedence() {
        Value v = eval("1 + 2 * 3");
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void eqTrue() {
        Value v = eval("3 == 3");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void eqFalse() {
        Value v = eval("3 == 4");
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void neq() {
        Value v = eval("3 != 4");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void lessThan() {
        assertTrue(((BoolValue) eval("1 < 2")).value);
        assertFalse(((BoolValue) eval("2 < 1")).value);
    }

    @Test
    void greaterThan() {
        assertTrue(((BoolValue) eval("3 > 1")).value);
        assertFalse(((BoolValue) eval("1 > 3")).value);
    }

    @Test
    void leGe() {
        assertTrue(((BoolValue) eval("2 <= 2")).value);
        assertTrue(((BoolValue) eval("3 >= 2")).value);
    }

    @Test
    void ifTrue() {
        Value v = eval("if (true) 1 else 2");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void ifFalse() {
        Value v = eval("if (false) 1 else 2");
        assertEquals(2, ((NumberValue) v).value);
    }

    @Test
    void ifBoolResult() {
        Value v = eval("if (true) true else false");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void lambdaIdentity() {
        Value v = eval("((x: Int) => x)(42)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void lambdaIncrement() {
        Value v = eval("((x: Int) => x + 1)(41)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void lambdaClosure() {
        Value v = eval("val add = (x: Int) => (y: Int) => x + y\nval z = add(3)(4)");
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void valBinding() {
        Value v = eval("val x = 42");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void valUsedMultiple() {
        Value v = eval("val x = 5\nval y = x + x");
        assertEquals(10, ((NumberValue) v).value);
    }

    @Test
    void valLambda() {
        Value v = eval("val f = (x: Int) => x * 2\nval y = f(7)");
        assertEquals(14, ((NumberValue) v).value);
    }

    @Test
    void valChain() {
        Value v = eval("val a = 1\nval b = 2\nval c = a + b");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void applicationLeftAssoc() {
        Value v = eval("((x: Int) => (y: Int) => x)(1)(2)");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void builtinSucc() {
        Value v = eval("succ(41)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void builtinPred() {
        Value v = eval("pred(42)");
        assertEquals(41, ((NumberValue) v).value);
    }

    @Test
    void builtinIszeroTrue() {
        assertTrue(((BoolValue) eval("iszero(0)")).value);
    }

    @Test
    void builtinIszeroFalse() {
        assertFalse(((BoolValue) eval("iszero(1)")).value);
    }

    @Test
    void builtinCombined() {
        Value v = eval("succ(pred(42))");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void builtinInLambda() {
        Value v = eval("((x: Int) => succ(x))(41)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void nestedApplication() {
        Value v = eval("((f: Int => Int) => (x: Int) => f(f(x)))((y: Int) => y + 1)(0)");
        assertEquals(2, ((NumberValue) v).value);
    }

    @Test
    void closureCapturesVal() {
        Value v = eval("val x = 10\nval f = (y: Int) => x + y\nval r = f(5)");
        assertEquals(15, ((NumberValue) v).value);
    }

    @Test
    void defFactorial() {
        Value v = eval("def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1)\nval r = fact(5)");
        assertEquals(120, ((NumberValue) v).value);
    }

    @Test
    void defFibonacci() {
        Value v = eval("def fib(n: Int): Int = if (n == 0) 0 else if (n == 1) 1 else fib(n - 1) + fib(n - 2)\nval r = fib(10)");
        assertEquals(55, ((NumberValue) v).value);
    }

    @Test
    void defSimple() {
        Value v = eval("def x = 42\nval r = x");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void unaryNotTrue() {
        Value v = eval("!true");
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void unaryNotFalse() {
        Value v = eval("!false");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void doubleNot() {
        Value v = eval("!!true");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void unaryMinusLiteral() {
        Value v = eval("-5");
        assertEquals(-5, ((NumberValue) v).value);
    }

    @Test
    void unaryMinusExpr() {
        Value v = eval("-(3 + 4)");
        assertEquals(-7, ((NumberValue) v).value);
    }

    @Test
    void doubleMinus() {
        Value v = eval("--5");
        assertEquals(5, ((NumberValue) v).value);
    }

    @Test
    void logicalAndTrueFalse() {
        Value v = eval("true && false");
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void logicalAndTrueTrue() {
        Value v = eval("true && true");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void logicalOrTrueFalse() {
        Value v = eval("true || false");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void logicalOrFalseFalse() {
        Value v = eval("false || false");
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void stringLiteral() {
        Value v = eval("\"hello\"");
        assertInstanceOf(StringValue.class, v);
        assertEquals("hello", ((StringValue) v).value);
    }

    @Test
    void stringConcat() {
        Value v = eval("\"hello\" + \" world\"");
        assertInstanceOf(StringValue.class, v);
        assertEquals("hello world", ((StringValue) v).value);
    }

    @Test
    void valString() {
        Value v = eval("val s = \"test\"\nval r = s");
        assertInstanceOf(StringValue.class, v);
        assertEquals("test", ((StringValue) v).value);
    }

    @Test
    void divisionByZero() {
        EvaluationResult r = new CompilerPipeline().analyze("1 / 0");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinPrint() {
        Value v = eval("print(42)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void semicolonSequencing() {
        Value v = eval("val x = 1; val y = 2; val z = 3");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void zeroArgConstructor() {
        Value v = eval("enum Bool { case True; case False }\nval b = True");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("True", cv.name);
    }

    @Test
    void oneArgConstructor() {
        Value v = eval("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Just", cv.name);
        assertEquals(1, cv.args.size());
        assertEquals(42, ((NumberValue) cv.args.get(0)).value);
    }

    @Test
    void multiArgConstructor() {
        Value v = eval("enum Pair[A, B] { case Pair(x: A, y: B) }\nval p = Pair(1)(true)");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Pair", cv.name);
        assertEquals(2, cv.args.size());
        assertEquals(1, ((NumberValue) cv.args.get(0)).value);
        assertTrue(((BoolValue) cv.args.get(1)).value);
    }

    @Test
    void caseExprSimple() {
        Value v = eval("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)\nx match { case Nothing => 0; case Just(y) => y }");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void caseExprNothing() {
        Value v = eval("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Nothing\nx match { case Nothing => 0; case Just(y) => 1 }");
        assertEquals(0, ((NumberValue) v).value);
    }

    @Test
    void caseExprBoolConstructors() {
        Value v = eval("enum Bool { case True; case False }\nval b = True\nb match { case True => 1; case False => 0 }");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void caseExprMultiBinding() {
        Value v = eval("enum Pair[A, B] { case Pair(x: A, y: B) }\nval p = Pair(3)(4)\np match { case Pair(x, y) => x + y }");
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void nestedConstructorInCase() {
        Value v = eval("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(Just(42))\nx match { case Nothing => 0; case Just(y) => y match { case Nothing => 0; case Just(z) => z } }");
        assertEquals(42, ((NumberValue) v).value);
    }

    // ── Stack-safety / depth limit ───────────────────────────────

    @Test
    void depthLimitNoLeakOnError() {
        // Verify depth counter is properly decremented even when recursion fails
        var src = "def f(n: Int): Int = if (n == 0) 0 else f(n - 1); f(50)";
        var r = new CompilerPipeline().analyze(src);
        assertTrue(r.parserResult().errors.isEmpty());
        var e = new Evaluator(5);
        assertThrows(RuntimeException.class, () -> e.evalProgram(r.parserResult().ast));
        // After error, depth counter should not leak — second eval must work
        var e2 = new Evaluator(500);
        Value v = e2.evalProgram(r.parserResult().ast);
        assertEquals(0, ((NumberValue) v).value);
    }

    @Test
    void depthLimitExceeded() {
        var src = "def f(n: Int): Int = if (n == 0) 0 else f(n - 1); f(50)";
        var r = new CompilerPipeline().analyze(src);
        assertTrue(r.parserResult().errors.isEmpty());
        // Default Evaluator has maxDepth=1000; create one with a tight limit
        var e = new Evaluator(10);
        assertThrows(RuntimeException.class, () -> e.evalProgram(r.parserResult().ast),
            "Deep recursion must hit depth limit");
    }

    @Test
    void depthLimitOkWithinBound() {
        var src = "def f(n: Int): Int = if (n == 0) 0 else f(n - 1); f(5)";
        var r = new CompilerPipeline().analyze(src);
        assertTrue(r.parserResult().errors.isEmpty());
        var e = new Evaluator(20);
        Value v = e.evalProgram(r.parserResult().ast);
        assertNotNull(v);
        assertEquals(0, ((NumberValue) v).value);
    }

    @Test
    void depthLimitEnforcedInMutualRecursion() {
        // Deep mutual recursion must also hit the depth limit
        var src = "def even(n: Int): Boolean = if (n == 0) true else odd(n - 1);\n" +
                  "def odd(n: Int): Boolean = if (n == 0) false else even(n - 1);\n" +
                  "even(50)";
        var r = new CompilerPipeline().analyze(src);
        assertTrue(r.parserResult().errors.isEmpty());
        var e = new Evaluator(10);
        assertThrows(RuntimeException.class, () -> e.evalProgram(r.parserResult().ast),
            "Deep mutual recursion must hit depth limit");
    }
}
