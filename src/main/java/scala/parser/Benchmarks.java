package scala.parser;

import java.util.List;

import scala.parser.core.interpreter.Evaluator;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

public class Benchmarks {

    public static void main(String[] args) {
        System.out.println("=== Scala Parser Benchmarks ===\n");

        // Test programs of increasing complexity
        String[] programs = {
            "small",       "42",
            "medium",      "val x = 42\nval y = x + 1\nval z = y * 2\nz + x",
            "large",       generateLarge(50),
            "xlarge",      generateLarge(200),
            "recursive",   "def fact(n: Int): Int = if (iszero(n)) 1 else n * fact(pred(n))\nfact(5)",
            "data_types",  "enum Maybe[A] { case Nothing; case Just(x: A) }\nJust(42) match { case Nothing => 0; case Just(x) => x }"
        };

        for (int i = 0; i < programs.length; i += 2) {
            String name = programs[i];
            String code = programs[i + 1];
            benchmark(name, code);
        }
    }

    private static void benchmark(String name, String code) {
        System.out.println("--- " + name + " ---");
        int iterations = 1000;
        int warmup = 200;

        // Tokenize tokens once
        List<Token> tokens = new Tokenizer(code).tokenize();

        // Warmup: parse + typecheck to warm up JIT
        for (int i = 0; i < warmup; i++) {
            Parser p = new Parser(tokens);
            var r = p.parse();
            TypeChecker tc = new TypeChecker(r.errors);
            tc.check(r.ast);
        }

        // Benchmark tokenize
        System.gc();
        long start = System.nanoTime();
        List<Token> t2 = null;
        for (int i = 0; i < iterations; i++) {
            Tokenizer t = new Tokenizer(code);
            t2 = t.tokenize();
        }
        long tokenizeTime = (System.nanoTime() - start) / iterations;
        System.out.println("  Tokenize:  " + tokenizeTime + " ns  (" + t2.size() + " tokens)");

        // Benchmark parse
        System.gc();
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Parser p = new Parser(tokens);
            p.parse();
        }
        long parseTime = (System.nanoTime() - start) / iterations;
        System.out.println("  Parse:     " + parseTime + " ns");

        // Benchmark parse + type check
        System.gc();
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Parser p = new Parser(tokens);
            var result = p.parse();
            TypeChecker tc = new TypeChecker(result.errors);
            tc.check(result.ast);
        }
        long typeTime = (System.nanoTime() - start) / iterations;
        System.out.println("  + TypeCheck: " + typeTime + " ns");

        // Benchmark evaluate (only if no errors)
        String evalLabel = "  + Evaluate:  ";
        long evalTime = 0;
        {
            Parser p = new Parser(tokens);
            var result = p.parse();
            if (result.errors.isEmpty()) {
                TypeChecker tc = new TypeChecker(result.errors);
                tc.check(result.ast);
                if (result.errors.isEmpty()) {
                    System.gc();
                    start = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        Evaluator ev = new Evaluator();
                        ev.evalProgram(result.ast);
                    }
                    evalTime = (System.nanoTime() - start) / iterations;
                    System.out.println(evalLabel + evalTime + " ns");
                }
            }
            if (result.errors.isEmpty()) {
                // Already printed above
            } else {
                System.out.println(evalLabel + "(skipped - errors)");
            }
        }

        double totalMs = (tokenizeTime + parseTime + typeTime) / 1_000_000.0;
        System.out.printf("  Total:     %.3f ms\n", totalMs);
        System.out.println();
    }

    private static String generateLarge(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("val x").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("x").append(n - 1);
        return sb.toString();
    }
}
