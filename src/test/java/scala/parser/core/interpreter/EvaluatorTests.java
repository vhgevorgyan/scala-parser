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
}
