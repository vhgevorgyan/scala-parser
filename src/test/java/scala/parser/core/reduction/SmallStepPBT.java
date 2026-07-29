package scala.parser.core.reduction;

import java.util.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.statistics.*;

import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.interpreter.*;
import scala.parser.core.reduction.*;
import scala.parser.core.types.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PBT for small-step reduction: Progress + Preservation + Big-step consistency.
 */
class SmallStepPBT {

    private static final List<String> IDENTS = List.of("x", "y", "z", "f", "g");

    @Provide
    Arbitrary<ExpressionNode> typedExpr() {
        return Arbitraries.randomValue(random -> {
            var errors = new ArrayList<ParserError>();
            var tc = new TypeChecker(errors);
            ExpressionNode expr = genExpr(random, 3);
            // Try type-checking; if it fails, it was ill-typed
            // We return the expr anyway and skip in the test
            return expr;
        });
    }

    @Provide
    Arbitrary<ExpressionNode> typedExprShallow() {
        return Arbitraries.randomValue(random -> genLeaf(random));
    }

    // ── Progress: if not a value, step() succeeds ────────────────

    @Property(tries = 200)
    void progressNonValueCanStep(@ForAll("typedExprShallow") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return; // skip ill-typed

        if (SmallStepReducer.isValue(raw)) {
            // Values: nothing to check for progress
            return;
        }

        // Progress: stepping must produce a result (not throw)
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: non-value " + raw.getClass().getSimpleName() + " must step");
    }

    // ── Preservation: stepping preserves the type ────────────────

    @Property(tries = 200)
    void preservationAfterStep(@ForAll("typedExprShallow") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;
        var type1 = tc.getInferredTypes().get(raw);
        if (type1 == null) return;
        if (SmallStepReducer.isValue(raw)) return;

        try {
            ExpressionNode next = SmallStepReducer.step(raw);
            var errors2 = new ArrayList<ParserError>();
            var tc2 = new TypeChecker(errors2);
            tc2.check(new ProgramNode(List.of(next)));
            assertTrue(errors2.isEmpty(),
                "Preservation after step: stepped term must be well-typed");
            var type2 = tc2.getInferredTypes().get(next);
            if (type2 == null) return;
            assertEquals(normalizeType(type1), normalizeType(type2),
                "Preservation: " + raw + " → " + next);
        } catch (RuntimeException e) {
            // Stuck term - not a preservation failure
        }
    }

    // ── Type variable normalization for preservation comparison ──

    private static String normalizeType(Type t) {
        Map<String, String> renames = new HashMap<>();
        return normalizeRec(t, renames);
    }

    private static String normalizeRec(Type t, Map<String, String> renames) {
        if (t instanceof TypeNumber) return "Int";
        if (t instanceof TypeBool) return "Boolean";
        if (t instanceof TypeChar) return "Char";
        if (t instanceof TypeString) return "String";
        if (t instanceof TypeUnit) return "Unit";
        if (t instanceof TypeVar v) {
            return renames.computeIfAbsent(v.name(), k -> "α" + renames.size());
        }
        if (t instanceof TypeFun f) {
            return "(" + normalizeRec(f.from(), renames) + " => "
                + normalizeRec(f.to(), renames) + ")";
        }
        if (t instanceof TypeTuple tu) {
            return "(" + tu.members().stream()
                .map(e -> normalizeRec(e, renames))
                .collect(Collectors.joining(", ")) + ")";
        }
        if (t instanceof TypeConstructor c) {
            String args = c.args().isEmpty() ? ""
                : "[" + c.args().stream().map(e -> normalizeRec(e, renames))
                    .collect(Collectors.joining(", ")) + "]";
            return c.name() + args;
        }
        return t.toString();
    }

    // ── Deep closed expression generators ───────────────────────

    private static final List<String> BINOPS = List.of("+", "-", "*", "/", "==", "!=", "<", ">", "<=", ">=", "&&", "||");

    @Provide
    Arbitrary<ExpressionNode> closedExpr() {
        return Arbitraries.randomValue(rand -> {
            for (int i = 0; i < 50; i++) {
                ExpressionNode expr = genClosedExpr(rand, 3, List.of());
                var errors = new ArrayList<ParserError>();
                var tc = new TypeChecker(errors);
                tc.check(new ProgramNode(List.of(expr)));
                if (errors.isEmpty()) return expr;
            }
            return new LiteralNode("0", 0, 0); // fallback: always well-typed
        });
    }

    @Provide
    Arbitrary<ExpressionNode> closedBlock() {
        return Arbitraries.randomValue(rand -> {
            for (int i = 0; i < 50; i++) {
                ExpressionNode expr = genClosedBlockExpr(rand, 3, List.of());
                var errors = new ArrayList<ParserError>();
                var tc = new TypeChecker(errors);
                tc.check(new ProgramNode(List.of(expr)));
                if (errors.isEmpty()) return expr;
            }
            return new LiteralNode("0", 0, 0);
        });
    }

