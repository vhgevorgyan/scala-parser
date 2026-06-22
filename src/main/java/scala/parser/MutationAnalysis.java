package scala.parser;

import java.util.*;
import java.util.stream.*;
import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.ast.CaseNode.*;
import scala.parser.core.interpreter.Evaluator;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

/**
 * Mutation analysis: applies systematic mutations to well-typed programs
 * and measures how many are caught by the pipeline.
 *
 * Outputs a table of mutation operator vs. detection rate.
 */
public class MutationAnalysis {

    private static final int TRIALS_PER_OP = 200;
    private static final List<String> IDENTS = List.of("x", "y", "z", "f", "g", "n", "b", "s");

    record Result(int total, int caughtParse, int caughtType, int caughtRuntime, int escaped) {
        double detectionRate() { return total == 0 ? 1.0 : (total - escaped) / (double) total; }
        public String toString() {
            return String.format("%5d  %5d  %5d  %5d  %5d  %6.1f%%",
                total, caughtParse, caughtType, caughtRuntime, escaped, detectionRate() * 100);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Mutation Analysis ===\n");
        System.out.println("Each mutation operator is applied to " + TRIALS_PER_OP + " well-typed base programs.");
        System.out.println("Detection: the mutation causes a parse error, type error, or runtime error.\n");

        var results = new LinkedHashMap<String, Result>();
        var rand = new Random(42);

        // Run each mutation operator
        for (var op : List.of(
            new ReplaceLiteralType(),
            new SwapOperator(),
            new SwapIfBranches(),
            new RemoveCaseBranch(),
            new SelfReference(),
            new UndefinedVariable(),
            new DivideByZero(),
            new BoolNegate(),
            new NotOnNumber(),
            new UndeclaredConstructor(),
            new TypeAnnotationMismatch(),
            new RemoveLetBinding()
        )) {
            results.put(op.name(), runOp(op, rand));
        }

        // Print table
        System.out.printf("%-32s %5s  %5s  %5s  %5s  %5s  %6s%n",
            "Operator", "Total", "Parse", "Type", "RTE", "Esc", "Rate");
        System.out.println("-".repeat(72));
        int totalAll = 0, caughtP = 0, caughtT = 0, caughtR = 0, escapedAll = 0;
        for (var e : results.entrySet()) {
            System.out.printf("%-32s %s%n", e.getKey(), e.getValue());
            var r = e.getValue();
            totalAll += r.total;
            caughtP += r.caughtParse;
            caughtT += r.caughtType;
            caughtR += r.caughtRuntime;
            escapedAll += r.escaped;
        }
        System.out.println("-".repeat(72));
        double overall = totalAll == 0 ? 1.0 : (totalAll - escapedAll) / (double) totalAll;
        System.out.printf("%-32s %5d  %5d  %5d  %5d  %5d  %6.1f%%%n",
            "OVERALL", totalAll, caughtP, caughtT, caughtR, escapedAll, overall * 100);
    }

    private static Result runOp(MutationOperator op, Random rand) {
        int total = 0, caughtParse = 0, caughtType = 0, caughtRuntime = 0, escaped = 0;
        int maxRetries = TRIALS_PER_OP * 20; // safety limit
        int retries = 0;
        for (int trials = 0; trials < TRIALS_PER_OP && retries < maxRetries; retries++) {
            var base = generateWellTyped(rand);
            String source = stringify(base);
            var tokens = new Tokenizer(source).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) continue;
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(result.ast);
            if (!errors.isEmpty()) continue;

            // Apply mutation
            var mutated = op.apply(base, rand);
            if (mutated == null) continue;

            total++;
            trials++;
            String mutatedSource = stringify(mutated);
            var mutatedTokens = new Tokenizer(mutatedSource).tokenize();
            var mutatedResult = new Parser(mutatedTokens).parse();
            if (!mutatedResult.errors.isEmpty()) {
                caughtParse++;
                continue;
            }
            var mutatedErrors = new ArrayList<ParserError>();
            new TypeChecker(mutatedErrors).check(mutatedResult.ast);
            if (!mutatedErrors.isEmpty()) {
                caughtType++;
                continue;
            }
            try {
                new Evaluator().evalProgram(mutatedResult.ast);
                escaped++;
            } catch (Exception e) {
                caughtRuntime++;
            }
        }
        return new Result(total, caughtParse, caughtType, caughtRuntime, escaped);
    }

