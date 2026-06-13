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
        EvaluationResult r = analyze("if 1 then 2 else 3");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void ifBranchesSameType() {
        EvaluationResult r = analyze("if true then 1 else false");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void ifReturnType() {
        EvaluationResult r = analyze("if true then 1 else 2");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void lambdaIdentity() {
        EvaluationResult r = analyze("\\x -> x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void lambdaApplied() {
        EvaluationResult r = analyze("(\\x -> x + 1) 5");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeMismatchApplication() {
        EvaluationResult r = analyze("(\\x -> x + 1) true");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void letSimple() {
        EvaluationResult r = analyze("let x = 42\nlet y = x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void letPolymorphicIdentity() {
        EvaluationResult r = analyze("let id = \\x -> x\nlet a = id 42\nlet b = id true");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void letPolymorphicApplied() {
        EvaluationResult r = analyze("let id = \\x -> x\nlet k = id id\nlet r = k 42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void higherOrderFunction() {
        EvaluationResult r = analyze("\\f -> \\x -> f x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void nestedLambdas() {
        EvaluationResult r = analyze("\\x -> \\y -> x + y");
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
        EvaluationResult r = analyze("succ 5");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinPred() {
        EvaluationResult r = analyze("pred 5");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinIszero() {
        EvaluationResult r = analyze("iszero 0");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinPrint() {
        EvaluationResult r = analyze("print 42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void builtinWrongArgType() {
        EvaluationResult r = analyze("succ true");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void applicationChain() {
        EvaluationResult r = analyze("let x = 5\nlet y = succ x\nlet z = pred y");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void multipleLetPolymorphic() {
        EvaluationResult r = analyze("let id = \\x -> x\nlet c = id true\nlet n = id 42\nlet res = c");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void letRec() {
        EvaluationResult r = analyze("let rec fact = \\n -> if n == 0 then 1 else n * fact (n - 1)");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void letRecTypeError() {
        EvaluationResult r = analyze("let rec f = \\x -> if f then 1 else 2");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationCorrect() {
        EvaluationResult r = analyze("let x : Number = 42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationMismatch() {
        EvaluationResult r = analyze("let x : Number = true");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void typeAnnotationBool() {
        EvaluationResult r = analyze("let b : Bool = true");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclSimple() {
        EvaluationResult r = analyze("data Maybe a = Nothing | Just a");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclWithConstructorUsage() {
        EvaluationResult r = analyze("data Maybe a = Nothing | Just a\nlet x = Just 42");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void dataDeclZeroArgConstructor() {
        EvaluationResult r = analyze("data Bool = True | False\nlet b = True");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseExprTypeCheck() {
        EvaluationResult r = analyze("data Maybe a = Nothing | Just a\nlet x = Just 42\ncase x of Nothing -> 0 | Just y -> y");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseExprResultType() {
        EvaluationResult r = analyze("data Maybe a = Nothing | Just a\nlet x = Nothing\ncase x of Nothing -> 0 | Just y -> 1");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void caseBranchTypesMustMatch() {
        EvaluationResult r = analyze("data Maybe a = Nothing | Just a\nlet x = Just true\ncase x of Nothing -> 0 | Just y -> false");
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
        EvaluationResult r = analyze("let s = \"hello\"");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void unaryNotInLet() {
        EvaluationResult r = analyze("let x = true\nlet y = !x");
        assertTrue(r.parserResult().errors.isEmpty());
    }

    @Test
    void logicalChain() {
        EvaluationResult r = analyze("let a = true\nlet b = false\nlet c = a && b || !a");
        assertTrue(r.parserResult().errors.isEmpty());
    }
}
