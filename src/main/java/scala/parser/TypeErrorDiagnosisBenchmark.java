package scala.parser;

import java.util.*;
import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

public class TypeErrorDiagnosisBenchmark {

    record ErrorCase(String name, String source, boolean expectError, int expectedLine, int expectedCol, String expectedFragments) {
        ErrorCase(String name, String source) {
            this(name, source, true, 0, 0, "");
        }
    }

    record DiagnosisResult(int total, int caught, int onRightLine, int onRightCol, int meaningfulMessage) {
        public String toString() {
            return String.format("total=%d caught=%d (%.1f%%)  rightLine=%d  rightCol=%d  meaningful=%d",
                total, caught, (total == 0 ? 0 : caught * 100.0 / total), onRightLine, onRightCol, meaningfulMessage);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Type Error Diagnosis Benchmark ===\n");
        System.out.println("Evaluates error message quality for a curated set of type errors.\n");

        var cases = List.of(
            new ErrorCase("typeMismatchArg", "def f(x: Int): Int = x; f(true)"),
            new ErrorCase("ifCondNotBool", "if (42) 1 else 0"),
            new ErrorCase("binaryOpType", "1 + true"),
            new ErrorCase("negateBool", "-true"),
            new ErrorCase("notOnNumber", "!42"),
            new ErrorCase("undefinedVar", "val x: Int = y"),
            new ErrorCase("annotationMismatchLet", "val x: Boolean = 42"),
            new ErrorCase("annotationMismatchDef", "def f(x: Int): Boolean = x"),
            new ErrorCase("recSelfRef", "def f: Int = f"),
            new ErrorCase("applicationNotFunction", "42(1)"),
            new ErrorCase("arityMismatch", "def f(x: Int): Int = x; f(1, 2)"),
            new ErrorCase("nonExhaustiveMatch", "enum E { case A; case B }; A match { case A => 0 }"),
            new ErrorCase("unknownConstructor", "enum E { case A; case B }; A match { case X => 0 }"),
            new ErrorCase("ifBranchesTypeMismatch", "if (true) 42 else true"),
            new ErrorCase("rangeOnBool", "true..false"),
            new ErrorCase("negateString", "-\"hello\""),
            new ErrorCase("undefinedBuiltinWrong", "succ(true)"),
            new ErrorCase("lambdaArgType", "((x: Int) => x + 1)(true)")
        );

        int total = 0, caught = 0, onRightLine = 0, onRightCol = 0, meaningful = 0;

        for (var c : cases) {
            total++;
            Tokenizer t = new Tokenizer(c.source);
            Parser p = new Parser(t.tokenize());
            ParserResult pr = p.parse();
            ProgramNode program = pr.ast;

            if (!pr.errors.isEmpty()) {
                System.out.println("  PARSE ERROR (skipped): " + c.name + " — " + pr.errors.get(0).message);
                continue;
            }

            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(program);

            boolean hasError = !errors.isEmpty();
            if (hasError) {
                caught++;
                var e = errors.get(0);
                boolean lineOk = c.expectedLine == 0 || e.line == c.expectedLine;
                boolean colOk = c.expectedCol == 0 || e.column == c.expectedCol;
                boolean msgOk = c.expectedFragments.isEmpty() || e.message.contains(c.expectedFragments);
                if (lineOk) onRightLine++;
                if (colOk) onRightCol++;
                if (msgOk) meaningful++;
                if (!lineOk || !colOk) {
                    System.out.println("  LOCATION OFF: " + c.name + " got L" + e.line + ":" + e.column + " — " + e.message);
                }
            } else {
                System.out.println("  ESCAPED: " + c.name + " — no type error detected");
            }
        }

        System.out.println();
        System.out.println("=== Results ===");
        System.out.println("Total cases: " + total);
        System.out.println("Caught: " + caught + "/" + total + " (" + (total == 0 ? 0 : caught * 100.0 / total) + "%)");
        System.out.println("Right line: " + onRightLine + "/" + caught);
        System.out.println("Right column: " + onRightCol + "/" + caught);
        System.out.println("Meaningful message: " + meaningful + "/" + caught);
    }
}
