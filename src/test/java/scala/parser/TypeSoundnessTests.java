package scala.parser;

import java.util.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.statistics.*;
import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.ast.CaseNode.*;
import scala.parser.core.ast.DataDeclNode.ConstructorDef;
import scala.parser.core.interpreter.Evaluator;
import scala.parser.core.interpreter.Value;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

import static org.junit.jupiter.api.Assertions.*;

class TypeSoundnessTests {

    // Seeds for reproducibility of fuzz testing
    private static final long[] FUZZ_SEEDS = {
        -8130541566584379553L, -238832842248951800L, -2329395849539171863L,
        42L, 12345L, 99999L, 2024L, 314159L, 271828L, 161803L
    };

    private static final List<String> IDENTS = List.of("x", "y", "z", "f", "g", "n", "b", "s");
    private static final List<String> BINOPS = List.of("+", "-", "*", "/", "==", "!=", "<", ">", "<=", ">=", "&&", "||");

    private static final List<DataTemplate> DATA_TPL = List.of(
        new DataTemplate("MyBool", List.of(),
            List.of(new CtorDef("MyTrue", List.of()), new CtorDef("MyFalse", List.of()))),
        new DataTemplate("MyMaybe", List.of("a"),
            List.of(new CtorDef("MyNothing", List.of()), new CtorDef("MyJust", List.of("a"))))
    );

    record DataTemplate(String name, List<String> params, List<CtorDef> ctors) {}
    record CtorDef(String name, List<String> argTypes) {}

