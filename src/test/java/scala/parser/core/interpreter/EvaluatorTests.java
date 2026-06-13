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
        Value v = eval("if true then 1 else 2");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void ifFalse() {
        Value v = eval("if false then 1 else 2");
        assertEquals(2, ((NumberValue) v).value);
    }

    @Test
    void ifBoolResult() {
        Value v = eval("if true then true else false");
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void lambdaIdentity() {
        Value v = eval("(\\x -> x) 42");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void lambdaIncrement() {
        Value v = eval("(\\x -> x + 1) 41");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void lambdaClosure() {
        Value v = eval("let add = \\x -> \\y -> x + y\nlet z = add 3 4");
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void letBinding() {
        Value v = eval("let x = 42");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void letUsedMultiple() {
        Value v = eval("let x = 5\nlet y = x + x");
        assertEquals(10, ((NumberValue) v).value);
    }

    @Test
    void letLambda() {
        Value v = eval("let f = \\x -> x * 2\nlet y = f 7");
        assertEquals(14, ((NumberValue) v).value);
    }

    @Test
    void letChain() {
        Value v = eval("let a = 1\nlet b = 2\nlet c = a + b");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void applicationLeftAssoc() {
        // ((\\x -> \\y -> x) 1) 2
        Value v = eval("(\\x -> \\y -> x) 1 2");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void builtinSucc() {
        Value v = eval("succ 41");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void builtinPred() {
        Value v = eval("pred 42");
        assertEquals(41, ((NumberValue) v).value);
    }

    @Test
    void builtinIszeroTrue() {
        assertTrue(((BoolValue) eval("iszero 0")).value);
    }

    @Test
    void builtinIszeroFalse() {
        assertFalse(((BoolValue) eval("iszero 1")).value);
    }

    @Test
    void builtinCombined() {
        Value v = eval("succ (pred 42)");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void builtinInLambda() {
        Value v = eval("(\\x -> succ x) 41");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void nestedApplication() {
        Value v = eval("(\\f -> \\x -> f (f x)) (\\y -> y + 1) 0");
        assertEquals(2, ((NumberValue) v).value);
    }

    @Test
    void closureCapturesLet() {
        Value v = eval("let x = 10\nlet f = \\y -> x + y\nlet r = f 5");
        assertEquals(15, ((NumberValue) v).value);
    }

    @Test
    void letRecFactorial() {
        Value v = eval("let rec fact = \\n -> if n == 0 then 1 else n * fact (n - 1)\nlet r = fact 5");
        assertEquals(120, ((NumberValue) v).value);
    }

    @Test
    void letRecFibonacci() {
        Value v = eval("let rec fib = \\n -> if n == 0 then 0 else if n == 1 then 1 else fib (n - 1) + fib (n - 2)\nlet r = fib 10");
        assertEquals(55, ((NumberValue) v).value);
    }

    @Test
    void letRecSimple() {
        Value v = eval("let rec x = 42\nlet r = x");
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
    void letString() {
        Value v = eval("let s = \"test\"\nlet r = s");
        assertInstanceOf(StringValue.class, v);
        assertEquals("test", ((StringValue) v).value);
    }

    @Test
    void divisionByZero() {
        assertThrows(RuntimeException.class, () -> eval("1 / 0"));
    }

    @Test
    void builtinPrint() {
        Value v = eval("print 42");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void semicolonSequencing() {
        Value v = eval("let x = 1; let y = 2; let z = 3");
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void zeroArgConstructor() {
        Value v = eval("data Bool = True | False\nlet b = True");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("True", cv.name);
    }

    @Test
    void oneArgConstructor() {
        Value v = eval("data Maybe a = Nothing | Just a\nlet x = Just 42");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Just", cv.name);
        assertEquals(1, cv.args.size());
        assertEquals(42, ((NumberValue) cv.args.get(0)).value);
    }

    @Test
    void multiArgConstructor() {
        Value v = eval("data Pair a b = Pair a b\nlet p = Pair 1 true");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Pair", cv.name);
        assertEquals(2, cv.args.size());
        assertEquals(1, ((NumberValue) cv.args.get(0)).value);
        assertTrue(((BoolValue) cv.args.get(1)).value);
    }

    @Test
    void caseExprSimple() {
        Value v = eval("data Maybe a = Nothing | Just a\nlet x = Just 42\ncase x of Nothing -> 0 | Just y -> y");
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void caseExprNothing() {
        Value v = eval("data Maybe a = Nothing | Just a\nlet x = Nothing\ncase x of Nothing -> 0 | Just y -> 1");
        assertEquals(0, ((NumberValue) v).value);
    }

    @Test
    void caseExprBoolConstructors() {
        Value v = eval("data Bool = True | False\nlet b = True\ncase b of True -> 1 | False -> 0");
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void caseExprMultiBinding() {
        Value v = eval("data Pair a b = Pair a b\nlet p = Pair 3 4\ncase p of Pair x y -> x + y");
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void nestedConstructorInCase() {
        Value v = eval("data Maybe a = Nothing | Just a\nlet x = Just (Just 42)\ncase x of Nothing -> 0 | Just y -> case y of Nothing -> 0 | Just z -> z");
        assertEquals(42, ((NumberValue) v).value);
    }
}
