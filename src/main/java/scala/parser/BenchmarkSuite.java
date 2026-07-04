package scala.parser;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.*;
import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.ast.CaseNode.*;
import scala.parser.core.ast.EnumDeclNode.ConstructorDef;
import scala.parser.core.interpreter.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.reduction.SmallStepReducer;
import scala.parser.core.reduction.Substitution;
import scala.parser.core.types.*;

/**
 * Benchmark suite producing tables/CSV data for thesis graphs.
 *
 * Run all:        gradlew runBenchmarkSuite
 * Individual:     gradlew runBenchmarkSuite --args="mutation property scalability typeerror coverage"
 */
public class BenchmarkSuite {

    // ── Shared random generator (seeded for reproducibility) ─────────
    private static final Random SHARED = new Random(42);

    // Suppress System.out during evaluator calls (print builtin would pollute output)
    private static PrintStream SAVED_OUT;
    private static void silencePrint() { SAVED_OUT = System.out; System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream())); }
    private static void restorePrint() { System.setOut(SAVED_OUT); }

    // ── AST helpers ──────────────────────────────────────────────────

    private static int countASTNodes(ASTNode node) {
        if (node instanceof ExpressionNode e) return countExprNodes(e);
        if (node instanceof ProgramNode p) {
            int sum = 1;
            for (var s : p.statements) sum += countASTNodes(s);
            return sum;
        }
        if (node instanceof LetStatementNode l) {
            int sum = 1 + countASTNodes(l.value);
            if (l.declaredType != null) sum += countTypeNodes(l.declaredType);
            return sum;
        }
        if (node instanceof MutualLetNode m) {
            int sum = 1;
            for (var d : m.defs) sum += countASTNodes(d);
            return sum;
        }
        if (node instanceof EnumDeclNode d) {
            return 1 + d.constructors.stream().mapToInt(c -> 1).sum();
        }
        if (node instanceof TypeAliasNode) return 1;
        return 1;
    }

    private static int countExprNodes(ExpressionNode e) {
        if (e instanceof LiteralNode) return 1;
        if (e instanceof BoolNode) return 1;
        if (e instanceof StringLiteralNode) return 1;
        if (e instanceof UnitLiteralNode) return 1;
        if (e instanceof IdentifierNode) return 1;
        if (e instanceof LambdaNode lam) return 1 + countExprNodes(lam.body);
        if (e instanceof ApplicationNode app) return 1 + countExprNodes(app.function) + countExprNodes(app.argument);
        if (e instanceof IfNode i) return 1 + countExprNodes(i.condition) + countExprNodes(i.thenBranch) + (i.elseBranch != null ? countExprNodes(i.elseBranch) : 0);
        if (e instanceof BinaryExprNode bin) return 1 + countExprNodes(bin.left) + countExprNodes(bin.right);
        if (e instanceof UnaryExprNode un) return 1 + countExprNodes(un.operand);
        if (e instanceof TupleNode tup) return 1 + tup.elements.stream().mapToInt(BenchmarkSuite::countExprNodes).sum();
        if (e instanceof AscriptionNode asc) return 1 + countExprNodes(asc.expr);
        if (e instanceof FixpointNode fp) return 1 + countExprNodes(fp.body);
        if (e instanceof CaseNode cn) return 1 + countExprNodes(cn.expr) + cn.branches.stream().mapToInt(b -> 1 + countExprNodes(b.body())).sum();
        if (e instanceof BlockNode b) return 1 + b.statements.stream().mapToInt(BenchmarkSuite::countASTNodes).sum();
        return 1;
    }

    private static int countTypeNodes(Type t) {
        if (t instanceof TypeNumber || t instanceof TypeBool || t instanceof TypeString || t instanceof TypeUnit || t instanceof TypeDynamic || t instanceof TypeVar) return 1;
        if (t instanceof TypeFun f) return 1 + countTypeNodes(f.from()) + countTypeNodes(f.to());
        if (t instanceof TypeConstructor) return 1;
        if (t instanceof TypeTuple) return 1;
        return 1;
    }

    private static int countReductionSteps(ExpressionNode expr) {
        ExpressionNode current = expr;
        int steps = 0;
        while (!SmallStepReducer.isValue(current)) {
            try {
                current = SmallStepReducer.step(current);
                steps++;
                if (steps > 10000) return -1;
            } catch (RuntimeException e) {
                return -1;
            }
        }
        return steps;
    }

    // ── Well-typed program generator (same as MutationAnalysis) ──────

    private static final List<String> IDENTS = List.of("x", "y", "z", "f", "g", "n", "b", "s");
    private static final List<String> BINOPS = List.of("+", "-", "*", "/", "==", "!=", "<", ">", "<=", ">=", "&&", "||");
    private static final List<String> SAMPLE_STRINGS = List.of("hello", "", "42", "true", "x");

    record DataTemplate(String name, List<String> params, List<CtorDef> ctors) {}
    record CtorDef(String name, List<String> argTypes) {}

    private static final List<DataTemplate> DATA_TPL = List.of(
        new DataTemplate("MyBool", List.of(),
            List.of(new CtorDef("MyTrue", List.of()), new CtorDef("MyFalse", List.of()))),
        new DataTemplate("MyMaybe", List.of("a"),
            List.of(new CtorDef("MyNothing", List.of()), new CtorDef("MyJust", List.of("a"))))
    );

    private static ProgramNode generateWellTyped(Random rand, int targetNodes) {
        int depth = Math.min(6, Math.max(2, targetNodes / 10));
        int n = Math.max(1, Math.min(10, targetNodes / 3 + 1));
        var stmts = new ArrayList<ASTNode>();
        boolean hasData = rand.nextDouble() < 0.3 && !DATA_TPL.isEmpty();
        DataTemplate tpl = hasData ? DATA_TPL.get(rand.nextInt(DATA_TPL.size())) : null;
        if (hasData) {
            stmts.add(new EnumDeclNode(tpl.name(), tpl.params(),
                tpl.ctors().stream().map(c -> new ConstructorDef(c.name(), c.argTypes())).toList(),
                0, 0));
        }
        for (int i = stmts.size(); i < n; i++) {
            if (rand.nextDouble() < 0.4 && i < n - 1) {
                stmts.add(new LetStatementNode(
                    IDENTS.get(rand.nextInt(IDENTS.size())), genExpr(rand, depth, tpl), 0, 0));
            } else {
                stmts.add(genExpr(rand, depth, tpl));
            }
        }
        return new ProgramNode(stmts);
    }

    private static ExpressionNode genExpr(Random rand, int depth, DataTemplate tpl) {
        if (depth <= 0 || (rand.nextDouble() < 0.12 && depth < 4)) return genLeaf(rand);
        return switch (rand.nextInt(14)) {
            case 0, 1 -> genLeaf(rand);
            case 2 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 3 -> new BoolNode(0, 0, rand.nextBoolean());
            case 4 -> new StringLiteralNode(SAMPLE_STRINGS.get(rand.nextInt(SAMPLE_STRINGS.size())), 0, 0);
            case 5 -> new LambdaNode(IDENTS.get(rand.nextInt(IDENTS.size())),
                genExpr(rand, depth - 1, tpl), 0, 0);
            case 6 -> new ApplicationNode(genExpr(rand, depth - 1, tpl),
                genExpr(rand, depth - 1, tpl), 0, 0);
            case 7 -> {
                ExpressionNode c = genExpr(rand, depth - 1, tpl);
                yield new IfNode(c, genExpr(rand, depth - 1, tpl),
                    genExpr(rand, depth - 1, tpl));
            }
            case 8 -> new BinaryExprNode(genExpr(rand, depth - 1, tpl),
                BINOPS.get(rand.nextInt(BINOPS.size())),
                genExpr(rand, depth - 1, tpl), 0, 0);
            case 9 -> new UnaryExprNode("!", genExpr(rand, depth - 1, tpl), 0, 0);
            case 10 -> genCase(rand, depth, tpl);
            default -> genLeaf(rand);
        };
    }

    private static ExpressionNode genCase(Random rand, int depth, DataTemplate tpl) {
        if (tpl == null) return genLeaf(rand);
        var ct = tpl.ctors().get(rand.nextInt(tpl.ctors().size()));
        ExpressionNode disc;
        if (ct.argTypes().isEmpty()) {
            disc = new IdentifierNode(ct.name(), 0, 0);
        } else {
            ExpressionNode arg = genExpr(rand, depth - 1, tpl);
            disc = new ApplicationNode(new IdentifierNode(ct.name(), 0, 0), arg, 0, 0);
        }
        var branches = new ArrayList<CaseBranch>();
        for (CtorDef c : tpl.ctors()) {
            var bindings = new ArrayList<CaseNode.Binding>();
            for (int i = 0; i < c.argTypes().size(); i++) {
                bindings.add(new CaseNode.Binding(
                    IDENTS.get(rand.nextInt(IDENTS.size())), 0, 0));
            }
            branches.add(new CaseBranch(c.name(), bindings,
                genExpr(rand, depth - 1, tpl)));
        }
        return new CaseNode(disc, branches, 0, 0);
    }

    private static ExpressionNode genLeaf(Random rand) {
        return switch (rand.nextInt(4)) {
            case 0 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 1 -> new BoolNode(0, 0, rand.nextBoolean());
            case 2 -> new StringLiteralNode(SAMPLE_STRINGS.get(rand.nextInt(SAMPLE_STRINGS.size())), 0, 0);
            default -> new IdentifierNode(IDENTS.get(rand.nextInt(IDENTS.size())), 0, 0);
        };
    }

    // ── Program size filter: generate program with target node count ──
    // Keeps generating until it gets a well-typed program close to targetNodes.

    private static ProgramNode generateProgramOfSize(Random rand, int targetNodes) {
        for (int attempt = 0; attempt < 200; attempt++) {
            var prog = generateWellTyped(rand, targetNodes);
            String src = stringify(prog);
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) continue;
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(result.ast);
            if (!errors.isEmpty()) continue;
            int size = countASTNodes(result.ast);
            if (size >= targetNodes * 0.3 && size <= targetNodes * 3) return result.ast;
        }
        return null;
    }

    // ── Stringifier (from MutationAnalysis) ──────────────────────────

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
            String ann = (l.declaredType != null) ? ": " + l.declaredType : "";
            if (l.isRec && l.value instanceof LambdaNode lam) {
                String paramAnn = lam.declaredType != null ? ": " + lam.declaredType : "";
                return "def " + l.name + "(" + lam.param + paramAnn + ")" + ann + " = " + stringify(lam.body);
            }
            return "val " + l.name + ann + " = " + stringify(l.value);
        }
        if (node instanceof EnumDeclNode d) {
            var sb = new StringBuilder("enum ").append(d.name);
            if (!d.params.isEmpty()) {
                sb.append('[');
                for (int i = 0; i < d.params.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(d.params.get(i));
                }
                sb.append(']');
            }
            sb.append(" { ");
            for (int i = 0; i < d.constructors.size(); i++) {
                if (i > 0) sb.append("; ");
                var c = d.constructors.get(i);
                sb.append("case ").append(c.name());
                if (!c.argTypes().isEmpty()) {
                    sb.append('(');
                    for (int j = 0; j < c.argTypes().size(); j++) {
                        if (j > 0) sb.append(", ");
                        sb.append("x").append(j).append(": ").append(c.argTypes().get(j));
                    }
                    sb.append(')');
                }
            }
            sb.append(" }");
            return sb.toString();
        }
        if (node instanceof LambdaNode lam) {
            String ann = lam.declaredType != null ? ": " + lam.declaredType : "";
            return "(" + lam.param + ann + ") => " + stringify(lam.body);
        }
        if (node instanceof ApplicationNode app) {
            var fn = stringify(app.function);
            var a = stringify(app.argument);
            if (needsParen(app.function)) fn = "(" + fn + ")";
            if (needsParen(app.argument)) a = "(" + a + ")";
            return fn + "(" + a + ")";
        }
        if (node instanceof IfNode i) {
            var sb = new StringBuilder("if (").append(stringify(i.condition)).append(") ")
                .append(stringify(i.thenBranch));
            if (i.elseBranch != null) sb.append(" else ").append(stringify(i.elseBranch));
            return sb.toString();
        }
        if (node instanceof UnitLiteralNode) return "()";
        if (node instanceof AscriptionNode asc) return "(" + stringify(asc.expr) + ": " + asc.declaredType + ")";
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
            if (needsParen(un.operand) || un.operand instanceof ApplicationNode) op = "(" + op + ")";
            return un.operator + op;
        }
        if (node instanceof CaseNode c) {
            var sb = new StringBuilder(stringify(c.expr)).append(" match {");
            for (int i = 0; i < c.branches.size(); i++) {
                var br = c.branches.get(i);
                if (br.constructor() != null) {
                    sb.append(" case ").append(br.constructor());
                    if (!br.bindings().isEmpty()) {
                        sb.append('(');
                        for (int j = 0; j < br.bindings().size(); j++) {
                            if (j > 0) sb.append(", ");
                            sb.append(br.bindings().get(j).name());
                        }
                        sb.append(')');
                    }
                } else if (br.literalPattern() != null) {
                    sb.append(" case ").append(stringify(br.literalPattern()));
                } else {
                    sb.append(" case _");
                }
                sb.append(" => ");
                var body = stringify(br.body());
                if (containsCase(br.body())) body = "(" + body + ")";
                sb.append(body);
                if (i < c.branches.size() - 1) sb.append(";");
            }
            sb.append(" }");
            return sb.toString();
        }
        if (node instanceof BlockNode b) {
            var sb = new StringBuilder("{");
            for (int i = 0; i < b.statements.size(); i++) {
                sb.append(" ").append(stringify(b.statements.get(i)));
                if (i < b.statements.size() - 1) sb.append(";");
            }
            sb.append(" }");
            return sb.toString();
        }
        return "0";
    }

    private static boolean containsCase(ExpressionNode e) {
        if (e instanceof CaseNode) return true;
        if (e instanceof LambdaNode lam) return containsCase(lam.body);
        if (e instanceof IfNode i) return containsCase(i.condition) || containsCase(i.thenBranch) || (i.elseBranch != null && containsCase(i.elseBranch));
        if (e instanceof ApplicationNode app) return containsCase(app.function) || containsCase(app.argument);
        if (e instanceof BinaryExprNode bin) return containsCase(bin.left) || containsCase(bin.right);
        if (e instanceof UnaryExprNode un) return containsCase(un.operand);
        if (e instanceof AscriptionNode asc) return containsCase(asc.expr);
        if (e instanceof BlockNode b) { for (var s : b.statements) if (s instanceof ExpressionNode ex && containsCase(ex)) return true; return false; }
        return false;
    }

    private static boolean needsParen(ExpressionNode e) {
        return e instanceof IfNode || e instanceof LambdaNode || e instanceof CaseNode || e instanceof BlockNode || e instanceof AscriptionNode;
    }

    private static String escapeStr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }

    // ── Shared mutation operators (subset of MutationAnalysis) ───────

    interface MutationOperator { String name(); ProgramNode apply(ProgramNode program, Random rand); }

    static class ReplaceLiteralTypeOp implements MutationOperator {
        public String name() { return "ReplaceLiteralType"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) { var r = replaceInExpr(e, rand); if (r != e) { done = true; stmts.add(r); } else stmts.add(s); }
                else stmts.add(s);
            }
            return done ? new ProgramNode(stmts) : null;
        }
        private ExpressionNode replaceInExpr(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode) return rand.nextBoolean() ? new StringLiteralNode("x", 0, 0) : new BoolNode(0, 0, rand.nextBoolean());
            if (e instanceof BoolNode) return new LiteralNode("0", 0, 0);
            if (e instanceof StringLiteralNode) return new LiteralNode("0", 0, 0);
            if (e instanceof BinaryExprNode b) { var l = replaceInExpr(b.left, rand); var r = replaceInExpr(b.right, rand); if (l != b.left || r != b.right) return new BinaryExprNode(l, b.operator, r, 0, 0); }
            if (e instanceof IfNode i) { var c = replaceInExpr(i.condition, rand); if (c != i.condition) return new IfNode(c, i.thenBranch, i.elseBranch); }
            return e;
        }
    }

    static class SwapOperatorOp implements MutationOperator {
        public String name() { return "SwapOperator"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            for (var s : program.statements) stmts.add(swapInStmt(s, rand));
            return new ProgramNode(stmts);
        }
        private ASTNode swapInStmt(ASTNode n, Random rand) {
            if (n instanceof BinaryExprNode b) {
                String newOp = switch (b.operator) { case "+" -> "-"; case "-" -> "+"; case "*" -> "/"; case "/" -> "*"; case "&&" -> "||"; case "||" -> "&&"; case "==" -> "!="; default -> b.operator; };
                return new BinaryExprNode((ExpressionNode)swapInStmt(b.left, rand), newOp, (ExpressionNode)swapInStmt(b.right, rand), 0, 0);
            }
            if (n instanceof ExpressionNode e) return e;
            return n;
        }
    }

    static class SwapIfBranchesOp implements MutationOperator {
        public String name() { return "SwapIfBranches"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>();
            for (var s : program.statements) stmts.add(swapIf(s));
            return new ProgramNode(stmts);
        }
        private ASTNode swapIf(ASTNode n) {
            if (n instanceof IfNode i) return new IfNode(i.condition, i.elseBranch, i.thenBranch);
            if (n instanceof LetStatementNode l) return new LetStatementNode(l.name, (ExpressionNode)swapIf(l.value), l.isRec, l.declaredType, 0, 0);
            return n;
        }
    }

    static class RemoveCaseBranchOp implements MutationOperator {
        public String name() { return "RemoveCaseBranch"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof CaseNode c && c.branches.size() > 1) { int idx = rand.nextInt(c.branches.size()); var nb = new ArrayList<>(c.branches); nb.remove(idx); stmts.add(new CaseNode(c.expr, nb, 0, 0)); done = true; }
                else stmts.add(s);
            }
            return done ? new ProgramNode(stmts) : null;
        }
    }

    static class SelfReferenceOp implements MutationOperator {
        public String name() { return "SelfReference"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            stmts.add(new LetStatementNode("f", new IdentifierNode("f", 0, 0), true, null, 0, 0));
            return new ProgramNode(stmts);
        }
    }

    static class UndefinedVariableOp implements MutationOperator {
        public String name() { return "UndefinedVariable"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) { stmts.add(replaceId(e, rand)); done = true; } else stmts.add(s);
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode replaceId(ExpressionNode e, Random rand) {
            if (e instanceof IdentifierNode) return new IdentifierNode("__BAD__" + rand.nextInt(1000), 0, 0);
            if (e instanceof BinaryExprNode b) return new BinaryExprNode(replaceId(b.left, rand), b.operator, replaceId(b.right, rand), 0, 0);
            if (e instanceof IfNode i) return new IfNode(replaceId(i.condition, rand), replaceId(i.thenBranch, rand), replaceId(i.elseBranch, rand));
            if (e instanceof LambdaNode l) return new LambdaNode(l.param, replaceId(l.body, rand), 0, 0);
            if (e instanceof ApplicationNode a) return new ApplicationNode(replaceId(a.function, rand), replaceId(a.argument, rand), 0, 0);
            if (e instanceof UnaryExprNode u) return new UnaryExprNode(u.operator, replaceId(u.operand, rand), 0, 0);
            return e;
        }
    }

    static class DivideByZeroOp implements MutationOperator {
        public String name() { return "DivideByZero"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) {
                if (!done && s instanceof ExpressionNode e) { stmts.add(injectDivZero(e, rand)); done = true; } else stmts.add(s);
            }
            return new ProgramNode(stmts);
        }
        private ExpressionNode injectDivZero(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode && rand.nextDouble() < 0.3) return new BinaryExprNode(e, "/", new LiteralNode("0", 0, 0), 0, 0);
            if (e instanceof BinaryExprNode b) { if (b.operator.equals("/") && rand.nextBoolean()) return new BinaryExprNode(b.left, "/", new LiteralNode("0", 0, 0), 0, 0); return new BinaryExprNode(injectDivZero(b.left, rand), b.operator, injectDivZero(b.right, rand), 0, 0); }
            if (e instanceof IfNode i) return new IfNode(injectDivZero(i.condition, rand), injectDivZero(i.thenBranch, rand), injectDivZero(i.elseBranch, rand));
            return e;
        }
    }

    static class BoolNegateOp implements MutationOperator {
        public String name() { return "BoolNegate"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) { if (!done && s instanceof ExpressionNode e) { stmts.add(negateBoolExpr(e, rand)); done = true; } else stmts.add(s); }
            return new ProgramNode(stmts);
        }
        private ExpressionNode negateBoolExpr(ExpressionNode e, Random rand) {
            if (e instanceof BoolNode) return new UnaryExprNode("-", e, 0, 0);
            if (e instanceof BinaryExprNode b) { var l = negateBoolExpr(b.left, rand); var r = negateBoolExpr(b.right, rand); if (l != b.left || r != b.right) return new BinaryExprNode(l, b.operator, r, 0, 0); }
            return e;
        }
    }

    static class NotOnNumberOp implements MutationOperator {
        public String name() { return "NotOnNumber"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<ASTNode>(); boolean done = false;
            for (var s : program.statements) { if (!done && s instanceof ExpressionNode e) { stmts.add(wrapWithNot(e, rand)); done = true; } else stmts.add(s); }
            return new ProgramNode(stmts);
        }
        private ExpressionNode wrapWithNot(ExpressionNode e, Random rand) {
            if (e instanceof LiteralNode && rand.nextDouble() < 0.4) return new UnaryExprNode("!", e, 0, 0);
            if (e instanceof BinaryExprNode b) { var l = wrapWithNot(b.left, rand); var r = wrapWithNot(b.right, rand); if (l != b.left || r != b.right) return new BinaryExprNode(l, b.operator, r, 0, 0); }
            return e;
        }
    }

    static class UndeclaredConstructorOp implements MutationOperator {
        public String name() { return "UndeclaredConstructor"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            boolean hasData = program.statements.stream().anyMatch(s -> s instanceof EnumDeclNode);
            if (!hasData) return null;
            stmts.add(new IdentifierNode("NonExistentConstructor", 0, 0));
            return new ProgramNode(stmts);
        }
    }

    static class TypeAnnotationMismatchOp implements MutationOperator {
        public String name() { return "TypeAnnotationMismatch"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            stmts.add(new LetStatementNode("_mismatch", new LiteralNode("42", 0, 0), false, new TypeBool(), 0, 0));
            return new ProgramNode(stmts);
        }
    }

    static class RemoveLetBindingOp implements MutationOperator {
        public String name() { return "RemoveLetBinding"; }
        public ProgramNode apply(ProgramNode program, Random rand) {
            var stmts = new ArrayList<>(program.statements);
            for (int i = 0; i < stmts.size(); i++) { if (stmts.get(i) instanceof LetStatementNode) { stmts.remove(i); return new ProgramNode(stmts); } }
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  1. PBT-Property-vs-Mutation Matrix
    // ─────────────────────────────────────────────────────────────────

    private static void runMutationPropertyMatrix() {
        System.out.println("=== 1. PBT-Property-vs-Mutation Matrix ===\n");
        System.out.println("For each mutation operator, classifies detection by pipeline stage");
        System.out.println("and maps to which PBT property category would catch the mutation.\n");

        var ops = List.of(
            new ReplaceLiteralTypeOp(), new SwapOperatorOp(), new SwapIfBranchesOp(),
            new RemoveCaseBranchOp(), new SelfReferenceOp(), new UndefinedVariableOp(),
            new DivideByZeroOp(), new BoolNegateOp(), new NotOnNumberOp(),
            new UndeclaredConstructorOp(), new TypeAnnotationMismatchOp(), new RemoveLetBindingOp()
        );
        int TRIALS = 200;

        // Category labels
        record Cat(String stage, String pbtProperty) {}
        Cat PARSE = new Cat("Parse", "— (caught before PBT)");
        Cat TYPE  = new Cat("Type", "preservationClosed* / preservationAfterStep");
        Cat RTE   = new Cat("RTE", "progressClosed* / value*IsIrreducible");
        Cat OUTPUT = new Cat("Output", "bigStepMatchesSmallStep / swap*ChangesOutput");
        Cat ESC   = new Cat("Escaped", "— (undetected)");

        System.out.printf("%-26s %8s %8s %8s %8s %8s  %-45s%n",
            "Operator", "Parse", "Type", "RTE", "Output", "Esc", "PBT Property");
        System.out.println("-".repeat(115));

        // Column counts per operator
        for (var op : ops) {
            var rand = new Random(SHARED.nextInt());
            var counts = runMutationPropertyOp(op, rand, TRIALS);
            String pbtHint = switch (op.name()) {
                case "ReplaceLiteralType" -> "preservationClosed*";
                case "SwapOperator" -> "bigStepMatchesSmallStep / swapOperatorChangesOutput";
                case "SwapIfBranches" -> "swapIfBranchesChangesOutput";
                case "RemoveCaseBranch" -> "progressClosedCase / preservationClosedCase";
                case "SelfReference" -> "preservationClosed*";
                case "UndefinedVariable" -> "undefinedVariableCausesError";
                case "DivideByZero" -> "progressClosed* (RTE)";
                case "BoolNegate" -> "preservationClosed*";
                case "NotOnNumber" -> "preservationClosed*";
                case "UndeclaredConstructor" -> "preservationClosed*";
                case "TypeAnnotationMismatch" -> "preservationClosed*";
                case "RemoveLetBinding" -> "removeLetBindingCausesError";
                default -> "";
            };
            System.out.printf("%-26s %8d %8d %8d %8d %8d  %-45s%n",
                op.name(), counts[0], counts[1], counts[2], counts[3], counts[4], pbtHint);
        }
        System.out.println("-".repeat(115));
        System.out.println("Property categories that detect each detection stage:");
        System.out.println("  Parse   → caught before property tests run (tokenizer/parser error)");
        System.out.println("  Type    → preservationClosedExpr/Block/Case asserts well-typedness after step");
        System.out.println("  RTE     → progressClosedExpr/Block/Case asserts step() doesn't throw for non-values");
        System.out.println("  Output  → bigStepMatchesSmallStep or coverage-guided output-comparison properties");
        System.out.println("  Escaped → no existing property detects this mutation\n");
    }

    private static int[] runMutationPropertyOp(MutationOperator op, Random rand, int trials) {
        int[] counts = new int[5]; // parse, type, rte, output, escaped
        int maxRetries = trials * 20;
        int retries = 0;
        for (int t = 0; t < trials && retries < maxRetries; retries++) {
            var base = generateWellTyped(rand, 10);
            String src = stringify(base);
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) continue;
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(result.ast);
            if (!errors.isEmpty()) continue;
            Value originalValue;
            try { originalValue = new Evaluator().evalProgram(result.ast); } catch (Exception e) { continue; }
            if (originalValue == null) continue;
            var mutated = op.apply(base, rand);
            if (mutated == null) continue;
            t++;
            String mutatedSource = stringify(mutated);
            var mutatedTokens = new Tokenizer(mutatedSource).tokenize();
            var mutatedResult = new Parser(mutatedTokens).parse();
            if (!mutatedResult.errors.isEmpty()) { counts[0]++; continue; }
            var mutatedErrors = new ArrayList<ParserError>();
            new TypeChecker(mutatedErrors).check(mutatedResult.ast);
            if (!mutatedErrors.isEmpty()) { counts[1]++; continue; }
            try {
                Value mutatedValue = new Evaluator().evalProgram(mutatedResult.ast);
                if (mutatedValue == null || !valueEquals(originalValue, mutatedValue)) { counts[3]++; }
                else { counts[4]++; }
            } catch (Exception e) { counts[2]++; }
        }
        return counts;
    }

    private static boolean valueEquals(Value a, Value b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof NumberValue na && b instanceof NumberValue nb) return na.value == nb.value;
        if (a instanceof BoolValue ba && b instanceof BoolValue bb) return ba.value == bb.value;
        if (a instanceof StringValue sa && b instanceof StringValue sb) return sa.value.equals(sb.value);
        if (a instanceof UnitValue) return true;
        if (a instanceof TupleValue ta && b instanceof TupleValue tb) {
            if (ta.values.size() != tb.values.size()) return false;
            for (int i = 0; i < ta.values.size(); i++) if (!valueEquals(ta.values.get(i), tb.values.get(i))) return false;
            return true;
        }
        return a.toString().equals(b.toString());
    }

    // ─────────────────────────────────────────────────────────────────
    //  2. Scalability Benchmark (time + steps vs. program size)
    // ─────────────────────────────────────────────────────────────────

    /** A hand-crafted program with known step count. */
    record ProgCase(String name, String source) {}

    // Note: only closed expressions (no free let-bound variables) work with
    // the expression-level small-step reducer. let-bindings and blocks are
    // excluded because the reducer can't resolve their variables at expr level.
    private static final List<ProgCase> HAND_CRAFTED = List.of(
        new ProgCase("literal", "42"),
        new ProgCase("bool", "true"),
        new ProgCase("string", "\"hello\""),
        new ProgCase("unit", "()"),
        new ProgCase("lambda", "(x: Int) => x"),
        new ProgCase("add", "1 + 2"),
        new ProgCase("sub", "5 - 3"),
        new ProgCase("mul", "3 * 4"),
        new ProgCase("if_true", "if (true) 1 else 2"),
        new ProgCase("if_false", "if (false) 1 else 2"),
        new ProgCase("not_true", "!false"),
        new ProgCase("beta_simple", "((x: Int) => x)(5)"),
        new ProgCase("succ_num", "succ(5)"),
        new ProgCase("pred_num", "pred(10)"),
        new ProgCase("iszero_true", "iszero(0)"),
        new ProgCase("iszero_false", "iszero(1)"),
        new ProgCase("nested_arith", "(1 + 2) * (3 + 4)"),
        new ProgCase("nested_if", "if (1 < 2) if (3 > 1) 1 else 2 else 3"),
        new ProgCase("chained_beta", "((x: Int) => x + 1)(((y: Int) => y * 2)(5))"),
        new ProgCase("nested_beta_deep", "((x: Int) => x + 1)(((y: Int) => y * 2)(((z: Int) => z - 3)(10)))"),
        new ProgCase("complex_arith", "((1 + 2) * (3 - 4)) + ((5 * 6) - (7 + 8))"),
        new ProgCase("tuple_beta", "((x: Int) => (x, x + 1))(42)"),
        new ProgCase("complex_if", "if ((1 + 2) * 3 > 10) (4 - 1) else (2 + 3) * (5 - 1)"),
        new ProgCase("deep_arith_chain", "(((1 + 2) + (3 + 4)) + ((5 + 6) + (7 + 8))) + (((9 + 10) + (11 + 12)) + ((13 + 14) + (15 + 16)))"),
        // Block-based programs (reducer handles BlockNode)
        new ProgCase("block_simple", "{ 42 }"),
        new ProgCase("block_arith", "{ 1 + 2 }"),
        new ProgCase("block_nested", "{ { 3 + 4 } }"),
        new ProgCase("block_beta", "{ ((x: Int) => x + 1)(5) }"),
        new ProgCase("block_if", "{ if (true) 1 else 2 }")
    );

    private static void runScalabilityBenchmark() { runScalabilityBenchmark(false, 0); }

    private static void runScalabilityBenchmark(boolean csv, int runId) {
        if (!csv) {
            System.out.println("=== 2. Scalability Benchmark ===\n");
            System.out.println("Measures time (ns) and step count for hand-crafted programs of increasing size.\n");
        }
        if (!csv) System.out.println("size,parseNs,tcNs,evalNs,reduceNs,steps,name");

        // Warmup with first few
        for (int w = 0; w < Math.min(3, HAND_CRAFTED.size()); w++) {
            measureScalability(HAND_CRAFTED.get(w));
        }

        for (var c : HAND_CRAFTED) {
            var m = measureScalability(c);
            if (m != null) {
                if (csv) {
                    System.out.println(m.size + "," + m.parseNs + "," + m.tcNs + ","
                        + m.evalNs + "," + m.reduceNs + "," + m.steps + "," + c.name + "," + runId);
                } else {
                    System.out.println(m.size + "," + m.parseNs + "," + m.tcNs + ","
                        + m.evalNs + "," + m.reduceNs + "," + m.steps + "," + c.name);
                }
            }
        }
        if (!csv) System.out.println();
    }

    private static class Meas {
        int size, steps; long parseNs, tcNs, evalNs, reduceNs;
        Meas(int s, long p, long t, long e, long r, int st) { size = s; parseNs = p; tcNs = t; evalNs = e; reduceNs = r; steps = st; }
    }

    private static Meas measureScalability(ProgCase c) {
        try {
            String src = c.source;
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) return null;
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(result.ast);
            if (!errors.isEmpty()) return null;
            int size = countASTNodes(result.ast);
            long t0, t1;

            t0 = System.nanoTime();
            var t2 = new Tokenizer(src).tokenize();
            var r2 = new Parser(t2).parse();
            t1 = System.nanoTime();
            long parseNs = t1 - t0;

            var e2 = new ArrayList<ParserError>();
            t0 = System.nanoTime();
            new TypeChecker(e2).check(r2.ast);
            t1 = System.nanoTime();
            long tcNs = t1 - t0;

            t0 = System.nanoTime();
            new Evaluator().evalProgram(r2.ast);
            t1 = System.nanoTime();
            long evalNs = t1 - t0;

            ExpressionNode lastExpr = findLastExpr(r2.ast);
            if (lastExpr == null) return null;
            t0 = System.nanoTime();
            int steps = countReductionSteps(lastExpr);
            t1 = System.nanoTime();
            long reduceNs = t1 - t0;

            return new Meas(size, parseNs, tcNs, evalNs, reduceNs, steps < 0 ? 0 : steps);
        } catch (Exception e) {
            return null;
        }
    }

    private static ExpressionNode findLastExpr(ProgramNode prog) {
        for (int i = prog.statements.size() - 1; i >= 0; i--) {
            if (prog.statements.get(i) instanceof ExpressionNode e) return e;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  3. Type Error Recovery Benchmark (systematic error injection)
    // ─────────────────────────────────────────────────────────────────

    private static void runTypeErrorRecovery() {
        System.out.println("=== 3. Type Error Recovery Benchmark ===\n");
        System.out.println("Injects type errors into well-typed programs and measures detection rate by stage.\n");

        var ops = List.of(
            new ReplaceLiteralTypeOp(), new SwapOperatorOp(), new SwapIfBranchesOp(),
            new RemoveCaseBranchOp(), new SelfReferenceOp(), new UndefinedVariableOp(),
            new DivideByZeroOp(), new BoolNegateOp(), new NotOnNumberOp(),
            new UndeclaredConstructorOp(), new TypeAnnotationMismatchOp(), new RemoveLetBindingOp()
        );
        int TRIALS = 200;

        System.out.printf("%-26s %8s %8s %8s %8s %8s%n",
            "Operator", "Total", "Parse", "Type", "RTE", "Escaped");
        System.out.println("-".repeat(66));

        double totalCaughtRate = 0;
        int opCount = 0;
        for (var op : ops) {
            var rand = new Random(SHARED.nextInt());
            int t = 0, parse = 0, type = 0, rte = 0, escaped = 0;
            int maxRetries = TRIALS * 20;
            int retries = 0;
            for (int trial = 0; trial < TRIALS && retries < maxRetries; retries++) {
                var base = generateWellTyped(rand, 10);
                String src = stringify(base);
                var tokens = new Tokenizer(src).tokenize();
                var result = new Parser(tokens).parse();
                if (!result.errors.isEmpty()) continue;
                var errors = new ArrayList<ParserError>();
                new TypeChecker(errors).check(result.ast);
                if (!errors.isEmpty()) continue;
                Value originalValue;
                try { originalValue = new Evaluator().evalProgram(result.ast); } catch (Exception e) { continue; }
                if (originalValue == null) continue;
                var mutated = op.apply(base, rand);
                if (mutated == null) continue;
                trial++;
                String mutatedSource = stringify(mutated);
                var mt = new Tokenizer(mutatedSource).tokenize();
                var mr = new Parser(mt).parse();
                if (!mr.errors.isEmpty()) { parse++; t++; continue; }
                var me = new ArrayList<ParserError>();
                new TypeChecker(me).check(mr.ast);
                if (!me.isEmpty()) { type++; t++; continue; }
                try {
                    Value mutatedValue = new Evaluator().evalProgram(mr.ast);
                    if (mutatedValue == null || !valueEquals(originalValue, mutatedValue)) { t++; } // output diff (still counts as caught for error recovery)
                    else { escaped++; t++; }
                } catch (Exception e) { rte++; t++; }
            }
            double caughtRate = t == 0 ? 0 : (t - escaped) * 100.0 / t;
            totalCaughtRate += caughtRate;
            opCount++;
            System.out.printf("%-26s %8d %8d %8d %8d %8d  %5.1f%%%n",
                op.name(), t, parse, type, rte, escaped, caughtRate);
        }
        System.out.println("-".repeat(66));
        System.out.printf("%-26s %8s %8s %8s %8s %8s  %5.1f%%%n",
            "AVERAGE", "", "", "", "", "", totalCaughtRate / opCount);
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────
    //  4. AST Node Coverage
    // ─────────────────────────────────────────────────────────────────

    private static void runASTCoverage() {
        System.out.println("=== 4. AST Node Coverage ===\n");
        System.out.println("For each AST node type, checks which components handle it.\n");

        var nodeTypes = List.of(
            "LiteralNode", "BoolNode", "StringLiteralNode", "UnitLiteralNode",
            "IdentifierNode", "LambdaNode", "ApplicationNode", "IfNode",
            "BinaryExprNode", "UnaryExprNode", "TupleNode", "AscriptionNode",
            "FixpointNode", "CaseNode", "BlockNode", "LetStatementNode",
            "MutualLetNode", "EnumDeclNode", "TypeAliasNode", "ProgramNode"
        );

        // Analyze source files for instanceof checks (approximation)
        Map<String, Set<String>> coverage = analyzeSourceCoverage(nodeTypes);

        System.out.printf("%-22s %8s %8s %8s %8s%n",
            "Node Type", "Eval", "Reducer", "Checker", "Parser");
        System.out.println("-".repeat(58));

        for (var nt : nodeTypes) {
            var comps = coverage.getOrDefault(nt, Set.of());
            System.out.printf("%-22s %8s %8s %8s %8s%n",
                nt,
                comps.contains("Evaluator") ? "Y" : "N",
                comps.contains("Reducer") ? "Y" : "N",
                comps.contains("Checker") ? "Y" : "N",
                comps.contains("Parser") ? "Y" : "N");
        }
        System.out.println();
    }

    private static Map<String, Set<String>> analyzeSourceCoverage(List<String> nodeTypes) {
        var result = new HashMap<String, Set<String>>();
        for (var nt : nodeTypes) result.put(nt, new HashSet<>());

        // For each component file, check if it references each node type in instanceof or switch
        var files = Map.of(
            "src/main/java/scala/parser/core/interpreter/Evaluator.java", "Evaluator",
            "src/main/java/scala/parser/core/reduction/SmallStepReducer.java", "Reducer",
            "src/main/java/scala/parser/core/types/TypeChecker.java", "Checker",
            "src/main/java/scala/parser/core/parser/Parser.java", "Parser"
        );

        try {
            for (var entry : files.entrySet()) {
                var content = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(entry.getKey())));
                for (var nt : nodeTypes) {
                    if (content.contains("instanceof " + nt) || content.contains("case " + nt) || content.contains(nt + ".class") || content.contains("new " + nt + "(")) {
                        result.get(nt).add(entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("  Warning: source analysis failed: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    //  5. Reduction Step Distribution (Histogram)
    // ─────────────────────────────────────────────────────────────────

    private static void runStepDistribution() { runStepDistribution(false, 0); }

    private static void runStepDistribution(boolean csv, int runId) {
        if (!csv) {
            System.out.println("=== 5. Reduction Step Distribution ===\n");
            System.out.println("Counts reduction steps for 200 random closed well-typed expressions.\n");
        }

        var stepCounts = new int[21]; // 0..19, 20+
        int total = 0, attempts = 0;
        while (total < 200 && attempts < 5000) {
            attempts++;
            var expr = genClosedExpr(SHARED, 4, new ArrayList<>());
            String src = stringify(new ProgramNode(List.of(expr)));
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) continue;
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(result.ast);
            if (!errors.isEmpty()) continue;
            try {
                silencePrint(); new Evaluator().evalProgram(result.ast); restorePrint();
            } catch (Exception e) { restorePrint(); continue; }

            int steps = countReductionSteps(expr);
            if (steps < 0) continue;
            total++;
            if (steps >= 20) stepCounts[20]++;
            else stepCounts[steps]++;
        }

        if (csv) {
            for (int s = 0; s <= 20; s++) {
                if (stepCounts[s] > 0) {
                    String label = s == 20 ? "20+" : String.valueOf(s);
                    System.out.println(runId + "," + label + "," + stepCounts[s]);
                }
            }
        } else {
            System.out.println("steps,count");
            for (int s = 0; s <= 20; s++) {
                if (stepCounts[s] > 0) {
                    String label = s == 20 ? "20+" : String.valueOf(s);
                    System.out.println(label + "," + stepCounts[s]);
                }
            }
        }
        int totalSteps = 0;
        for (int s = 0; s <= 20; s++) totalSteps += stepCounts[s] * s;
        double avg = total > 0 ? (double) totalSteps / total : 0;
        if (!csv) {
            System.out.println("\nTotal programs: " + total);
            System.out.println("Average steps: " + String.format("%.2f", avg));
            System.out.println("Programs with 0 steps (already values): " + stepCounts[0]);
            System.out.println();
        }
    }

    private static ExpressionNode genClosedExpr(Random rand, int depth, List<String> bound) {
        if (depth <= 0 || rand.nextDouble() < 0.15) {
            if (bound.isEmpty() || rand.nextBoolean()) {
                return switch (rand.nextInt(4)) {
                    case 0 -> new LiteralNode(String.valueOf(rand.nextInt(100)), 0, 0);
                    case 1 -> new BoolNode(0, 0, rand.nextBoolean());
                    case 2 -> new StringLiteralNode("x", 0, 0);
                    default -> new UnitLiteralNode(0, 0);
                };
            }
            return new IdentifierNode(bound.get(rand.nextInt(bound.size())), 0, 0);
        }
        int choice = rand.nextInt(8);
        if (choice == 0) {
            String p = "x" + rand.nextInt(1000);
            var nb = new ArrayList<>(bound); nb.add(p);
            return new LambdaNode(p, genClosedExpr(rand, depth - 1, nb), 0, 0);
        }
        if (choice == 1)
            return new ApplicationNode(genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        if (choice == 2)
            return new IfNode(genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound));
        if (choice == 3) {
            String op = List.of("+", "-", "*", "/", "==", "!=", "<", ">").get(rand.nextInt(8));
            return new BinaryExprNode(genClosedExpr(rand, depth - 1, bound), op,
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        if (choice == 4)
            return new UnaryExprNode(rand.nextBoolean() ? "!" : "-",
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        if (rand.nextDouble() < 0.3 && !bound.isEmpty()) {
            String id = bound.get(rand.nextInt(bound.size()));
            return new ApplicationNode(new IdentifierNode(id, 0, 0),
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        // Builtin applications that tend to need steps
        var builtinNames = List.of("succ", "pred", "iszero");
        String bn = builtinNames.get(rand.nextInt(builtinNames.size()));
        var arg = genClosedExpr(rand, depth - 1, bound);
        return new ApplicationNode(new IdentifierNode(bn, 0, 0), arg, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────
    //  6. Parser Error Recovery Quality (Cascade)
    // ─────────────────────────────────────────────────────────────────

    private static void runParserErrorRecovery() {
        System.out.println("=== 6. Parser Error Recovery Quality ===\n");
        System.out.println("Injects single-character mutations into well-typed source code\n");
        System.out.println("and measures cascade: how many (bogus) follow-up errors result.\n");

        var sources = List.of(
            "val x: Int = 42; x + 1",
            "if (true) 1 else 0",
            "((x: Int) => x + 1)(5)",
            "val f = (x: Int) => x * 2; f(10)",
            "val a: Int = 1 + 2; val b: Int = a * 3; b",
            "{ val x: Int = 5; x + 1 }",
            "succ(5)",
            "if ((1 + 2) * 3 > 10) (4 - 1) else (2 + 3) * (5 - 1)",
            "((x: Int) => x + 1)(((y: Int) => y * 2)(5))",
            "val x: Int = { val y: Int = 1 + 2; y * 3 }; x - 1"
        );

        record Inj(String label, java.util.function.UnaryOperator<String> fn) {}
        var injectors = List.of(
            new Inj("delete-char", s -> { int p = s.isEmpty() ? 0 : SHARED.nextInt(s.length()); return s.substring(0, p) + s.substring(Math.min(p + 1, s.length())); }),
            new Inj("insert-!", s -> { int p = s.isEmpty() ? 0 : SHARED.nextInt(s.length() + 1); return s.substring(0, p) + "!" + s.substring(p); }),
            new Inj("replace->#", s -> { if (s.isEmpty()) return s; int p = SHARED.nextInt(s.length()); return s.substring(0, p) + "#" + s.substring(p + 1); }),
            new Inj("delete-digit", s -> { for (int i = 0; i < 20; i++) { int p = SHARED.nextInt(s.length()); if (Character.isDigit(s.charAt(p))) return s.substring(0, p) + s.substring(p + 1); } return s; })
        );

        int TRIALS = 50;
        System.out.printf("%-16s %8s %8s %8s %8s %10s%n",
            "Injector", "Total", "Cascade", "MaxCasc", "ParseErr", "CascadeRate");
        System.out.println("-".repeat(66));

        for (var inj : injectors) {
            int total = 0, cascadeSum = 0, maxCascade = 0, parseErrCount = 0;
            for (int t = 0; t < TRIALS; t++) {
                String src = sources.get(SHARED.nextInt(sources.size()));
                // Verify original is parseable
                var origTokens = new Tokenizer(src).tokenize();
                var origResult = new Parser(origTokens).parse();
                if (!origResult.errors.isEmpty()) continue;

                String mutated = inj.fn.apply(src);
                if (mutated.equals(src)) continue;
                total++;
                var mutTokens = new Tokenizer(mutated).tokenize();
                var mutResult = new Parser(mutTokens).parse();
                int errCount = mutResult.errors.size();
                if (errCount > 0) parseErrCount++;
                if (errCount > 0) {
                    cascadeSum += (errCount - 1);
                    if ((errCount - 1) > maxCascade) maxCascade = errCount - 1;
                }
            }
            double rate = total > 0 ? cascadeSum * 100.0 / total : 0;
            System.out.printf("%-16s %8d %8d %8d %8d %9.1f%%%n",
                inj.label, total, cascadeSum, maxCascade, parseErrCount, rate);
        }
        System.out.println("\nCascade = total parser errors - 1 (bogus follow-up errors from a single injection)");
        System.out.println("Lower cascade rate = better error recovery\n");
    }

    // ─────────────────────────────────────────────────────────────────
    //  7. Empirical PBT-vs-Mutation Matrix
    // ─────────────────────────────────────────────────────────────────

    /** Runs actual PBT property checks against each mutated closed expression. */
    private static void runEmpiricalMatrix() {
        runEmpiricalMatrix(1, false);
    }

    private static void runEmpiricalMatrix(int runs, boolean csv) {
        if (!csv) {
            System.out.println("=== 7. Empirical PBT-vs-Mutation Matrix ===\n");
            System.out.println("For each mutation operator, runs actual PBT property checks\n");
            System.out.println("against " + (100 * runs) + " mutated closed well-typed expressions.\n");
        }

        var ops = List.of(
            new ReplaceLiteralTypeOp(), new SwapOperatorOp(), new SwapIfBranchesOp(),
            new SelfReferenceOp(), new BoolNegateOp(), new NotOnNumberOp(),
            new DivideByZeroOp()
        );

        if (!csv) {
            System.out.printf("%-22s %8s %8s %8s %8s %8s %8s %8s%n",
                "Operator", "Progress", "Preserv", "Determ", "BigStep", "ValueIrred", "VsOrig", "AnyPBT");
            System.out.println("-".repeat(88));
        }

        // Expression-level mutation adapters
        record Mut(String name, java.util.function.BiFunction<ExpressionNode, Random, ExpressionNode> apply) {}
        var exprOps = List.of(
            new Mut("ReplaceLiteralType", (e, r) -> { var p = new ReplaceLiteralTypeOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; }),
            new Mut("SwapOperator", (e, r) -> { var p = new SwapOperatorOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; }),
            new Mut("SwapIfBranches", (e, r) -> { var p = new SwapIfBranchesOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; }),
            new Mut("SelfReference", (e, r) -> e), // self-ref adds a let, not applicable to single expr
            new Mut("BoolNegate", (e, r) -> { var p = new BoolNegateOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; }),
            new Mut("NotOnNumber", (e, r) -> { var p = new NotOnNumberOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; }),
            new Mut("DivideByZero", (e, r) -> { var p = new DivideByZeroOp().apply(new ProgramNode(List.of(e)), r); return p != null && p.statements.get(0) instanceof ExpressionNode x ? x : e; })
        );

        // Generate expressions that are more likely to trigger each mutation
        // Pre-built sub-expressions with known structure
        var seedExprs = new HashMap<String, List<ExpressionNode>>();
        seedExprs.put("ReplaceLiteralType", List.of(
            new LiteralNode("42", 0, 0), new BoolNode(0, 0, true), new StringLiteralNode("hello", 0, 0),
            new BinaryExprNode(new LiteralNode("1", 0, 0), "+", new LiteralNode("2", 0, 0), 0, 0),
            new IfNode(new BoolNode(0, 0, true), new LiteralNode("1", 0, 0), new LiteralNode("0", 0, 0))
        ));
        seedExprs.put("SwapOperator", List.of(
            new BinaryExprNode(new LiteralNode("1", 0, 0), "+", new LiteralNode("2", 0, 0), 0, 0),
            new BinaryExprNode(new LiteralNode("5", 0, 0), "-", new LiteralNode("3", 0, 0), 0, 0),
            new BinaryExprNode(new LiteralNode("3", 0, 0), "*", new LiteralNode("4", 0, 0), 0, 0),
            new BinaryExprNode(new LiteralNode("10", 0, 0), "/", new LiteralNode("2", 0, 0), 0, 0)
        ));
        seedExprs.put("SwapIfBranches", List.of(
            new IfNode(new BoolNode(0, 0, true), new LiteralNode("1", 0, 0), new LiteralNode("0", 0, 0)),
            new IfNode(new BoolNode(0, 0, false), new LiteralNode("42", 0, 0), new LiteralNode("7", 0, 0)),
            new IfNode(new BinaryExprNode(new LiteralNode("1", 0, 0), "<", new LiteralNode("2", 0, 0), 0, 0),
                new LiteralNode("1", 0, 0), new LiteralNode("0", 0, 0))
        ));
        seedExprs.put("SelfReference", List.of(
            new LiteralNode("0", 0, 0) // dummy — self-ref not applicable to single expr
        ));
        seedExprs.put("BoolNegate", List.of(
            new BoolNode(0, 0, true), new BoolNode(0, 0, false),
            new BinaryExprNode(new BoolNode(0, 0, true), "&&", new BoolNode(0, 0, false), 0, 0),
            new BinaryExprNode(new BoolNode(0, 0, true), "||", new BoolNode(0, 0, true), 0, 0)
        ));
        seedExprs.put("NotOnNumber", List.of(
            new LiteralNode("42", 0, 0), new LiteralNode("0", 0, 0),
            new BinaryExprNode(new LiteralNode("1", 0, 0), "+", new LiteralNode("2", 0, 0), 0, 0)
        ));
        seedExprs.put("DivideByZero", List.of(
            new BinaryExprNode(new LiteralNode("10", 0, 0), "/", new LiteralNode("2", 0, 0), 0, 0),
            new LiteralNode("5", 0, 0) // injectDivZero may wrap this as 5/0
        ));

        int TRIALS = 100 * runs;
        for (var m : exprOps) {
            var rand = new Random(SHARED.nextInt());
            int progressFail = 0, preservFail = 0, determinFail = 0, bigStepFail = 0, valueIrredFail = 0, outputFail = 0, anyFail = 0;
            int total = 0, retries = 0;
            var seeds = seedExprs.getOrDefault(m.name, List.of());
            while (total < TRIALS && retries < TRIALS * 10) {
                retries++;
                ExpressionNode expr;
                if (!seeds.isEmpty() && rand.nextDouble() < 0.6) {
                    expr = seeds.get(rand.nextInt(seeds.size()));
                } else {
                    expr = genClosedExpr(rand, 4, new ArrayList<>());
                }
                String src0 = stringify(new ProgramNode(List.of(expr)));
                var tok0 = new Tokenizer(src0).tokenize();
                var res0 = new Parser(tok0).parse();
                if (!res0.errors.isEmpty()) continue;
                var err0 = new ArrayList<ParserError>();
                new TypeChecker(err0).check(res0.ast);
                if (!err0.isEmpty()) continue;
                Value originalBigVal;
                try { silencePrint(); originalBigVal = new Evaluator().evalProgram(res0.ast); restorePrint(); } catch (Exception e) { restorePrint(); continue; }
                if (originalBigVal == null) continue;

                var mutated = m.apply.apply(expr, rand);
                if (mutated == expr && !m.name.equals("SelfReference")) continue;
                total++;
                String src = stringify(new ProgramNode(List.of(mutated)));
                var tok = new Tokenizer(src).tokenize();
                var res = new Parser(tok).parse();
                boolean parseErr = !res.errors.isEmpty();

                var tcErrs = new ArrayList<ParserError>();
                new TypeChecker(tcErrs).check(res.ast);
                boolean typeErr = !tcErrs.isEmpty();

                Value bigVal = null; boolean rte = false;
                if (!parseErr && !typeErr) {
                    try { silencePrint(); bigVal = new Evaluator().evalProgram(res.ast); restorePrint(); } catch (Exception e) { restorePrint(); rte = true; }
                }

                boolean caught = false;

                // Progress: if not a value, step must not throw
                if (parseErr || typeErr || rte) { progressFail++; caught = true; }
                else if (!SmallStepReducer.isValue(mutated)) {
                    try { SmallStepReducer.step(mutated); } catch (Exception e) { progressFail++; caught = true; }
                }

                // Preservation: after step, result must be well-typed
                if (!parseErr && !typeErr && !rte) {
                    try {
                        var stepped = SmallStepReducer.step(mutated);
                        String stepSrc = stringify(new ProgramNode(List.of(stepped)));
                        var stepTok = new Tokenizer(stepSrc).tokenize();
                        var stepRes = new Parser(stepTok).parse();
                        if (!stepRes.errors.isEmpty()) { preservFail++; caught = true; }
                        else {
                            var stepErrs = new ArrayList<ParserError>();
                            new TypeChecker(stepErrs).check(stepRes.ast);
                            if (!stepErrs.isEmpty()) { preservFail++; caught = true; }
                        }
                    } catch (Exception e) { /* stuck — not a preservation failure */ }
                } else { preservFail++; caught = true; }

                // Determinism: step twice, compare
                if (!parseErr && !typeErr && !rte) {
                    try {
                        var s1 = SmallStepReducer.step(mutated);
                        var s2 = SmallStepReducer.step(mutated);
                        if (!exprEquals(s1, s2)) { determinFail++; caught = true; }
                    } catch (Exception e) { /* stuck */ }
                } else { determinFail++; caught = true; }

                // BigStep matches SmallStep
                if (!parseErr && !typeErr && !rte) {
                    boolean match = false;
                    try {
                        var normal = SmallStepReducer.reduce(mutated);
                        if (bigVal != null && SmallStepReducer.isValue(normal)) {
                            String nStr = valueNodeToString(normal);
                            String bStr = valueToString(bigVal);
                            match = nStr != null && bStr != null && nStr.equals(bStr);
                        }
                    } catch (Exception e) { match = false; }
                    if (!match) { bigStepFail++; caught = true; }
                } else { bigStepFail++; caught = true; }

                // ValueIrreducible: if value, step must throw
                if (SmallStepReducer.isValue(mutated)) {
                    try { SmallStepReducer.step(mutated); valueIrredFail++; caught = true; }
                    catch (Exception e) { /* expected — value is irreducible */ }
                }

                // Output comparison: original vs mutated
                boolean outputDiff = false;
                if (!parseErr && !typeErr && !rte && bigVal != null) {
                    if (!valueEquals(originalBigVal, bigVal)) outputDiff = true;
                }
                if (outputDiff) { outputFail++; caught = true; }

                if (caught) anyFail++;
            }
            if (csv) {
                System.out.println("empirical," + m.name + "," + progressFail + "," + preservFail + ","
                    + determinFail + "," + bigStepFail + "," + valueIrredFail + "," + outputFail + "," + anyFail);
            } else {
                System.out.printf("%-22s %8d %8d %8d %8d %8d %8d %8d%n",
                    m.name, progressFail, preservFail, determinFail, bigStepFail, valueIrredFail, outputFail, anyFail);
            }
        }
        if (!csv) {
            System.out.println("\nEach column shows how many times out of " + TRIALS + " that property caught the mutation.");
            System.out.println("VsOrig = original vs. mutated output comparison (valueEquals)\n");
        }
    }

    private static String valueNodeToString(ExpressionNode node) {
        if (node instanceof LiteralNode l) return l.value;
        if (node instanceof BoolNode b) return String.valueOf(b.value);
        if (node instanceof StringLiteralNode s) return "\"" + s.value + "\"";
        if (node instanceof UnitLiteralNode) return "()";
        if (node instanceof IdentifierNode id) return id.name;
        if (node instanceof TupleNode t) {
            return t.elements.stream().map(BenchmarkSuite::valueNodeToString)
                .collect(Collectors.joining(", ", "(", ")"));
        }
        return null;
    }

    private static String valueToString(Value v) {
        if (v instanceof NumberValue n) return String.valueOf(n.value);
        if (v instanceof BoolValue b) return String.valueOf(b.value);
        if (v instanceof StringValue s) return "\"" + s.value + "\"";
        if (v instanceof UnitValue) return "()";
        if (v instanceof TupleValue t) {
            return t.values.stream().map(BenchmarkSuite::valueToString)
                .collect(Collectors.joining(", ", "(", ")"));
        }
        return null;
    }

    private static boolean exprEquals(ExpressionNode a, ExpressionNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof LiteralNode la && b instanceof LiteralNode lb) return la.value.equals(lb.value);
        if (a instanceof BoolNode ba && b instanceof BoolNode bb) return ba.value == bb.value;
        if (a instanceof StringLiteralNode sa && b instanceof StringLiteralNode sb) return sa.value.equals(sb.value);
        if (a instanceof UnitLiteralNode) return true;
        if (a instanceof IdentifierNode ia && b instanceof IdentifierNode ib) return ia.name.equals(ib.name);
        if (a instanceof LambdaNode la && b instanceof LambdaNode lb) return la.param.equals(lb.param) && exprEquals(la.body, lb.body);
        if (a instanceof ApplicationNode aa && b instanceof ApplicationNode ab) return exprEquals(aa.function, ab.function) && exprEquals(aa.argument, ab.argument);
        if (a instanceof IfNode ia && b instanceof IfNode ib) return exprEquals(ia.condition, ib.condition) && exprEquals(ia.thenBranch, ib.thenBranch) && exprEquals(ia.elseBranch, ib.elseBranch);
        if (a instanceof BinaryExprNode ba && b instanceof BinaryExprNode bb) return ba.operator.equals(bb.operator) && exprEquals(ba.left, bb.left) && exprEquals(ba.right, bb.right);
        if (a instanceof UnaryExprNode ua && b instanceof UnaryExprNode ub) return ua.operator.equals(ub.operator) && exprEquals(ua.operand, ub.operand);
        if (a instanceof TupleNode ta && b instanceof TupleNode tb) { if (ta.elements.size() != tb.elements.size()) return false; for (int i = 0; i < ta.elements.size(); i++) if (!exprEquals(ta.elements.get(i), tb.elements.get(i))) return false; return true; }
        if (a instanceof AscriptionNode aa && b instanceof AscriptionNode ab) return exprEquals(aa.expr, ab.expr) && aa.declaredType.toString().equals(ab.declaredType.toString());
        if (a instanceof FixpointNode fa && b instanceof FixpointNode fb) return exprEquals(fa.body, fb.body);
        if (a instanceof CaseNode ca && b instanceof CaseNode cb) {
            if (!exprEquals(ca.expr, cb.expr)) return false;
            if (ca.branches.size() != cb.branches.size()) return false;
            for (int i = 0; i < ca.branches.size(); i++) {
                var ba = ca.branches.get(i); var bb = cb.branches.get(i);
                if (!Objects.equals(ba.constructor(), bb.constructor())) return false;
                if (!Objects.equals(ba.literalPattern(), bb.literalPattern())) return false;
                if (!exprEquals(ba.body(), bb.body())) return false;
            }
            return true;
        }
        if (a instanceof BlockNode ba && b instanceof BlockNode bb) {
            if (ba.statements.size() != bb.statements.size()) return false;
            for (int i = 0; i < ba.statements.size(); i++) {
                var sa = ba.statements.get(i); var sb = bb.statements.get(i);
                if (sa instanceof ExpressionNode ea && sb instanceof ExpressionNode eb) { if (!exprEquals(ea, eb)) return false; }
                else if (!sa.toString().equals(sb.toString())) return false;
            }
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────
    //  8. Laufzeit-Breakdown (Box-Plot Data)
    // ─────────────────────────────────────────────────────────────────

    private static void runRuntimeBreakdown() { runRuntimeBreakdown(false, 0); }

    private static void runRuntimeBreakdown(boolean csv, int runId) {
        if (!csv) {
            System.out.println("=== 8. Laufzeit-Breakdown ===\n");
            System.out.println("Measures pipeline stage times (ns) for 100 random well-typed programs.\n");
        }
        if (!csv) System.out.println("size,parseNs,tcNs,evalNs,reduceNs");

        int total = 0, attempts = 0;
        while (total < 100 && attempts < 2000) {
            attempts++;
            var prog = generateWellTyped(SHARED, 20);
            String src = stringify(prog);
            int size = countASTNodes(prog);
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) continue;
            var errors = new ArrayList<ParserError>();
            var tc = new TypeChecker(errors);
            tc.check(result.ast);
            if (!errors.isEmpty()) continue;
            Value val;
            try { val = new Evaluator().evalProgram(result.ast); } catch (Exception e) { continue; }
            if (val == null) continue;
            ExpressionNode lastExpr = findLastExpr(result.ast);
            if (lastExpr == null) continue;
            int steps = countReductionSteps(lastExpr);
            if (steps < 0) continue;

            long t0, t1;
            t0 = System.nanoTime();
            var t2 = new Tokenizer(src).tokenize();
            var r2 = new Parser(t2).parse();
            t1 = System.nanoTime();
            long parseNs = t1 - t0;
            var e2 = new ArrayList<ParserError>();
            t0 = System.nanoTime();
            new TypeChecker(e2).check(r2.ast);
            t1 = System.nanoTime();
            long tcNs = t1 - t0;
            t0 = System.nanoTime();
            try { new Evaluator().evalProgram(r2.ast); } catch (Exception ex) {}
            t1 = System.nanoTime();
            long evalNs = t1 - t0;
            t0 = System.nanoTime();
            countReductionSteps(findLastExpr(r2.ast));
            t1 = System.nanoTime();
            long reduceNs = t1 - t0;

            if (csv) {
                System.out.println(runId + "," + size + "," + parseNs + "," + tcNs + "," + evalNs + "," + reduceNs);
            } else {
                System.out.println(size + "," + parseNs + "," + tcNs + "," + evalNs + "," + reduceNs);
            }
            total++;
        }
        if (!csv) System.out.println("\nTotal: " + total + " programs in " + attempts + " attempts\n");
    }

    // ─────────────────────────────────────────────────────────────────
    //  Main
    // ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        var argList = new ArrayList<>(List.of(args));
        boolean csv = argList.remove("--csv");
        int runs = 1;
        int runIdx = argList.indexOf("--runs");
        if (runIdx >= 0 && runIdx + 1 < argList.size()) {
            runs = Integer.parseInt(argList.get(runIdx + 1));
            argList.remove(runIdx + 1);
            argList.remove(runIdx);
        }
        var selected = argList.isEmpty()
            ? Set.of("mutation", "property", "scalability", "typeerror", "coverage", "steps", "parser", "empirical", "breakdown")
            : Set.copyOf(argList);

        if (selected.contains("mutation") || selected.contains("property"))
            runMutationPropertyMatrix();
        if (selected.contains("scalability")) {
            if (csv) System.out.println("# scalability: size,parseNs,tcNs,evalNs,reduceNs,steps,name,run");
            for (int r = 0; r < runs; r++) runScalabilityBenchmark(csv, r);
        }
        if (selected.contains("typeerror"))
            runTypeErrorRecovery();
        if (selected.contains("coverage"))
            runASTCoverage();
        if (selected.contains("steps")) {
            if (csv) System.out.println("# steps: run,steps,count");
            for (int r = 0; r < runs; r++) runStepDistribution(csv, r);
        }
        if (selected.contains("parser"))
            runParserErrorRecovery();
        if (selected.contains("empirical"))
            runEmpiricalMatrix(runs, csv);
        if (selected.contains("breakdown")) {
            if (csv) System.out.println("# breakdown: run,size,parseNs,tcNs,evalNs,reduceNs");
            for (int r = 0; r < runs; r++) runRuntimeBreakdown(csv, r);
        }
    }
}