    // ---------------------------------------------------------------
    // Well-typed program generator — guaranteed well-typed
    // Uses only numbers, arithmetic, and if(==) to prevent type errors.
    // ---------------------------------------------------------------

    private static ProgramNode generateWellTyped(Random rand) {
        int n = rand.nextInt(3) + 2;
        var stmts = new ArrayList<ASTNode>();
        // Pre-bind a variable so identifier-dependent mutations can reference it
        if (rand.nextDouble() < 0.5) {
            stmts.add(new LetStatementNode("x", new LiteralNode("42", 0, 0), 0, 0));
        }
        for (int i = 0; i < n; i++) {
            stmts.add(genSafeNumExpr(rand, 3));
        }
        return new ProgramNode(stmts);
    }

    private static ExpressionNode genSafeNumExpr(Random rand, int depth) {
        if (depth <= 0) return new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
        return switch (rand.nextInt(6)) {
            case 0, 1 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 2 -> {
                String op = rand.nextBoolean() ? "+" : "-";
                yield new BinaryExprNode(
                    new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0), op,
                    new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0), 0, 0);
            }
            case 3 -> {
                String op = rand.nextBoolean() ? "*" : "/";
                var rhs = op.equals("/") ? new LiteralNode(String.valueOf(rand.nextInt(99) + 1), 0, 0)
                    : new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
                yield new BinaryExprNode(
                    new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0), op, rhs, 0, 0);
            }
            case 4 -> {
                // == produces Bool; we wrap in if so program stays Number-typed
                var cmp = new BinaryExprNode(
                    new LiteralNode(String.valueOf(rand.nextInt(10)), 0, 0), "==",
                    new LiteralNode(String.valueOf(rand.nextInt(10)), 0, 0), 0, 0);
                yield new IfNode(cmp, genSafeNumExpr(rand, depth - 1),
                    genSafeNumExpr(rand, depth - 1));
            }
            case 5 -> {
                // < produces Bool; wrap in if
                var cmp = new BinaryExprNode(
                    new LiteralNode(String.valueOf(rand.nextInt(10)), 0, 0), "<",
                    new LiteralNode(String.valueOf(rand.nextInt(10)), 0, 0), 0, 0);
                yield new IfNode(cmp, genSafeNumExpr(rand, depth - 1),
                    genSafeNumExpr(rand, depth - 1));
            }
            default -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
        };
    }

    // ---------------------------------------------------------------
    // Mutation operator interface and implementations
    // ---------------------------------------------------------------

    interface MutationOperator {
        String name();
        ProgramNode apply(ProgramNode program, Random rand);
    }

    /** Replace a literal with a different type (Number→String, Bool→Number, etc.) */
    static class ReplaceLiteralType implements MutationOperator {
        public String name() { return "ReplaceLiteralType"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) {
                    var replaced = replaceInExpr(e, rand);
                    if (replaced != e) { done = true; stmts.add(replaced); }
                    else { stmts.add(s); }
                } else {
                    stmts.add(s);
                }
            }
            return done ? new ProgramNode(stmts) : null;
        }
        private ExpressionNode replaceInExpr(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode) {
                return rand.nextBoolean()
                    ? new StringLiteralNode("x", 0, 0)
                    : new BoolNode(0, 0, rand.nextBoolean());
            }
            if (e instanceof BoolNode) {
                return new LiteralNode("0", 0, 0);
            }
            if (e instanceof StringLiteralNode) {
                return new LiteralNode("0", 0, 0);
            }
            if (e instanceof BinaryExprNode b) {
                var l = replaceInExpr(b.left, rand);
                var r = replaceInExpr(b.right, rand);
                if (l != b.left || r != b.right)
                    return new BinaryExprNode(l, b.operator, r, 0, 0);
            }
            if (e instanceof IfNode i) {
                var c = replaceInExpr(i.condition, rand);
                if (c != i.condition)
                    return new IfNode(c, i.thenBranch, i.elseBranch);
            }
            return e;
        }
    }

    /** Swap + and - (preserves well-typedness but changes semantics) */
    static class SwapOperator implements MutationOperator {
        public String name() { return "SwapOperator"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            for (var s : program.statements) {
                stmts.add(swapInStmt(s, rand));
            }
            return new ProgramNode(stmts);
        }
        private ASTNode swapInStmt(ASTNode n, Random rand) {
            if (n instanceof BinaryExprNode b) {
                String newOp = switch (b.operator) {
                    case "+" -> "-";
                    case "-" -> "+";
                    case "*" -> "/";
                    case "/" -> "*";
                    case "&&" -> "||";
                    case "||" -> "&&";
                    case "==" -> "!=";
                    default -> b.operator;
                };
                return new BinaryExprNode((ExpressionNode)swapInStmt(b.left, rand),
                    newOp, (ExpressionNode)swapInStmt(b.right, rand), 0, 0);
            }
            if (n instanceof ExpressionNode e) return e;
            return n;
        }
    }

    /** Swap if/else branches */
    static class SwapIfBranches implements MutationOperator {
        public String name() { return "SwapIfBranches"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            for (var s : program.statements)
                stmts.add(swapIf(s));
            return new ProgramNode(stmts);
        }
        private ASTNode swapIf(ASTNode n) {
            if (n instanceof IfNode i)
                return new IfNode(i.condition, i.elseBranch, i.thenBranch);
            if (n instanceof LetStatementNode l)
                return new LetStatementNode(l.name, (ExpressionNode)swapIf(l.value),
                    l.isRec, l.declaredType, 0, 0);
            return n;
        }
    }

    /** Remove a random case branch */
    static class RemoveCaseBranch implements MutationOperator {
        public String name() { return "RemoveCaseBranch"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof CaseNode c && c.branches.size() > 1) {
                    int idx = rand.nextInt(c.branches.size());
                    var newBranches = new ArrayList<>(c.branches);
                    newBranches.remove(idx);
                    stmts.add(new CaseNode(c.expr, newBranches, 0, 0));
                    done = true;
                } else {
                    stmts.add(s);
                }
            }
            return done ? new ProgramNode(stmts) : null;
        }
    }

    /** Create let rec f = f (self-reference) */
    static class SelfReference implements MutationOperator {
        public String name() { return "SelfReference"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            stmts.add(new LetStatementNode("f", new IdentifierNode("f", 0, 0), true, null, 0, 0));
            return new ProgramNode(stmts);
        }
    }

    /** Replace an identifier with an undefined one */
    static class UndefinedVariable implements MutationOperator {
        public String name() { return "UndefinedVariable"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) {
                    stmts.add(replaceId(e, rand));
                    done = true;
                } else {
                    stmts.add(s);
                }
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode replaceId(ExpressionNode e, Random rand) {
            if (e instanceof IdentifierNode)
                return new IdentifierNode("__BAD__" + rand.nextInt(1000), 0, 0);
            if (e instanceof BinaryExprNode b)
                return new BinaryExprNode(replaceId(b.left, rand), b.operator, replaceId(b.right, rand), 0, 0);
            if (e instanceof IfNode i)
                return new IfNode(replaceId(i.condition, rand), replaceId(i.thenBranch, rand), replaceId(i.elseBranch, rand));
            if (e instanceof LambdaNode l)
                return new LambdaNode(l.param, replaceId(l.body, rand), 0, 0);
            if (e instanceof ApplicationNode a)
                return new ApplicationNode(replaceId(a.function, rand), replaceId(a.argument, rand), 0, 0);
            if (e instanceof UnaryExprNode u)
                return new UnaryExprNode(u.operator, replaceId(u.operand, rand), 0, 0);
            return e;
        }
    }

    /** Division by zero literal */
    static class DivideByZero implements MutationOperator {
        public String name() { return "DivideByZero"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) {
                    stmts.add(injectDivZero(e, rand));
                    done = true;
                } else {
                    stmts.add(s);
                }
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode injectDivZero(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode && rand.nextDouble() < 0.3)
                return new BinaryExprNode(e, "/", new LiteralNode("0", 0, 0), 0, 0);
            if (e instanceof BinaryExprNode b) {
                // Replace RHS with 0 for division
                if (b.operator.equals("/") && rand.nextBoolean())
                    return new BinaryExprNode(b.left, "/", new LiteralNode("0", 0, 0), 0, 0);
                return new BinaryExprNode(injectDivZero(b.left, rand), b.operator, injectDivZero(b.right, rand), 0, 0);
            }
            if (e instanceof IfNode i)
                return new IfNode(injectDivZero(i.condition, rand), injectDivZero(i.thenBranch, rand), injectDivZero(i.elseBranch, rand));
            return e;
        }
    }

    /** Negate a boolean with - (type mismatch) */
    static class BoolNegate implements MutationOperator {
        public String name() { return "BoolNegate"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) {
                    stmts.add(negateBoolExpr(e, rand));
                    done = true;
                } else {
                    stmts.add(s);
                }
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode negateBoolExpr(ExpressionNode e, Random rand) {
            if (e instanceof BoolNode)
                return new UnaryExprNode("-", e, 0, 0);
            if (e instanceof BinaryExprNode b) {
                var l = negateBoolExpr(b.left, rand);
                var r = negateBoolExpr(b.right, rand);
                if (l != b.left || r != b.right)
                    return new BinaryExprNode(l, b.operator, r, 0, 0);
            }
            return e;
        }
    }

    /** Apply ! to a number */
    static class NotOnNumber implements MutationOperator {
        public String name() { return "NotOnNumber"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) {
                    stmts.add(wrapWithNot(e, rand));
                    done = true;
                } else {
                    stmts.add(s);
                }
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode wrapWithNot(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode && rand.nextDouble() < 0.4)
                return new UnaryExprNode("!", e, 0, 0);
            if (e instanceof BinaryExprNode b) {
                var l = wrapWithNot(b.left, rand);
                var r = wrapWithNot(b.right, rand);
                if (l != b.left || r != b.right)
                    return new BinaryExprNode(l, b.operator, r, 0, 0);
            }
            return e;
        }
    }

    /** Use an undeclared constructor */
    static class UndeclaredConstructor implements MutationOperator {
        public String name() { return "UndeclaredConstructor"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            // Find a data decl to know which constructor would be valid,
            // then use a completely bogus constructor name
            boolean hasData = program.statements.stream().anyMatch(s -> s instanceof DataDeclNode);
            if (!hasData) return null;
            stmts.add(new IdentifierNode("NonExistentConstructor", 0, 0));
            return new ProgramNode(stmts);
        }
    }

    /** Type annotation mismatch (Number where Bool expected) */
    static class TypeAnnotationMismatch implements MutationOperator {
        public String name() { return "TypeAnnotationMismatch"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            // Add a let with mismatched type annotation
            stmts.addAll(program.statements);
            stmts.add(new LetStatementNode("_mismatch",
                new LiteralNode("42", 0, 0), false,
                new scala.parser.core.types.TypeBool(), 0, 0));
            return new ProgramNode(stmts);
        }
    }

    /** Remove a let binding (introduces undefined variable on later uses) */
    static class RemoveLetBinding implements MutationOperator {
        public String name() { return "RemoveLetBinding"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            // Find a let statement and remove it
            for (int i = 0; i < stmts.size(); i++) {
                if (stmts.get(i) instanceof LetStatementNode) {
                    stmts.remove(i);
                    return new ProgramNode(stmts);
                }
            }
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Stringifier (copied from TypeSoundnessTests to avoid dependency)
    // ---------------------------------------------------------------

    static String stringify(ASTNode node) {
        if (node instanceof ProgramNode p) {
            var sb = new StringBuilder();
            for (int i = 0; i < p.statements.size(); i++) {
                if (i > 0) sb.append(";\n");
                sb.append(stringify(p.statements.get(i)));
            }
            return sb.append('\n').toString();
        }
        if (node instanceof LetStatementNode l) {
            String rec = l.isRec ? "rec " : "";
            String ann = (l.declaredType != null) ? " : " + l.declaredType : "";
            return "let " + rec + l.name + ann + " = " + stringify(l.value);
        }
        if (node instanceof DataDeclNode d) {
            var sb = new StringBuilder("data ").append(d.name);
            for (var p : d.params) sb.append(' ').append(p);
            sb.append(" = ");
            for (int i = 0; i < d.constructors.size(); i++) {
                if (i > 0) sb.append(" | ");
                var c = d.constructors.get(i);
                sb.append(c.name());
                for (var a : c.argTypes()) sb.append(' ').append(a);
            }
            return sb.toString();
        }
        if (node instanceof LambdaNode lam) {
            return "\\" + lam.param + " -> " + stringify(lam.body);
        }
        if (node instanceof ApplicationNode app) {
            var fn = stringify(app.function);
            var a = stringify(app.argument);
            if (needsParen(app.function)) fn = "(" + fn + ")";
            if (needsParen(app.argument)) a = "(" + a + ")";
            return "(" + fn + " " + a + ")";
        }
        if (node instanceof IfNode i) {
            return "if " + stringify(i.condition) + " then " + stringify(i.thenBranch)
                + " else " + stringify(i.elseBranch);
        }
        if (node instanceof LiteralNode lit) return lit.value;
        if (node instanceof BoolNode b) return String.valueOf(b.value);
        if (node instanceof StringLiteralNode s) return "\"" + escapeStr(s.value) + "\"";
        if (node instanceof IdentifierNode id) return id.name;
        if (node instanceof BinaryExprNode bin) {
            var l = stringify(bin.left);
            var r = stringify(bin.right);
            if (needsParen(bin.left)) l = "(" + l + ")";
            if (needsParen(bin.right)) r = "(" + r + ")";
            return "(" + l + " " + bin.operator + " " + r + ")";
        }
        if (node instanceof UnaryExprNode un) {
            var op = stringify(un.operand);
            if (needsParen(un.operand)) op = "(" + op + ")";
            return un.operator + op;
        }
        if (node instanceof CaseNode c) {
            var sb = new StringBuilder("case ").append(stringify(c.expr)).append(" of ");
            for (int i = 0; i < c.branches.size(); i++) {
                if (i > 0) sb.append(" | ");
                var br = c.branches.get(i);
                sb.append(br.constructor());
                for (var b : br.bindings()) sb.append(' ').append(b.name());
                sb.append(" -> ");
                var body = stringify(br.body());
                if (containsCase(br.body())) body = "(" + body + ")";
                sb.append(body);
            }
            return sb.toString();
        }
        return "0";
    }

    private static boolean containsCase(ExpressionNode e) {
        if (e instanceof CaseNode) return true;
        if (e instanceof LambdaNode lam) return containsCase(lam.body);
        if (e instanceof IfNode i) return containsCase(i.condition) || containsCase(i.thenBranch) || containsCase(i.elseBranch);
        if (e instanceof ApplicationNode app) return containsCase(app.function) || containsCase(app.argument);
        if (e instanceof BinaryExprNode bin) return containsCase(bin.left) || containsCase(bin.right);
        if (e instanceof UnaryExprNode un) return containsCase(un.operand);
        return false;
    }

    private static boolean needsParen(ExpressionNode e) {
        return e instanceof IfNode || e instanceof LambdaNode || e instanceof CaseNode;
    }

    private static String escapeStr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }
}
