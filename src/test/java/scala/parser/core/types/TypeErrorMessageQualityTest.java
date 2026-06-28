package scala.parser.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.interpreter.EvaluationResult;
import scala.parser.lsp.CompilerPipeline;

class TypeErrorMessageQualityTest {

    private EvaluationResult analyze(String src) {
        return new CompilerPipeline().analyze(src);
    }

    private String firstError(String src) {
        var errors = analyze(src).parserResult().errors;
        assertFalse(errors.isEmpty(), "Expected at least one error for: " + src);
        return errors.get(0).message;
    }

    private List<ParserError> allErrors(String src) {
        return analyze(src).parserResult().errors;
    }

    @Test
    void errorFormatContainsExpectedAndGot() {
        for (String src : List.of(
            "true + 1",
            "42 && false",
            "!42",
            "-true",
            "if (1) 2 else 3",
            "((x: Int) => x + 1)(true)",
            "val x: Int = true"
        )) {
            String msg = firstError(src);
            assertTrue(msg.contains("expected") && msg.contains("but got"),
                "Message must follow 'expected ... but got' format, got: '" + msg + "' for: " + src);
        }
    }

    @Test
    void ifBranchesLabeled() {
        String msg = firstError("if (true) 1 else false");
        assertTrue(msg.contains("then") && msg.contains("else"),
            "Should label both branches, got: " + msg);
    }

    @Test
    void binaryOperatorLeftOperand() {
        String msg = firstError("1 + true");
        assertTrue(msg.contains("operand"), "Should mention operand: " + msg);
    }

    @Test
    void binaryOperatorRightOperand() {
        String msg = firstError("true + 1");
        assertTrue(msg.contains("operand"), "Should mention operand: " + msg);
    }

    @Test
    void typeAscriptionMismatch() {
        String msg = firstError("(42: Boolean)");
        assertTrue(msg.contains("expected") && msg.contains("but got"),
            "Ascription error should show types, got: " + msg);
    }

    @Test
    void recursiveLetTypeErrorShowsTypes() {
        String msg = firstError("def f(x: Int): Boolean = x");
        assertTrue(msg.length() > 40, "Error should be descriptive: " + msg);
    }

    @Test
    void rangeOperatorErrors() {
        String msg = firstError("true..10");
        assertTrue(msg.contains("Range"), "Should mention 'Range': " + msg);
        assertTrue(msg.contains("expected") && msg.contains("but got"),
            "Should follow format: " + msg);
    }

    @Test
    void caseBranchTypeMismatchShowsLabels() {
        String src = "enum Maybe[A] { case Nothing; case Just(x: A) }\n" +
                     "val x = Just(true)\n" +
                     "x match { case Nothing => 0; case Just(y) => false }";
        String msg = firstError(src);
        assertTrue(msg.contains("previous") && msg.contains("this branch"),
            "Should label branch comparison, got: " + msg);
    }

    @Test
    void notOnNumberMessage() {
        String msg = firstError("!42");
        assertTrue(msg.contains("expected") && msg.contains("Bool"),
            "Should indicate expected Bool: " + msg);
    }

    @Test
    void unaryMinusOnBoolMessage() {
        String msg = firstError("-true");
        assertTrue(msg.contains("expected") && msg.contains("Int"),
            "Should indicate expected Int: " + msg);
    }
}
