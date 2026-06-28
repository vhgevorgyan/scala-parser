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

    record Point(int size, long ns) {}

    private static final int[] SIZES = {10, 50, 100, 500, 1000};
    private static final int WARMUP = 300;
    private static final int ITERATIONS = 500;

    private static final List<Point> arithTok = new ArrayList<>();
    private static final List<Point> arithPar = new ArrayList<>();
    private static final List<Point> arithTc = new ArrayList<>();
    private static final List<Point> arithEval = new ArrayList<>();
    private static final List<Point> adtTok = new ArrayList<>();
    private static final List<Point> adtPar = new ArrayList<>();
    private static final List<Point> adtTc = new ArrayList<>();
    private static final List<Point> adtEval = new ArrayList<>();
    private static final List<Point> recTok = new ArrayList<>();
    private static final List<Point> recPar = new ArrayList<>();
    private static final List<Point> recTc = new ArrayList<>();
    private static final List<Point> recEval = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== Scaling Benchmarks ===\n");

        for (int n : SIZES) {
            String arith = generateArith(n);
            run("arith", "Arithmetic chain", arith, n,
                arithTok, arithPar, arithTc, arithEval);
        }

        for (int n : SIZES) {
            String adt = generateAdt(n);
            run("adt", "ADT chain", adt, n,
                adtTok, adtPar, adtTc, adtEval);
        }

        for (int n : SIZES) {
            String rec = generateRecursive(n);
            run("rec", "Recursive calls", rec, n,
                recTok, recPar, recTc, recEval);
        }

        printSummary("Arithmetic chain", arithTok, arithPar, arithTc, arithEval);
        printSummary("ADT chain",       adtTok,   adtPar,   adtTc,   adtEval);
        printSummary("Recursive calls", recTok,   recPar,   recTc,   recEval);
    }

    private static void printSummary(String label,
            List<Point> tok, List<Point> par, List<Point> tc, List<Point> eval) {
        System.out.println("\n─── " + label + " ───");
        System.out.printf("%-6s  %12s  %12s  %12s  %12s%n",
            "Size", "Tokenize(µs)", "Parse(µs)", "TypeCheck(µs)", "Evaluate(µs)");
        System.out.println("-".repeat(62));
        for (int i = 0; i < tok.size(); i++) {
            int s = tok.get(i).size;
            System.out.printf("%-6d  %12.3f  %12.3f  %12.3f  %12.3f%n",
                s,
                tok.get(i).ns / 1000.0,
                par.get(i).ns / 1000.0,
                tc.get(i).ns / 1000.0,
                i < eval.size() ? eval.get(i).ns / 1000.0 : 0);
        }

        // Estimate O-notation from largest two points (avoids JIT warmup distortion)
        if (tok.size() >= 4) {
            int i1 = tok.size() - 2, i2 = tok.size() - 1;
            double n1 = tok.get(i1).size, n2 = tok.get(i2).size;
            double tokG = estimateGrowth(tok.get(i1).ns, n1, tok.get(i2).ns, n2);
            double parG = estimateGrowth(par.get(i1).ns, n1, par.get(i2).ns, n2);
            double tcG  = estimateGrowth(tc.get(i1).ns,  n1, tc.get(i2).ns,  n2);
            double evG  = eval.size() >= 2
                ? estimateGrowth(eval.get(i1).ns, n1, eval.get(i2).ns, n2) : -1;
            System.out.printf("  O(growth from %d→%d): tok=%.2f  par=%.2f  tc=%.2f  eval=%.2f%n",
                (int)n1, (int)n2, tokG, parG, tcG, evG);
            System.out.println("  (1.0 = O(n), 2.0 = O(n²), 0.0 = O(1))");
        }
    }

    private static void run(String label, String phaseLabel, String code, int size,
            List<Point> tokOut, List<Point> parOut, List<Point> tcOut, List<Point> evalOut) {
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
        tokOut.add(new Point(size, tokTime));

        // Parse
        System.gc();
        start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            new Parser(tokens).parse();
        }
        long parseTime = (System.nanoTime() - start) / ITERATIONS;
        parOut.add(new Point(size, parseTime));

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
        tcOut.add(new Point(size, tcTime));

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
                evalOut.add(new Point(size, evalTime));
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

    /** n recursive function calls (eval depth capped at 100 to avoid stack overflow) */
    private static String generateRecursive(int n) {
        var sb = new StringBuilder();
        sb.append("def f(n: Int): Int = if (iszero(n)) 0 else f(pred(n))\n");
        int arg = Math.min(100, n / 2);
        sb.append("f(");
        sb.append(arg);
        sb.append(")");
        return sb.toString();
    }

    // ── Analysis helpers (called after run to compute O-notation) ──

    static double estimateGrowth(double t1, double n1, double t2, double n2) {
        return Math.log(t2 / t1) / Math.log(n2 / n1);
    }
}