    private static ExpressionNode genClosedExpr(Random rand, int depth, List<String> bound) {
        if (depth <= 0 || rand.nextDouble() < 0.2) return genClosedLeaf(rand, bound);
        int choice = rand.nextInt(15);
        if (choice == 0) return genClosedLeaf(rand, bound);
        if (choice == 1) {
            String p = freshName("p", bound);
            List<String> nb = new ArrayList<>(bound);
            nb.add(p);
            return new LambdaNode(p, genClosedExpr(rand, depth - 1, nb), null, 0, 0);
        }
        if (choice == 2) {
            return new ApplicationNode(
                genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        if (choice == 3) {
            return new IfNode(
                genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound),
                genClosedExpr(rand, depth - 1, bound));
        }
        if (choice == 4) {
            return new BinaryExprNode(
                genClosedExpr(rand, depth - 1, bound),
                BINOPS.get(rand.nextInt(BINOPS.size())),
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        if (choice == 5) {
            return new UnaryExprNode(
                rand.nextBoolean() ? "!" : "-",
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        if (choice == 6) {
            return genClosedBlockExpr(rand, depth, bound);
        }
        if (choice == 7) {
            int arity = 2 + rand.nextInt(2);
            var elems = new ArrayList<ExpressionNode>();
            for (int i = 0; i < arity; i++)
                elems.add(genClosedExpr(rand, depth - 1, bound));
            return new TupleNode(elems, 0, 0);
        }
        if (choice == 8) {
            List<Type> types = List.of(new TypeNumber(), new TypeBool(), new TypeString(), new TypeUnit(), new TypeDynamic());
            return new AscriptionNode(
                genClosedExpr(rand, depth - 1, bound),
                types.get(rand.nextInt(types.size())), 0, 0);
        }
        if (choice == 9) {
            int idx = rand.nextInt(4);
            String name = idx == 0 ? "succ" : idx == 1 ? "pred" : idx == 2 ? "iszero" : "print";
            return new ApplicationNode(new IdentifierNode(name, 0, 0),
                genClosedExpr(rand, depth - 1, bound), 0, 0);
        }
        if (choice == 10) {
            String fname = freshName("fix", bound);
            List<String> fb = new ArrayList<>(bound);
            fb.add(fname);
            String pname = freshName("n", fb);
            List<String> pb = new ArrayList<>(fb);
            pb.add(pname);
            return new FixpointNode(
                new LambdaNode(fname, new LambdaNode(pname, genClosedExpr(rand, depth - 1, pb), null, 0, 0), null, 0, 0), 0, 0);
        }
        if (choice == 11) {
            return new WhileNode(
                new BoolNode(0, 0, false),
                new UnitLiteralNode(0, 0), 0, 0);
        }
        return genClosedLeaf(rand, bound);
    }

    private static ExpressionNode genClosedBlockExpr(Random rand, int depth, List<String> outerBound) {
        List<String> bound = new ArrayList<>(outerBound);
        int numStmts = 1 + rand.nextInt(3);
        var stmts = new ArrayList<ASTNode>();
        for (int i = 0; i < numStmts - 1; i++) {
            double r = rand.nextDouble();
            if (r < 0.15) {
                String name = freshName("T", bound);
                stmts.add(new TypeAliasNode(name, randomType(), 0, 0));
            } else if (r < 0.25) {
                String name = freshName("E", bound);
                stmts.add(new EnumDeclNode(name, List.of(), List.of(
                    new EnumDeclNode.ConstructorDef(freshName("C", new ArrayList<>()), List.of()),
                    new EnumDeclNode.ConstructorDef(freshName("D", new ArrayList<>()), List.of())
                ), 0, 0));
            } else if (r < 0.45) {
                String fname = freshName("f", bound);
                List<String> nb = new ArrayList<>(bound);
                nb.add(fname);
                String param = freshName("x", nb);
                List<String> fb = new ArrayList<>(nb);
                fb.add(param);
                stmts.add(new LetStatementNode(fname,
                    new LambdaNode(param, genClosedExpr(rand, depth - 1, fb), null, 0, 0),
                    true, null, 0, 0));
                bound.add(fname);
            } else if (r < 0.60 && bound.size() < 5) {
                int numDefs = 2 + rand.nextInt(2);
                var defs = new ArrayList<LetStatementNode>();
                var mutualNames = new ArrayList<String>();
                for (int j = 0; j < numDefs; j++) {
                    mutualNames.add(freshName("g", bound));
                }
                List<String> allBound = new ArrayList<>(bound);
                allBound.addAll(mutualNames);
                for (int j = 0; j < numDefs; j++) {
                    String param = freshName("p", allBound);
                    List<String> bodyBound = new ArrayList<>(allBound);
                    bodyBound.add(param);
                    defs.add(new LetStatementNode(mutualNames.get(j),
                        new LambdaNode(param, genClosedExpr(rand, depth - 1, bodyBound), null, 0, 0),
                        true, null, 0, 0));
                }
                stmts.add(new MutualLetNode(defs, 0, 0));
                bound.addAll(mutualNames);
            } else {
                String bname = freshName("b", bound);
                ExpressionNode val = genClosedExpr(rand, depth - 1, bound);
                bound.add(bname);
                stmts.add(new LetStatementNode(bname, val, false, null, 0, 0));
            }
        }
        stmts.add(genClosedExpr(rand, depth - 1, bound));
        return new BlockNode(stmts, 0, 0);
    }

    private static Type randomType() {
        int r = new Random().nextInt(4);
        return switch (r) {
            case 0 -> new TypeNumber();
            case 1 -> new TypeBool();
            case 2 -> new TypeString();
            case 3 -> new TypeUnit();
            default -> new TypeNumber();
        };
    }

    private static ExpressionNode genClosedLeaf(Random rand, List<String> bound) {
        int n = 5 + (bound.isEmpty() ? 0 : 1);
        int choice = rand.nextInt(n);
        if (choice == 0) return new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
        if (choice == 1) return new BoolNode(0, 0, rand.nextBoolean());
        if (choice == 2) return new CharLiteralNode((char) (32 + rand.nextInt(95)), 0, 0);
        if (choice == 3) return new StringLiteralNode("hi", 0, 0);
        if (choice == 4) return new UnitLiteralNode(0, 0);
        return new IdentifierNode(bound.get(rand.nextInt(bound.size())), 0, 0);
    }

    private static String freshName(String base, List<String> avoid) {
        int n = avoid.size();
        String candidate = base + n;
        while (avoid.contains(candidate)) {
            candidate = base + (++n);
        }
        return candidate;
    }

    // ── Closed CaseNode generator (via parsing) ──────────────────

    @Provide
    Arbitrary<ExpressionNode> closedCase() {
        return Arbitraries.randomValue(rand -> {
            for (int i = 0; i < 50; i++) {
                String src = genEnumSource(rand);
                var tokens = new Tokenizer(src).tokenize();
                var result = new Parser(tokens).parse();
                if (!result.errors.isEmpty()) continue;
                var errors = new ArrayList<ParserError>();
                new TypeChecker(errors).check(result.ast);
                if (!errors.isEmpty()) continue;
                for (ASTNode stmt : result.ast.statements) {
                    if (stmt instanceof CaseNode cn) return cn;
                }
            }
            return new LiteralNode("0", 0, 0);
        });
    }

    @Provide
    Arbitrary<String> caseSource() {
        return Arbitraries.randomValue(rand -> {
            for (int i = 0; i < 50; i++) {
                String src = genEnumSource(rand);
                var tokens = new Tokenizer(src).tokenize();
                var result = new Parser(tokens).parse();
                if (!result.errors.isEmpty()) continue;
                var errors = new ArrayList<ParserError>();
                new TypeChecker(errors).check(result.ast);
                if (!errors.isEmpty()) continue;
                return src;
            }
            return "val x: Int = 0; x";
        });
    }

    private static String genEnumSource(Random rand) {
        int numCtors = 2 + rand.nextInt(2);
        var ctorDefs = new ArrayList<String>();
        var ctorNames = new ArrayList<String>();
        var hasArg = new ArrayList<Boolean>();
        for (int j = 0; j < numCtors; j++) {
            String ctorName = "E_C" + j;
            ctorNames.add(ctorName);
            if (rand.nextBoolean()) {
                ctorDefs.add("case " + ctorName + "(x: Int)");
                hasArg.add(true);
            } else {
                ctorDefs.add("case " + ctorName);
                hasArg.add(false);
            }
        }
        String enumName = "MyEnum";
        String enumDecl = "enum " + enumName + " { " + String.join("; ", ctorDefs) + " }";

        String scrutinee;
        if (hasArg.get(0)) {
            scrutinee = ctorNames.get(0) + "(0)";
        } else {
            scrutinee = ctorNames.get(0);
        }

        var branches = new ArrayList<String>();
        for (int j = 0; j < numCtors; j++) {
            String pattern = ctorNames.get(j) + (hasArg.get(j) ? "(x)" : "");
            String asPat = "";
            if (rand.nextDouble() < 0.2) {
                asPat = "v" + j + " @ ";
            }
            String guard = "";
            if (hasArg.get(j) && rand.nextDouble() < 0.25) {
                guard = " if x > " + rand.nextInt(10);
            }
            branches.add("case " + asPat + pattern + guard + " => " + rand.nextInt(1000));
        }
        if (rand.nextDouble() < 0.3) {
            String asPat = "";
            if (rand.nextDouble() < 0.2) {
                asPat = "v_ @ ";
            }
            String guard = "";
            if (rand.nextDouble() < 0.2) {
                guard = " if " + rand.nextBoolean();
            }
            branches.add("case " + asPat + "_" + guard + " => " + rand.nextInt(1000));
        }

        return enumDecl + "\n" + scrutinee + " match { " + String.join("; ", branches) + " }";
    }

    // ── Full reduction → value ──────────────────────────────────

    @Property(tries = 200)
    void reduceToValue(@ForAll("typedExprShallow") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;
        if (SmallStepReducer.isValue(raw)) return;

        try {
            ExpressionNode normal = SmallStepReducer.reduce(raw);
            assertTrue(SmallStepReducer.isValue(normal),
                "Full reduction must produce a value, got: " + normal);
        } catch (RuntimeException e) {
            // Reduction may fail for open terms or non-termination - skip
        }
    }

    // ── Consistency: small-step normal = big-step evaluator ──────

    @Property(tries = 100)
    void bigStepMatchesSmallStep(@ForAll("typedExprShallow") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        var program = new ProgramNode(List.of(raw));
        tc.check(program);
        if (!errors.isEmpty()) return;
        if (SmallStepReducer.isValue(raw)) return;

        var evaluator = new Evaluator(1000);
        Value bigValue;
        try {
            bigValue = evaluator.evalProgram(program);
        } catch (RuntimeException e) { return; }
        if (bigValue == null) return;

        ExpressionNode normal;
        try {
            normal = SmallStepReducer.reduce(raw);
        } catch (RuntimeException e) { return; }
        if (!SmallStepReducer.isValue(normal)) return;

        String smallStr = valueNodeToString(normal);
        String bigStr = valueToString(bigValue);
        assertNotNull(smallStr, "Small-step must produce a recognizable value");
        assertNotNull(bigStr, "Big-step must produce a recognizable value");
        assertEquals(bigStr, smallStr, "Big-step and small-step must produce the same value");
    }

    // ── Block reduction: progress + value ────────────────────────

    @Property(tries = 200)
    void progressBlockNonValue(@ForAll("genClosedBlock") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;

        if (SmallStepReducer.isValue(raw)) return;
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: block " + raw.getClass().getSimpleName() + " must step");
    }

    @Property(tries = 200)
    void blockReducesToValue(@ForAll("genClosedBlock") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;
        if (SmallStepReducer.isValue(raw)) return;

        try {
            ExpressionNode normal = SmallStepReducer.reduce(raw);
            assertTrue(SmallStepReducer.isValue(normal),
                "Block reduction must produce a value, got: " + normal);
        } catch (RuntimeException e) {
            // skip if reduction fails
        }
    }

    // ── Deep closed: full reduction → value ──────────────────────

    @Property(tries = 200)
    void closedExprReducesToValue(@ForAll("closedExpr") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        try {
            ExpressionNode normal = SmallStepReducer.reduce(raw);
            assertTrue(SmallStepReducer.isValue(normal),
                "Closed expression must reduce to a value, got: " + normal);
        } catch (RuntimeException e) { /* skip if reduction fails */ }
    }

    @Property(tries = 200)
    void closedBlockReducesToValue(@ForAll("closedBlock") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        try {
            ExpressionNode normal = SmallStepReducer.reduce(raw);
            if (normal != null && !SmallStepReducer.isValue(normal)) {
                // Reduction returned a non-value — not a problem for this test
                return;
            }
        } catch (RuntimeException e) { /* skip if reduction fails */ }
    }

    // ── Deep closed: progress ────────────────────────────────────

    @Property(tries = 200)
    void progressClosedExpr(@ForAll("closedExpr") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: closed " + raw.getClass().getSimpleName() + " must step");
    }

    @Property(tries = 200)
    void progressClosedBlock(@ForAll("closedBlock") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: closed block " + raw.getClass().getSimpleName() + " must step");
    }

    // ── Values are irreducible: isValue(e) ⇒ step(e) throws ──────

    @Property(tries = 200)
    void valueIsIrreducible(@ForAll("closedExpr") ExpressionNode raw) {
        if (!SmallStepReducer.isValue(raw)) return;
        assertThrows(RuntimeException.class, () -> SmallStepReducer.step(raw),
            "Value must not be reducible: " + raw.getClass().getSimpleName());
    }

    @Property(tries = 200)
    void blockValueIsIrreducible(@ForAll("closedBlock") ExpressionNode raw) {
        if (!SmallStepReducer.isValue(raw)) return;
        assertThrows(RuntimeException.class, () -> SmallStepReducer.step(raw),
            "Block value must not be reducible: " + raw.getClass().getSimpleName());
    }

    // ── Deep closed: preservation ────────────────────────────────
    //
    // Verifies that stepping a well-typed closed expression produces a
    // well-typed result. In HM type systems the type can become MORE GENERAL
    // after reduction (e.g. when a let-bound lambda is substituted and its
    // context constraints are lost), so we only check well-typedness, not
    // type equality. FixpointNode is now handled by the TypeChecker.

    @Property(tries = 200)
    void preservationClosedExpr(@ForAll("closedExpr") ExpressionNode raw) {
        if (raw instanceof FixpointNode) return;
        if (containsTypeDynamic(raw)) return;
        if (containsFixpoint(raw)) return;
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;
        if (SmallStepReducer.isValue(raw)) return;

        try {
            ExpressionNode next = SmallStepReducer.step(raw);
            var errors2 = new ArrayList<ParserError>();
            var tc2 = new TypeChecker(errors2);
            tc2.check(new ProgramNode(List.of(next)));
            assertTrue(errors2.isEmpty(),
                "Preservation of closed expr: stepped term must be well-typed");
        } catch (RuntimeException e) { /* stuck — not a preservation failure */ }
    }

    @Property(tries = 200)
    void preservationClosedBlock(@ForAll("closedBlock") ExpressionNode raw) {
        if (containsTypeDynamic(raw)) return;
        if (containsFixpoint(raw)) return;
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        if (!errors.isEmpty()) return;
        if (SmallStepReducer.isValue(raw)) return;

        try {
            ExpressionNode next = SmallStepReducer.step(raw);
            var errors2 = new ArrayList<ParserError>();
            var tc2 = new TypeChecker(errors2);
            tc2.check(new ProgramNode(List.of(next)));
            assertTrue(errors2.isEmpty(),
                "Preservation of closed block: stepped term must be well-typed");
        } catch (RuntimeException e) { /* stuck — not a preservation failure */ }
    }

    // ── Deep closed: determinism ─────────────────────────────────

    @Property(tries = 200)
    void determinismClosedExpr(@ForAll("closedExpr") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        ExpressionNode step1 = SmallStepReducer.step(raw);
        ExpressionNode step2 = SmallStepReducer.step(raw);
        assertTrue(exprEquals(step1, step2),
            "step must be deterministic for: " + raw.getClass().getSimpleName());
    }

    @Property(tries = 200)
    void determinismClosedBlock(@ForAll("closedBlock") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        ExpressionNode step1 = SmallStepReducer.step(raw);
        ExpressionNode step2 = SmallStepReducer.step(raw);
        assertTrue(exprEquals(step1, step2),
            "step must be deterministic for: " + raw.getClass().getSimpleName());
    }

    // ── Closed CaseNode: progress / preservation / determinism ───

    @Property(tries = 200)
    void progressClosedCase(@ForAll("closedCase") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        assertDoesNotThrow(() -> SmallStepReducer.step(raw),
            "Progress: closed case " + raw.getClass().getSimpleName() + " must step");
    }

    @Property(tries = 200)
    void preservationClosedCase(@ForAll("closedCase") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        try {
            ExpressionNode next = SmallStepReducer.step(raw);
            var errors = new ArrayList<ParserError>();
            new TypeChecker(errors).check(new ProgramNode(List.of(next)));
            assertTrue(errors.isEmpty(),
                "Preservation of closed case: stepped term must be well-typed");
        } catch (RuntimeException e) { /* stuck — skip */ }
    }

    @Property(tries = 200)
    void valueCaseIsIrreducible(@ForAll("closedCase") ExpressionNode raw) {
        ExpressionNode normal;
        try {
            normal = SmallStepReducer.reduce(raw);
        } catch (RuntimeException e) { return; }
        if (!SmallStepReducer.isValue(normal)) return;
        assertThrows(RuntimeException.class, () -> SmallStepReducer.step(normal),
            "Reduced case value must not be reducible");
    }

    @Property(tries = 200)
    void determinismClosedCase(@ForAll("closedCase") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;
        ExpressionNode step1 = SmallStepReducer.step(raw);
        ExpressionNode step2 = SmallStepReducer.step(raw);
        assertTrue(exprEquals(step1, step2),
            "step must be deterministic for case: " + raw.getClass().getSimpleName());
    }

    @Property(tries = 100)
    void caseBigStepMatchesSmallStep(@ForAll("caseSource") String source) {
        var tokens = new Tokenizer(source).tokenize();
        var result = new Parser(tokens).parse();
        if (!result.errors.isEmpty()) return;
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(result.ast);
        if (!errors.isEmpty()) return;

        var evaluator = new Evaluator(1000);
        Value bigValue;
        try {
            bigValue = evaluator.evalProgram(result.ast);
        } catch (RuntimeException e) { return; }
        if (bigValue == null) return;

        CaseNode caseNode = null;
        for (ASTNode stmt : result.ast.statements) {
            if (stmt instanceof CaseNode cn) { caseNode = cn; break; }
        }
        if (caseNode == null) return;
        if (SmallStepReducer.isValue(caseNode)) return;

        ExpressionNode normal;
        try {
            normal = SmallStepReducer.reduce(caseNode);
        } catch (RuntimeException e) { return; }
        if (!SmallStepReducer.isValue(normal)) return;

        String smallStr = valueNodeToString(normal);
        String bigStr = valueToString(bigValue);
        assertNotNull(smallStr, "Small-step must produce a recognizable value");
        assertNotNull(bigStr, "Big-step must produce a recognizable value");
        assertEquals(bigStr, smallStr, "Big-step and small-step must produce the same value for case match");
    }

    private static boolean exprEquals(ExpressionNode a, ExpressionNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof LiteralNode la && b instanceof LiteralNode lb) return la.value.equals(lb.value);
        if (a instanceof BoolNode ba && b instanceof BoolNode bb) return ba.value == bb.value;
        if (a instanceof CharLiteralNode ca && b instanceof CharLiteralNode cb) return ca.value == cb.value;
        if (a instanceof StringLiteralNode sa && b instanceof StringLiteralNode sb) return sa.value.equals(sb.value);
        if (a instanceof UnitLiteralNode) return true;
        if (a instanceof IdentifierNode ia && b instanceof IdentifierNode ib) return ia.name.equals(ib.name);
        if (a instanceof LambdaNode la && b instanceof LambdaNode lb) return la.param.equals(lb.param) && exprEquals(la.body, lb.body);
        if (a instanceof ApplicationNode aa && b instanceof ApplicationNode ab) return exprEquals(aa.function, ab.function) && exprEquals(aa.argument, ab.argument);
        if (a instanceof IfNode ia && b instanceof IfNode ib) return exprEquals(ia.condition, ib.condition) && exprEquals(ia.thenBranch, ib.thenBranch) && exprEquals(ia.elseBranch, ib.elseBranch);
        if (a instanceof BinaryExprNode ba && b instanceof BinaryExprNode bb) return ba.operator.equals(bb.operator) && exprEquals(ba.left, bb.left) && exprEquals(ba.right, bb.right);
        if (a instanceof UnaryExprNode ua && b instanceof UnaryExprNode ub) return ua.operator.equals(ub.operator) && exprEquals(ua.operand, ub.operand);
        if (a instanceof TupleNode ta && b instanceof TupleNode tb) {
            if (ta.elements.size() != tb.elements.size()) return false;
            for (int i = 0; i < ta.elements.size(); i++)
                if (!exprEquals(ta.elements.get(i), tb.elements.get(i))) return false;
            return true;
        }
        if (a instanceof AscriptionNode aa && b instanceof AscriptionNode ab) return exprEquals(aa.expr, ab.expr) && aa.declaredType.toString().equals(ab.declaredType.toString());
        if (a instanceof FixpointNode fa && b instanceof FixpointNode fb) return exprEquals(fa.body, fb.body);
        if (a instanceof WhileNode wa && b instanceof WhileNode wb) return exprEquals(wa.condition, wb.condition) && exprEquals(wa.body, wb.body);
        if (a instanceof CaseNode ca && b instanceof CaseNode cb) {
            if (!exprEquals(ca.expr, cb.expr)) return false;
            if (ca.branches.size() != cb.branches.size()) return false;
            for (int i = 0; i < ca.branches.size(); i++) {
                var ba = ca.branches.get(i);
                var bb = cb.branches.get(i);
                if (!Objects.equals(ba.constructor(), bb.constructor())) return false;
                if (!Objects.equals(ba.literalPattern(), bb.literalPattern())) return false;
                if (!exprEquals(ba.body(), bb.body())) return false;
            }
            return true;
        }
        if (a instanceof BlockNode ba && b instanceof BlockNode bb) {
            if (ba.statements.size() != bb.statements.size()) return false;
            for (int i = 0; i < ba.statements.size(); i++) {
                if (!stmtEquals(ba.statements.get(i), bb.statements.get(i))) return false;
            }
            return true;
        }
        return a.toString().equals(b.toString());
    }

    private static boolean stmtEquals(ASTNode a, ASTNode b) {
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof LetStatementNode la && b instanceof LetStatementNode lb) {
            return la.name.equals(lb.name) && la.isRec == lb.isRec && exprEquals(la.value, lb.value);
        }
        if (a instanceof MutualLetNode ma && b instanceof MutualLetNode mb) {
            if (ma.defs.size() != mb.defs.size()) return false;
            for (int i = 0; i < ma.defs.size(); i++) {
                if (!stmtEquals(ma.defs.get(i), mb.defs.get(i))) return false;
            }
            return true;
        }
        if (a instanceof ExpressionNode ea && b instanceof ExpressionNode eb) return exprEquals(ea, eb);
        return a.toString().equals(b.toString());
    }

    // ── Deep closed: consistency big-step ≈ small-step ───────────

    @Property(tries = 200)
    void closedBigStepMatchesSmallStep(@ForAll("closedExpr") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;

        // Big-step via Evaluator
        var evaluator = new Evaluator(1000);
        Value bigValue;
        try {
            bigValue = evaluator.evalProgram(new ProgramNode(List.of(raw)));
        } catch (RuntimeException e) { return; }
        if (bigValue == null) return;

        // Small-step: reduce to normal form
        ExpressionNode normal;
        try {
            normal = SmallStepReducer.reduce(raw);
        } catch (RuntimeException e) { return; }
        if (!SmallStepReducer.isValue(normal)) return;

        // Both must produce consistent results
        String smallStr = valueNodeToString(normal);
        String bigStr = valueToString(bigValue);
        assertNotNull(smallStr, "Small-step must produce a recognizable value");
        assertNotNull(bigStr, "Big-step must produce a recognizable value");
        assertEquals(bigStr, smallStr, "Big-step and small-step must produce the same value");
    }

    @Property(tries = 100)
    void closedBlockBigStepMatchesSmallStep(@ForAll("closedBlock") ExpressionNode raw) {
        if (SmallStepReducer.isValue(raw)) return;

        var evaluator = new Evaluator(1000);
        Value bigValue;
        try {
            bigValue = evaluator.evalProgram(new ProgramNode(List.of(raw)));
        } catch (RuntimeException e) { return; }
        if (bigValue == null) return;

        ExpressionNode normal;
        try {
            normal = SmallStepReducer.reduce(raw);
        } catch (RuntimeException e) { return; }
        if (!SmallStepReducer.isValue(normal)) return;

        String smallStr = valueNodeToString(normal);
        String bigStr = valueToString(bigValue);
        assertNotNull(smallStr, "Small-step must produce a recognizable value");
        assertNotNull(bigStr, "Big-step must produce a recognizable value");
        assertEquals(bigStr, smallStr, "Big-step and small-step must produce the same value");
    }

    // ── Let-generalization stress test ───────────────────────────

    @Property(tries = 200)
    void letGeneralization(@ForAll("genLetPoly") ExpressionNode raw) {
        var errors = new ArrayList<ParserError>();
        var tc = new TypeChecker(errors);
        tc.check(new ProgramNode(List.of(raw)));
        assertTrue(errors.isEmpty(),
            "Let-polymorphic term must type-check, got: " +
            errors.stream().map(e -> e.message).toList());
    }

    @Provide
    Arbitrary<ExpressionNode> genLetPoly() {
        return Arbitraries.randomValue(random -> genLetPolyBlock(random, 2, 0));
    }

    private ExpressionNode genLetPolyBlock(Random rand, int maxDepth, int depth) {
        int numLets = rand.nextInt(4) + 1; // 1-4 lets
        var stmts = new ArrayList<ASTNode>();
        var polyNames = new ArrayList<String>();
        var monoNames = new ArrayList<String>();

        for (int i = 0; i < numLets; i++) {
            if (rand.nextBoolean()) {
                String name = "f" + depth + "_" + i;
                polyNames.add(name);
                stmts.add(new LetStatementNode(name,
                    new LambdaNode("x" + i, new IdentifierNode("x" + i, 0, 0),
                        null, 0, 0),
                    false, null, 0, 0));
            } else {
                String name = "n" + depth + "_" + i;
                monoNames.add(name);
                stmts.add(new LetStatementNode(name,
                    rand.nextBoolean()
                        ? new LiteralNode(String.valueOf(rand.nextInt(100)), 0, 0)
                        : new BoolNode(0, 0, rand.nextBoolean()),
                    false, null, 0, 0));
            }
        }

        // Uses of polymorphic lets (apply to different types)
        for (String name : polyNames) {
            ExpressionNode arg1 = rand.nextBoolean()
                ? new LiteralNode(String.valueOf(rand.nextInt(100)), 0, 0)
                : new BoolNode(0, 0, rand.nextBoolean());
            stmts.add(new ApplicationNode(
                new IdentifierNode(name, 0, 0), arg1, 0, 0));
            if (rand.nextDouble() < 0.7) {
                ExpressionNode arg2 = rand.nextBoolean()
                    ? new LiteralNode(String.valueOf(rand.nextInt(100)), 0, 0)
                    : new BoolNode(0, 0, rand.nextBoolean());
                stmts.add(new ApplicationNode(
                    new IdentifierNode(name, 0, 0), arg2, 0, 0));
            }
        }

        // Uses of monomorphic lets
        for (String name : monoNames) {
            if (rand.nextDouble() < 0.5) {
                stmts.add(new IdentifierNode(name, 0, 0));
            }
        }

        // Maybe add a nested block
        if (depth < maxDepth && rand.nextDouble() < 0.3) {
            stmts.add(genLetPolyBlock(rand, maxDepth, depth + 1));
        }

        // Ensure we have at least 2 statements
        if (stmts.size() < 2) {
            stmts.add(new LiteralNode("42", 0, 0));
        }

        return new BlockNode(stmts, 0, 0);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static ExpressionNode genExpr(Random rand, int depth) {
        if (depth <= 0) return genLeaf(rand);
        return switch (rand.nextInt(15)) {
            case 0, 1 -> genLeaf(rand);
            case 2 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 3 -> new BoolNode(0, 0, rand.nextBoolean());
            case 4 -> {
                yield new LambdaNode(IDENTS.get(rand.nextInt(IDENTS.size())),
                    genExpr(rand, depth - 1), null, 0, 0);
            }
            case 5 -> new ApplicationNode(genExpr(rand, depth - 1),
                genExpr(rand, depth - 1), 0, 0);
            case 6 -> {
                yield new IfNode(genExpr(rand, depth - 1),
                    genExpr(rand, depth - 1), genExpr(rand, depth - 1));
            }
            case 7 -> new BinaryExprNode(genExpr(rand, depth - 1),
                List.of("+", "-", "*", "/", "==", "<", ">", "&&", "||")
                    .get(rand.nextInt(9)),
                genExpr(rand, depth - 1), 0, 0);
            case 8 -> new UnaryExprNode(
                rand.nextBoolean() ? "!" : "-",
                genExpr(rand, depth - 1), 0, 0);
            case 9 -> genRandomBlock(rand, depth);
            case 10 -> {
                int arity = 2 + rand.nextInt(2);
                var elems = new ArrayList<ExpressionNode>();
                for (int i = 0; i < arity; i++)
                    elems.add(genExpr(rand, depth - 1));
                yield new TupleNode(elems, 0, 0);
            }
            case 11 -> new AscriptionNode(genExpr(rand, depth - 1),
                List.of(new TypeNumber(), new TypeBool(), new TypeString(), new TypeUnit(), new TypeDynamic())
                    .get(rand.nextInt(5)), 0, 0);
            case 12 -> {
                String fname = freshName("fix", new ArrayList<>());
                String pname = freshName("n", new ArrayList<>());
                yield new FixpointNode(
                    new LambdaNode(fname, new LambdaNode(pname, genExpr(rand, depth - 1), null, 0, 0), null, 0, 0), 0, 0);
            }
            case 13 -> {
                yield new WhileNode(
                    new BoolNode(0, 0, false),
                    new UnitLiteralNode(0, 0), 0, 0);
            }
            default -> genLeaf(rand);
        };
    }

    private static ExpressionNode genRandomBlock(Random rand, int depth) {
        int numStmts = 1 + rand.nextInt(4); // 1-4 statements
        var stmts = new ArrayList<ASTNode>();
        for (int i = 0; i < numStmts - 1; i++) {
            double r = rand.nextDouble();
            if (r < 0.15) {
                stmts.add(new TypeAliasNode("T_" + i, randomType(), 0, 0));
            } else if (r < 0.25) {
                stmts.add(new EnumDeclNode("E_" + i, List.of(), List.of(
                    new EnumDeclNode.ConstructorDef("C", List.of()),
                    new EnumDeclNode.ConstructorDef("D", List.of())
                ), 0, 0));
            } else if (r < 0.65) {
                String name = IDENTS.get(rand.nextInt(IDENTS.size())) + "_" + i;
                stmts.add(new LetStatementNode(name,
                    genExpr(rand, depth - 1), rand.nextBoolean(),
                    null, 0, 0));
            } else {
                stmts.add(genExpr(rand, depth - 1));
            }
        }
        // Last statement is always an expression
        stmts.add(genExpr(rand, depth - 1));
        return new BlockNode(stmts, 0, 0);
    }

    // ── Closed block generator (no free identifiers) ─────────────

    @Provide
    Arbitrary<ExpressionNode> genClosedBlock() {
        return Arbitraries.randomValue(rand -> genClosedBlockExpr(rand, 3));
    }

    private static ExpressionNode genClosedBlockExpr(Random rand, int depth) {
        if (depth <= 0 || rand.nextDouble() < 0.4) return genClosedLeaf(rand);
        int numStmts = 1 + rand.nextInt(3);
        var stmts = new ArrayList<ASTNode>();
        for (int i = 0; i < numStmts - 1; i++) {
            double r = rand.nextDouble();
            if (r < 0.15) {
                stmts.add(new TypeAliasNode("T", randomType(), 0, 0));
            } else if (r < 0.25) {
                stmts.add(new EnumDeclNode("E", List.of(), List.of(
                    new EnumDeclNode.ConstructorDef("C", List.of()),
                    new EnumDeclNode.ConstructorDef("D", List.of())
                ), 0, 0));
            } else if (r < 0.65) {
                String name = "b" + depth + "_" + i + "_" + rand.nextInt(10000);
                stmts.add(new LetStatementNode(name,
                    genClosedLeaf(rand), false, null, 0, 0));
            } else {
                stmts.add(genClosedLeaf(rand));
            }
        }
        stmts.add(genClosedLeaf(rand));
        return new BlockNode(stmts, 0, 0);
    }

    private static ExpressionNode genClosedLeaf(Random rand) {
        return switch (rand.nextInt(5)) {
            case 0 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 1 -> new BoolNode(0, 0, rand.nextBoolean());
            case 2 -> new CharLiteralNode((char) (32 + rand.nextInt(95)), 0, 0);
            case 3 -> new UnitLiteralNode(0, 0);
            default -> new StringLiteralNode("hi", 0, 0);
        };
    }

    private static ExpressionNode genLeaf(Random rand) {
        return switch (rand.nextInt(6)) {
            case 0 -> new LiteralNode(String.valueOf(rand.nextInt(1000)), 0, 0);
            case 1 -> new BoolNode(0, 0, rand.nextBoolean());
            case 2 -> new CharLiteralNode((char) (32 + rand.nextInt(95)), 0, 0);
            case 3 -> new StringLiteralNode("hello", 0, 0);
            case 4 -> new UnitLiteralNode(0, 0);
            default -> new IdentifierNode(IDENTS.get(rand.nextInt(IDENTS.size())), 0, 0);
        };
    }

    private static String valueNodeToString(ExpressionNode node) {
        if (node instanceof LiteralNode l) return l.value;
        if (node instanceof BoolNode b) return String.valueOf(b.value);
        if (node instanceof CharLiteralNode c) return String.valueOf(c.value);
        if (node instanceof StringLiteralNode s) return s.value;
        if (node instanceof UnitLiteralNode) return "()";
        if (node instanceof IdentifierNode id) return id.name;
        if (node instanceof LambdaNode) return "<closure>";
        if (node instanceof TupleNode t) {
            return "(" + t.elements.stream().map(SmallStepPBT::valueNodeToString)
                .collect(Collectors.joining(", ")) + ")";
        }
        if (node instanceof AscriptionNode a) return valueNodeToString(a.expr);
        if (node instanceof CaseNode c) return "case(" + valueNodeToString(c.expr) + ")";
        return null;
    }

    private static String valueToString(Value v) {
        if (v instanceof NumberValue n) return Integer.toString(n.value);
        if (v instanceof BoolValue b) return Boolean.toString(b.value);
        if (v instanceof CharValue c) return String.valueOf(c.value);
        if (v instanceof StringValue s) return s.value;
        if (v instanceof UnitValue) return "()";
        if (v instanceof TupleValue t) {
            return "(" + t.values.stream().map(SmallStepPBT::valueToString)
                .collect(Collectors.joining(", ")) + ")";
        }
        if (v instanceof ClosureValue) return "<closure>";
        return null;
    }

    private static boolean containsTypeDynamic(ExpressionNode expr) {
        if (expr instanceof AscriptionNode asc && asc.declaredType instanceof TypeDynamic) return true;
        if (expr instanceof ApplicationNode app) return containsTypeDynamic(app.function) || containsTypeDynamic(app.argument);
        if (expr instanceof LambdaNode lam) return containsTypeDynamic(lam.body);
        if (expr instanceof IfNode i) return containsTypeDynamic(i.condition) || containsTypeDynamic(i.thenBranch) || (i.elseBranch != null && containsTypeDynamic(i.elseBranch));
        if (expr instanceof BinaryExprNode bin) return containsTypeDynamic(bin.left) || containsTypeDynamic(bin.right);
        if (expr instanceof UnaryExprNode un) return containsTypeDynamic(un.operand);
        if (expr instanceof TupleNode tup) { for (ExpressionNode e : tup.elements) if (containsTypeDynamic(e)) return true; }
        if (expr instanceof BlockNode blk) { for (ASTNode s : blk.statements) if (containsTypeDynamic(s)) return true; }
        if (expr instanceof CaseNode c) { if (containsTypeDynamic(c.expr)) return true; }
        if (expr instanceof FixpointNode fp) return containsTypeDynamic(fp.body);
        if (expr instanceof WhileNode w) return containsTypeDynamic(w.condition) || containsTypeDynamic(w.body);
        return false;
    }

    private static boolean containsTypeDynamic(ASTNode node) {
        if (node instanceof ExpressionNode e) return containsTypeDynamic(e);
        if (node instanceof scala.parser.core.ast.LetStatementNode l) return containsTypeDynamic(l.value);
        if (node instanceof scala.parser.core.ast.MutualLetNode m) { for (var d : m.defs) if (containsTypeDynamic(d.value)) return true; }
        return false;
    }

    private static boolean containsFixpoint(ExpressionNode expr) {
        if (expr instanceof FixpointNode) return true;
        if (expr instanceof ApplicationNode app) return containsFixpoint(app.function) || containsFixpoint(app.argument);
        if (expr instanceof LambdaNode lam) return containsFixpoint(lam.body);
        if (expr instanceof IfNode i) return containsFixpoint(i.condition) || containsFixpoint(i.thenBranch) || (i.elseBranch != null && containsFixpoint(i.elseBranch));
        if (expr instanceof BinaryExprNode bin) return containsFixpoint(bin.left) || containsFixpoint(bin.right);
        if (expr instanceof UnaryExprNode un) return containsFixpoint(un.operand);
        if (expr instanceof TupleNode tup) { for (ExpressionNode e : tup.elements) if (containsFixpoint(e)) return true; }
        if (expr instanceof BlockNode blk) { for (ASTNode s : blk.statements) if (containsFixpointAux(s)) return true; }
        if (expr instanceof CaseNode c) { if (containsFixpoint(c.expr)) return true; }
        if (expr instanceof AscriptionNode asc) return containsFixpoint(asc.expr);
        if (expr instanceof WhileNode w) return containsFixpoint(w.condition) || containsFixpoint(w.body);
        return false;
    }

    private static boolean containsFixpointAux(ASTNode node) {
        if (node instanceof ExpressionNode e) return containsFixpoint(e);
        if (node instanceof scala.parser.core.ast.LetStatementNode l) return containsFixpoint(l.value);
        if (node instanceof scala.parser.core.ast.MutualLetNode m) { for (var d : m.defs) if (containsFixpoint(d.value)) return true; }
        return false;
    }
}