    @Property(tries = 1000)
    void typeSoundness(@ForAll("program") ProgramNode program) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(program);
        if (!errors.isEmpty()) return;
        assertDoesNotThrow(() -> new Evaluator().evalProgram(program),
                "Well-typed program must not crash during evaluation");
    }

    @Property(tries = 500)
    void determinism(@ForAll("program") ProgramNode program) {
        var e1 = new ArrayList<ParserError>();
        new TypeChecker(e1).check(program);
        var e2 = new ArrayList<ParserError>();
        new TypeChecker(e2).check(program);
        assertEquals(e1.size(), e2.size(), "Type checking must be deterministic");
        if (!e1.isEmpty()) return;
        var v1 = new Evaluator().evalProgram(program);
        var v2 = new Evaluator().evalProgram(program);
        assertEquals(v1 != null ? v1.toString() : "null",
                     v2 != null ? v2.toString() : "null",
                     "Evaluation must be deterministic");
    }

    @Property(tries = 500)
    void weakening(@ForAll("program") ProgramNode program) {
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        if (!errors.isEmpty()) return;
        var extended = new ProgramNode(Stream.concat(
            Stream.of(new LetStatementNode("_unused", new LiteralNode("0", 0, 0), 0, 0)),
            program.statements.stream()
        ).toList());
        var errors2 = new ArrayList<ParserError>();
        new TypeChecker(errors2).check(extended);
        assertTrue(errors2.isEmpty(), "Weakening: adding unused let must preserve typability");
    }

    @Property(tries = 5000)
    void parseRoundTripFuzz(@ForAll("program") ProgramNode program) {
        String source = stringify(program);
        var tokens = new Tokenizer(source).tokenize();
        var result = new Parser(tokens).parse();
        if (!result.errors.isEmpty()) return;
        var strippedStmts = result.ast.statements.stream()
            .filter(s -> !(s instanceof LiteralNode lit && lit.value.equals("0") && lit.line == -1))
            .toList();
        var stripped = new ProgramNode(strippedStmts);
        assertTrue(astEquals(program, stripped),
            "Fuzz round-trip mismatch\nSource:\n" + source);
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(result.ast);
        if (errors.isEmpty()) {
            assertDoesNotThrow(() -> new Evaluator().evalProgram(result.ast));
        }
    }

    @Property(tries = 500)
    void parseRoundTrip(@ForAll("program") ProgramNode program) {
        String source = stringify(program);
        var tokens = new Tokenizer(source).tokenize();
        var tokenStrs = new StringBuilder();
        for (var tok : tokens) tokenStrs.append(tok.type).append('(').append(tok.text).append(") ");
        var result = new Parser(tokens).parse();
        if (!result.errors.isEmpty()) return;
        // Strip no-op statements (LiteralNode("0", -1, -1)) from parsed AST;
        // these are inserted by `;` statement separators
        var strippedStmts = result.ast.statements.stream()
            .filter(s -> !(s instanceof LiteralNode lit && lit.value.equals("0") && lit.line == -1))
            .toList();
        var stripped = new ProgramNode(strippedStmts);
        assertTrue(astEquals(program, stripped),
            "Round-trip AST mismatch\nOriginal: " + program.statements.size() + " stmts\n"
            +             "Parsed:   " + stripped.statements.size() + " stmts\n"
            + "Tokens: " + tokenStrs + "\nSource:\n" + source);
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(result.ast);
        if (errors.isEmpty()) {
            assertDoesNotThrow(() -> new Evaluator().evalProgram(result.ast));
        }
    }

    // ---------------------------------------------------------------
    // Negative PBTs: type errors must be detected and informative
    // ---------------------------------------------------------------

    @Property(tries = 500)
    void notOnNonBool(@ForAll("badType") ExpressionNode expr) {
        var program = new ProgramNode(List.of(new UnaryExprNode("!", expr, 0, 0)));
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        assertFalse(errors.isEmpty(), "'!' on non-Bool must be rejected");
        boolean hasBool = errors.stream().anyMatch(e -> e.message.contains("Bool"));
        assertTrue(hasBool, "Error must mention Bool, got first: " + errors.get(0).message);
    }

    @Property(tries = 500)
    void ifCondMustBeBool(@ForAll("badType") ExpressionNode cond) {
        var program = new ProgramNode(List.of(new IfNode(cond, new LiteralNode("0", 0, 0), new LiteralNode("0", 0, 0))));
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        assertFalse(errors.isEmpty(), "Non-Bool if-condition must be rejected");
        boolean hasBool = errors.stream().anyMatch(e -> e.message.contains("Bool"));
        assertTrue(hasBool, "Error must mention Bool, got first: " + errors.get(0).message);
    }

    @Property(tries = 500)
    void noDirectSelfRecursion(@ForAll("program") ProgramNode program) {
        var recProg = new ProgramNode(Stream.concat(program.statements.stream(),
            Stream.of(new LetStatementNode("f", new IdentifierNode("f", 0, 0), true, null, 0, 0))
        ).toList());
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(recProg);
        boolean hasRecError = errors.stream().anyMatch(e -> e.message.contains("directly refers to itself"));
        assertTrue(hasRecError, "let rec f = f must produce self-reference error");
    }

    @Property(tries = 500)
    void undeclaredVariable(@ForAll("program") ProgramNode program) {
        var badProg = new ProgramNode(Stream.concat(program.statements.stream(),
            Stream.of(new IdentifierNode("__doesNotExist__", 0, 0))
        ).toList());
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(badProg);
        boolean hasUndef = errors.stream().anyMatch(e -> e.message.contains("Undefined"));
        assertTrue(hasUndef, "Reference to undeclared variable must produce undefined error");
    }

    @Property(tries = 500)
    void typeErrorMessagesAreInformative(@ForAll("program") ProgramNode program) {
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        for (var e : errors) {
            assertNotNull(e.message, "Error message must not be null");
            assertFalse(e.message.isBlank(), "Error message must not be blank");
            assertTrue(e.message.length() > 5, "Error message must be descriptive, got: '" + e.message + "'");
        }
    }

    @Property(tries = 1000)
    void exhaustivenessCheck(@ForAll("programWithData") ProgramNode program) {
        if (program.statements.isEmpty()) return;
        // Find a data decl and a case expression that might miss constructors
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(program);
        for (var e : errors) {
            if (e.message.contains("Non-exhaustive")) {
                assertTrue(e.message.contains("missing constructor"),
                    "Exhaustiveness error must name missing constructor: " + e.message);
            }
        }
    }

    // ---------------------------------------------------------------
    // Alternative providers for negative testing
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<ExpressionNode> badType() {
        return Arbitraries.randomValue(random -> {
            // Generate expressions that are always well-defined but NEVER Bool:
            // numbers and strings are concrete wrong types for ! / if-cond
            int kind = random.nextInt(3);
            return switch (kind) {
                case 0 -> new LiteralNode(String.valueOf(random.nextInt(1000)), 0, 0);
                case 1 -> new StringLiteralNode("oops", 0, 0);
                default -> new BinaryExprNode(
                    new LiteralNode(String.valueOf(random.nextInt(1000)), 0, 0), "+",
                    new LiteralNode(String.valueOf(random.nextInt(1000)), 0, 0), 0, 0);
            };
        });
    }

    @Provide
    Arbitrary<ProgramNode> programWithData() {
        return Arbitraries.randomValue(random -> {
            int n = random.nextInt(4) + 1;
            var stmts = new ArrayList<ASTNode>();
            if (!DATA_TPL.isEmpty()) {
                var tpl = DATA_TPL.get(random.nextInt(DATA_TPL.size()));
                stmts.add(new DataDeclNode(tpl.name(), tpl.params(),
                    tpl.ctors().stream().map(c -> new ConstructorDef(c.name(), c.argTypes())).toList(), 0, 0));
                // Generate a case that might miss some constructors
                var ct = tpl.ctors().get(random.nextInt(tpl.ctors().size()));
                var disc = ct.argTypes().isEmpty()
                    ? new IdentifierNode(ct.name(), 0, 0)
                    : new ApplicationNode(new IdentifierNode(ct.name(), 0, 0),
                        genLeaf(random), 0, 0);
                var branches = new ArrayList<CaseBranch>();
                int skipCtor = random.nextInt(tpl.ctors().size());
                for (int i = 0; i < tpl.ctors().size(); i++) {
                    if (i == skipCtor && random.nextDouble() < 0.5) continue;
                    var c = tpl.ctors().get(i);
                    var bindings = new ArrayList<CaseNode.Binding>();
                    for (int j = 0; j < c.argTypes().size(); j++)
                        bindings.add(new CaseNode.Binding(IDENTS.get(random.nextInt(IDENTS.size())), 0, 0));
                    branches.add(new CaseBranch(c.name(), bindings, genLeaf(random)));
                }
                stmts.add(new CaseNode(disc, branches, 0, 0));
            }
            for (int i = stmts.size(); i < n; i++)
                stmts.add(genLeaf(random));
            return new ProgramNode(stmts);
        });
    }

    @Provide
    Arbitrary<ProgramNode> program() {
        return Arbitraries.randomValue(random -> {
            int n = random.nextInt(6) + 1;
            var stmts = new ArrayList<ASTNode>();
            boolean hasData = random.nextDouble() < 0.3 && !DATA_TPL.isEmpty();
            DataTemplate tpl = hasData ? DATA_TPL.get(random.nextInt(DATA_TPL.size())) : null;
            if (hasData) {
                stmts.add(new DataDeclNode(tpl.name(), tpl.params(),
                    tpl.ctors().stream().map(c -> new ConstructorDef(c.name(), c.argTypes())).toList(),
                    0, 0));
            }
            for (int i = stmts.size(); i < n; i++) {
                if (random.nextDouble() < 0.4 && i < n - 1) {
                    stmts.add(new LetStatementNode(
                        IDENTS.get(random.nextInt(IDENTS.size())), genExpr(random, 3, tpl), 0, 0));
                } else {
                    stmts.add(genExpr(random, 3, tpl));
                }
            }
            return new ProgramNode(stmts);
        });
    }

    private static ExpressionNode genExpr(Random rand, int depth, DataTemplate tpl) {
        if (depth <= 0 || (rand.nextDouble() < 0.12 && depth < 4)) return genLeaf(rand);
        return switch (rand.nextInt(14)) {
            case 0, 1 -> genLeaf(rand);
            case 2 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 3 -> new BoolNode(0, 0, rand.nextBoolean());
            case 4 -> new StringLiteralNode(sampleString(rand), 0, 0);
            case 5 -> {
                yield new LambdaNode(IDENTS.get(rand.nextInt(IDENTS.size())),
                    genExpr(rand, depth - 1, tpl), 0, 0);
            }
            case 6 -> new ApplicationNode(genExpr(rand, depth - 1, tpl),
                genExpr(rand, depth - 1, tpl), 0, 0);
            case 7 -> {
                ExpressionNode c = genExpr(rand, depth - 1, tpl);
                yield new IfNode(c, genExpr(rand, depth - 1, tpl),
                    genExpr(rand, depth - 1, tpl));
            }
            case 8 -> {
                yield new BinaryExprNode(genExpr(rand, depth - 1, tpl),
                    BINOPS.get(rand.nextInt(BINOPS.size())),
                    genExpr(rand, depth - 1, tpl), 0, 0);
            }
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
            case 2 -> new StringLiteralNode(sampleString(rand), 0, 0);
            default -> new IdentifierNode(IDENTS.get(rand.nextInt(IDENTS.size())), 0, 0);
        };
    }

    private static String sampleString(Random rand) {
        return switch (rand.nextInt(5)) {
            case 0 -> "hello";
            case 1 -> "";
            case 2 -> "42";
            case 3 -> "true";
            default -> "x";
        };
    }

    // ---------------------------------------------------------------
    // Stringifier
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
            return "let " + l.name + " = " + stringify(l.value);
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
            // parsePrimary() doesn't handle IF/Lambda/Case directly;
            // wrap in parens so parsePrimary's (expr) mechanism handles it
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
            return "!" + op;
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
                // If the branch body transitively contains a CaseNode,
                // wrap in parens to prevent | separator leakage to this case
                if (containsCase(br.body()))
                    body = "(" + body + ")";
                sb.append(body);
            }
            return sb.toString();
        }
        return "0";
    }

    // Does an expression transitively contain a CaseNode?
    // Used to determine if we must parenthesize a case branch body
    // to prevent inner | tokens from leaking to the outer case parser.
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

    // ---------------------------------------------------------------
    // Structural AST equality (ignoring line/column)
    // ---------------------------------------------------------------

    static boolean astEquals(ASTNode a, ASTNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof ProgramNode p1) {
            var p2 = (ProgramNode) b;
            if (p1.statements.size() != p2.statements.size()) return false;
            for (int i = 0; i < p1.statements.size(); i++)
                if (!astEquals(p1.statements.get(i), p2.statements.get(i))) return false;
            return true;
        }
        if (a instanceof LetStatementNode l1) {
            var l2 = (LetStatementNode) b;
            return l1.name.equals(l2.name) && l1.isRec == l2.isRec
                && astEquals(l1.value, l2.value);
        }
        if (a instanceof DataDeclNode d1) {
            var d2 = (DataDeclNode) b;
            return d1.name.equals(d2.name) && d1.params.equals(d2.params)
                && d1.constructors.equals(d2.constructors);
        }
        if (a instanceof LambdaNode lam1) {
            var lam2 = (LambdaNode) b;
            return lam1.param.equals(lam2.param) && astEquals(lam1.body, lam2.body);
        }
        if (a instanceof ApplicationNode app1) {
            var app2 = (ApplicationNode) b;
            return astEquals(app1.function, app2.function)
                && astEquals(app1.argument, app2.argument);
        }
        if (a instanceof IfNode i1) {
            var i2 = (IfNode) b;
            return astEquals(i1.condition, i2.condition)
                && astEquals(i1.thenBranch, i2.thenBranch)
                && astEquals(i1.elseBranch, i2.elseBranch);
        }
        if (a instanceof LiteralNode lit1) return lit1.value.equals(((LiteralNode) b).value);
        if (a instanceof BoolNode b1) return b1.value == ((BoolNode) b).value;
        if (a instanceof StringLiteralNode s1) return s1.value.equals(((StringLiteralNode) b).value);
        if (a instanceof IdentifierNode id1) return id1.name.equals(((IdentifierNode) b).name);
        if (a instanceof BinaryExprNode bin1) {
            var bin2 = (BinaryExprNode) b;
            return bin1.operator.equals(bin2.operator)
                && astEquals(bin1.left, bin2.left) && astEquals(bin1.right, bin2.right);
        }
        if (a instanceof UnaryExprNode un1) {
            var un2 = (UnaryExprNode) b;
            return un1.operator.equals(un2.operator) && astEquals(un1.operand, un2.operand);
        }
        if (a instanceof CaseNode c1) {
            var c2 = (CaseNode) b;
            if (!astEquals(c1.expr, c2.expr)) return false;
            if (c1.branches.size() != c2.branches.size()) return false;
            for (int i = 0; i < c1.branches.size(); i++)
                if (!branchEquals(c1.branches.get(i), c2.branches.get(i))) return false;
            return true;
        }
        return false;
    }

    private static boolean branchEquals(CaseBranch b1, CaseBranch b2) {
        if (!b1.constructor().equals(b2.constructor())) return false;
        if (b1.bindings().size() != b2.bindings().size()) return false;
        for (int i = 0; i < b1.bindings().size(); i++)
            if (!b1.bindings().get(i).name().equals(b2.bindings().get(i).name())) return false;
        return astEquals(b1.body(), b2.body());
    }

    // ---------------------------------------------------------------
    // Type Error Localization (mutation-based)
    // ---------------------------------------------------------------

    @Property(tries = 1000)
    void typeErrorLocalization(@ForAll("program") ProgramNode program) {
        String source = stringify(program);
        var tokens = new Tokenizer(source).tokenize();
        var result = new Parser(tokens).parse();
        if (!result.errors.isEmpty()) return;
        var errors = new ArrayList<ParserError>();
        new TypeChecker(errors).check(result.ast);
        if (!errors.isEmpty()) return;

        var mutable = new ArrayList<Token>();
        for (var tok : tokens) {
            if (tok.type == TokenType.NUMBER || tok.type == TokenType.TRUE
                || tok.type == TokenType.FALSE || tok.type == TokenType.STRING)
                mutable.add(tok);
        }
        if (mutable.isEmpty()) return;

        var rand = new Random();
        int pick = rand.nextInt(mutable.size());
        var target = mutable.get(pick);
        var replacement = mutationOf(target, rand);

        var mutatedTokens = new ArrayList<Token>(tokens.size());
        for (var tok : tokens)
            mutatedTokens.add(tok == target ? replacement : tok);

        var mutatedResult = new Parser(mutatedTokens).parse();
        if (mutatedResult.errors.isEmpty()) return;

        var first = mutatedResult.errors.get(0);
        boolean correct = first.line == target.line && first.column == target.column;
        assertTrue(correct,
            "Error localization failed: mutated at (" + target.line + "," + target.column + ") "
            + "but first error at (" + first.line + "," + first.column + "): "
            + first.message);
    }

    private static Token mutationOf(Token original, Random rand) {
        return switch (original.type) {
            case NUMBER -> rand.nextBoolean()
                ? new Token(TokenType.TRUE, "true", original.line, original.column)
                : new Token(TokenType.STRING, "x", original.line, original.column);
            case TRUE, FALSE -> rand.nextBoolean()
                ? new Token(TokenType.NUMBER, "0", original.line, original.column)
                : new Token(TokenType.STRING, "x", original.line, original.column);
            case STRING -> new Token(TokenType.NUMBER, "0", original.line, original.column);
            default -> throw new IllegalArgumentException("" + original.type);
        };
    }

    // ---------------------------------------------------------------
    // Error Recovery Quality — mutation-based cascade measurement
    // ---------------------------------------------------------------

    @Property(tries = 500)
    void errorRecoveryQuality(@ForAll("program") ProgramNode program) {
        String source = stringify(program);
        var tokens = new Tokenizer(source).tokenize();

        var deletable = new ArrayList<Integer>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            var t = tokens.get(i).type;
            if (t != TokenType.SEMICOLON && t != TokenType.EOF)
                deletable.add(i);
        }
        if (deletable.isEmpty()) return;

        var rand = new Random();
        int kind = rand.nextInt(3);
        int idx = deletable.get(rand.nextInt(deletable.size()));
        var tok = tokens.get(idx);

        var mutant = new ArrayList<Token>(tokens);
        switch (kind) {
            case 0 -> mutant.remove(idx);
            case 1 -> mutant.set(idx, new Token(
                TokenType.IDENT, "!!INVALID!!", tok.line, tok.column));
            default -> mutant.add(idx, new Token(
                TokenType.IDENT, "!!INVALID!!", tok.line, tok.column));
        }

        var result = new Parser(mutant).parse();
        if (result.errors.isEmpty()) return;

        int total = result.errors.size();
        // Precision = how many of the reported errors are real (1) vs cascade
        double precision = 1.0 / total;
        // Record as jqwik statistics
        Statistics.collect("cascade/errors", total);
        Statistics.collect("cascade/precision", String.format("%.2f", precision));

        // Sanity: most mutations should produce ≤5 cascade errors
        assertTrue(total <= 10,
            "Excessive error cascade (" + total + " errors from 1 mutation):\n"
            + "Source: " + source.replace("\n", "\\n"));
    }
}
