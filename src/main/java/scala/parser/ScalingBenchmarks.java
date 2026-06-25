package scala.parser;

import java.util.*;

import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;
import scala.parser.core.interpreter.Evaluator;

/**
 * Measures pipeline latency at increasing input sizes.
 * Outputs a CSV table suitable for plotting.
 *
 * Usage: gradlew runScalingBenchmarks
 */
public class ScalingBenchmarks {

    private static final int[] SIZES = {10, 50, 100, 500, 1000};
    private static final int WARMUP = 300;
    private static final int ITERATIONS = 500;

    public static void main(String[] args) {
        System.out.println("=== Scaling Benchmarks ===\n");
        System.out.println("Phase,PhaseLabel,Size,TimeNs");

        for (int n : SIZES) {
            String arith = generateArith(n);
            run("arith", "Arithmetic chain", arith, n);
        }

        for (int n : SIZES) {
            String adt = generateAdt(n);
            run("adt", "ADT chain", adt, n);
        }

        for (int n : SIZES) {
            String rec = generateRecursive(n);
            run("rec", "Recursive calls", rec, n);
        }

        // Summary table
        System.out.println("\n=== Summary (mean total time) ===");
        System.out.printf("%-6s %-18s %-18s %-18s%n", "Size", "Arith (ms)", "ADT (ms)", "Rec (ms)");
        System.out.println("-".repeat(64));
        for (int n : SIZES) {
            // We already printed detail rows; summary for quick reference
        }
    }

    private static void run(String label, String phaseLabel, String code, int size) {
        var tokens = new Tokenizer(code).tokenize();

        // Warmup: parse + typecheck
        for (int i = 0; i < WARMUP; i++) {
            var p = new Parser(tokens);
            var r = p.parse();
            var tc = new TypeChecker(r.errors);
            tc.check(r.ast);
        }

        // Tokenize
        System.gc();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            new Tokenizer(code).tokenize();
        }
        long tokTime = (System.nanoTime() - start) / ITERATIONS;
        System.out.printf("%s,tokenize,%d,%d%n", label, size, tokTime);

        // Parse
        System.gc();
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            new Parser(tokens).parse();
        }
        long parseTime = (System.nanoTime() - start) / ITERATIONS;
        System.out.printf("%s,parse,%d,%d%n", label, size, parseTime);

        // Parse + Typecheck
        System.gc();
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            var p = new Parser(tokens);
            var r = p.parse();
            var tc = new TypeChecker(r.errors);
            tc.check(r.ast);
        }
        long tcTime = (System.nanoTime() - start) / ITERATIONS;
        System.out.printf("%s,typecheck,%d,%d%n", label, size, tcTime);

        // Evaluate (if no errors)
        var result = new Parser(tokens).parse();
        if (result.errors.isEmpty()) {
            new TypeChecker(result.errors).check(result.ast);
            if (result.errors.isEmpty()) {
                System.gc();
                start = System.nanoTime();
                for (int i = 0; i < ITERATIONS; i++) {
                    new Evaluator().evalProgram(result.ast);
                }
                long evalTime = (System.nanoTime() - start) / ITERATIONS;
                System.out.printf("%s,evaluate,%d,%d%n", label, size, evalTime);
            }
        }
    }

    /** n let-bindings: let x0 = 0; let x1 = x0 + 1; ... */
    private static String generateArith(int n) {
        var sb = new StringBuilder();
        sb.append("val x0 = 0\n");
        for (int i = 1; i < n; i++) {
            sb.append("val x").append(i).append(" = x").append(i - 1).append(" + 1\n");
        }
        sb.append("x").append(n - 1);
        return sb.toString();
    }

    /** n enum declarations with case expressions */
    private static String generateAdt(int n) {
        var sb = new StringBuilder();
        sb.append("enum MyBool { case MyTrue; case MyFalse }\n");
        sb.append("val id = (b: Int) => b\n");
        for (int i = 0; i < n; i++) {
            String ct = (i % 2 == 0) ? "MyTrue" : "MyFalse";
            sb.append(ct).append(" match { case MyTrue => 0; case MyFalse => 1 }\n");
        }
        sb.append("0");
        return sb.toString();
    }

    /** n recursive function calls */
    private static String generateRecursive(int n) {
        var sb = new StringBuilder();
        sb.append("def f(n: Int): Int = if (iszero(n)) 0 else f(pred(n))\n");
        sb.append("f(");
        sb.append(n / 2);
        sb.append(")");
        return sb.toString();
    }
}
