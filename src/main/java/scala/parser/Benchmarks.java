package scala.parser;

import java.util.List;

import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

public class Benchmarks {

    public static void main(String[] args) {
        System.out.println("=== Scala Parser Benchmarks ===\n");

        // Test programs of increasing complexity
        String[] programs = {
            "small",       "42",
            "medium",      "let x = 42\nlet y = x + 1\nlet z = y * 2\nz + x",
            "large",       generateLarge(50),
            "xlarge",      generateLarge(200),
            "recursive",   "let rec fact = \\n -> if iszero n then 1 else n * fact (pred n)\nfact 5",
            "data_types",  "data Maybe a = Nothing | Just a\ncase Just 42 of Nothing -> 0; Just x -> x"
        };

        for (int i = 0; i < programs.length; i += 2) {
            String name = programs[i];
            String code = programs[i + 1];
            benchmark(name, code);
        }
    }

    private static void benchmark(String name, String code) {
        System.out.println("--- " + name + " ---");

        // Tokenize
        long start = System.nanoTime();
        int iterations = 1000;
        List<Token> tokens = null;
        for (int i = 0; i < iterations; i++) {
            Tokenizer t = new Tokenizer(code);
            tokens = t.tokenize();
        }
        long tokenizeTime = (System.nanoTime() - start) / iterations;
        System.out.println("  Tokenize:  " + tokenizeTime + " ns  (" + tokens.size() + " tokens)");

        // Parse
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Parser p = new Parser(tokens);
            p.parse();
        }
        long parseTime = (System.nanoTime() - start) / iterations;
        System.out.println("  Parse:     " + parseTime + " ns");

        // Parse + Type Check
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Parser p = new Parser(tokens);
            var result = p.parse();
            TypeChecker tc = new TypeChecker(result.errors);
            tc.check(result.ast);
        }
        long typeTime = (System.nanoTime() - start) / iterations;
        System.out.println("  + TypeCheck: " + typeTime + " ns");

        // Total throughput
        double totalMs = (tokenizeTime + parseTime + typeTime) / 1_000_000.0;
        System.out.printf("  Total:     %.3f ms\n", totalMs);

        System.out.println();
    }

    private static String generateLarge(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("let x").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("x").append(n - 1);
        return sb.toString();
    }
}
