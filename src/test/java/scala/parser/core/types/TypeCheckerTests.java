package scala.parser.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.interpreter.EvaluationResult;
import scala.parser.lsp.CompilerPipeline;

class TypeCheckerTests {

    private EvaluationResult analyze(String src) {
        return new CompilerPipeline().analyze(src);
    }

    @Test
    void numberLiteral() {
        EvaluationResult r = analyze("42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void booleanLiteral() {
        EvaluationResult r = analyze("true");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void arithmetic() {
        EvaluationResult r = analyze("1 + 2 * 3");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void comparison() {
        EvaluationResult r = analyze("1 + 2 == 3");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void arithmeticTypeError() {
        EvaluationResult r = analyze("true + 1");
        assertFalse(r.parserResult().errors.isEmpty());
        String msg = r.parserResult().errors.get(0).message;
        assertTrue(msg.contains("+") || msg.contains("number") || msg.contains("requires numbers"));
    }

    @Test
    void comparisonTypeError() {
        EvaluationResult r = analyze("true == false");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void ifConditionMustBeBool() {
        EvaluationResult r = analyze("if (1) 2 else 3");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void ifBranchesSameType() {
        EvaluationResult r = analyze("if (true) 1 else false");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void ifReturnType() {
        EvaluationResult r = analyze("if (true) 1 else 2");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void lambdaIdentity() {
        EvaluationResult r = analyze("(x: Int) => x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void lambdaApplied() {
        EvaluationResult r = analyze("((x: Int) => x + 1)(5)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeMismatchApplication() {
        EvaluationResult r = analyze("((x: Int) => x + 1)(true)");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void valSimple() {
        EvaluationResult r = analyze("val x = 42\nval y = x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void valPolymorphicIdentity() {
        EvaluationResult r = analyze("val id = (x) => x\nval a = id(42)\nval b = id(true)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void valPolymorphicApplied() {
        EvaluationResult r = analyze("val id = (x) => x\nval k = id(id)\nval r = k(42)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void higherOrderFunction() {
        EvaluationResult r = analyze("(f: Int => Int) => (x) => f(x)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void nestedLambdas() {
        EvaluationResult r = analyze("(x: Int) => (y: Int) => x + y");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void undefinedVariable() {
        EvaluationResult r = analyze("undefinedVar");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("Undefined"));
    }

    @Test
    void builtinSucc() {
        EvaluationResult r = analyze("succ(5)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinPred() {
        EvaluationResult r = analyze("pred(5)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinIszero() {
        EvaluationResult r = analyze("iszero(0)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinPrint() {
        EvaluationResult r = analyze("print(42)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinWrongArgType() {
        EvaluationResult r = analyze("succ(true)");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void applicationChain() {
        EvaluationResult r = analyze("val x = 5\nval y = succ(x)\nval z = pred(y)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void multipleValPolymorphic() {
        EvaluationResult r = analyze("val id = (x) => x\nval c = id(true)\nval n = id(42)\nval res = c");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void defRec() {
        EvaluationResult r = analyze("def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void defRecTypeError() {
        EvaluationResult r = analyze("def f(x: Boolean): Int = if (f) 1 else 2");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationCorrect() {
        EvaluationResult r = analyze("val x: Int = 42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationMismatch() {
        EvaluationResult r = analyze("val x: Int = true");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationBool() {
        EvaluationResult r = analyze("val b: Boolean = true");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclSimple() {
        EvaluationResult r = analyze("enum Maybe[A] { case Nothing; case Just(x: A) }");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclWithConstructorUsage() {
        EvaluationResult r = analyze("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclZeroArgConstructor() {
        EvaluationResult r = analyze("enum Bool { case True; case False }\nval b = True");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseExprTypeCheck() {
        EvaluationResult r = analyze("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)\nx match { case Nothing => 0; case Just(y) => y }");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseExprResultType() {
        EvaluationResult r = analyze("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Nothing\nx match { case Nothing => 0; case Just(y) => 1 }");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseBranchTypesMustMatch() {
        EvaluationResult r = analyze("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(true)\nx match { case Nothing => 0; case Just(y) => false }");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalNot() {
        EvaluationResult r = analyze("!true");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalNotNonBool() {
        EvaluationResult r = analyze("!42");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void unaryMinus() {
        EvaluationResult r = analyze("-5");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void unaryMinusNonNumber() {
        EvaluationResult r = analyze("-true");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalAnd() {
        EvaluationResult r = analyze("true && false");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalOr() {
        EvaluationResult r = analyze("true || false");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalAndNonBool() {
        EvaluationResult r = analyze("1 && 2");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalOrNonBool() {
        EvaluationResult r = analyze("1 || 2");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void stringLiteralType() {
        EvaluationResult r = analyze("val s = \"hello\"");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void unaryNotInVal() {
        EvaluationResult r = analyze("val x = true\nval y = !x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalChain() {
        EvaluationResult r = analyze("val a = true\nval b = false\nval c = a && b || !a");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void charLiteralType() {
        EvaluationResult r = analyze("val c: Char = 'a'");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void charTypeMismatch() {
        EvaluationResult r = analyze("val c: Int = 'a'");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void whileConditionMustBeBool() {
        EvaluationResult r = analyze("while (1) {}");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void whileConditionBool() {
        EvaluationResult r = analyze("while (false) {}");
        assertTrue(r.parserResult().errors.isEmpty());
    }
}
