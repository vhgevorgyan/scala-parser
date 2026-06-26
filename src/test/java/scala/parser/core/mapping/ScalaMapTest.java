package scala.parser.core.mapping;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import scala.parser.core.interpreter.*;
import scala.parser.lsp.CompilerPipeline;

/**
 * Systematische Tests der Scala→λ-Core-Abbildung.
 * Jeder Test deckt exakt ein Scala-Konstrukt und seine λ-Erweiterung ab.
 */
public class ScalaMapTest {

    private EvaluationResult analyze(String src) {
        return new CompilerPipeline().analyze(src);
    }

    private Value eval(String src) {
        EvaluationResult r = analyze(src);
        String errMsg = r.parserResult().errors.stream()
            .map(e -> e.line + ":" + e.column + " " + e.message)
            .collect(java.util.stream.Collectors.joining("; "));
        assertTrue(r.parserResult().errors.isEmpty(), "errors: " + errMsg);
        return r.value();
    }

    // ── 1. Basis: Lambda + Application + Identifier ──────────────

    @Test
    void lambdaIdentity() {
        Value v = eval("((x: Int) => x)(42)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void lambdaMultipleArgs() {
        Value v = eval("((x: Int) => (y: Int) => x + y)(3)(4)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(7, ((NumberValue) v).value);
    }

    @Test
    void identifierReference() {
        Value v = eval("((x: Int) => x + x)(5)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(10, ((NumberValue) v).value);
    }

    // ── 2. Let-Bindings: val / def ────────────────────────────────

    @Test
    void valBinding() {
        Value v = eval("val x = 42; x + 1");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(43, ((NumberValue) v).value);
    }

    @Test
    void defBinding() {
        Value v = eval("def double(x: Int): Int = x + x; double(21)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void recursiveDef() {
        Value v = eval("def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1); fact(5)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(120, ((NumberValue) v).value);
    }

    // ── 3. If / Unit ──────────────────────────────────────────────

    @Test
    void ifThenElse() {
        Value v = eval("if (true) 1 else 2");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void ifWithoutElse() {
        Value v = eval("if (false) 42");
        assertInstanceOf(UnitValue.class, v);
    }

    @Test
    void unitLiteral() {
        Value v = eval("()");
        assertInstanceOf(UnitValue.class, v);
    }

    // ── 4. Blocks ────────────────────────────────────────────────

    @Test
    void blockWithMultipleStatements() {
        Value v = eval("{ val x = 1; val y = 2; x + y }");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(3, ((NumberValue) v).value);
    }

    @Test
    void blockAsExpression() {
        Value v = eval("((x: Int) => { val y = x + 1; y * 2 })(5)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(12, ((NumberValue) v).value);
    }

    // ── 5. Literale: Int, Bool, String ───────────────────────────

    @Test
    void intLiteral() {
        Value v = eval("42");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void boolLiteralTrue() {
        Value v = eval("true");
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void boolLiteralFalse() {
        Value v = eval("false");
        assertInstanceOf(BoolValue.class, v);
        assertFalse(((BoolValue) v).value);
    }

    @Test
    void stringLiteral() {
        Value v = eval("\"hello\"");
        assertInstanceOf(StringValue.class, v);
        assertEquals("hello", ((StringValue) v).value);
    }

    // ── 6. Arithmetik + Vergleich + Bool-Operatoren ──────────────

    @Test
    void addition() {
        assertEquals(5, ((NumberValue) eval("2 + 3")).value);
    }

    @Test
    void subtraction() {
        assertEquals(1, ((NumberValue) eval("3 - 2")).value);
    }

    @Test
    void multiplication() {
        assertEquals(6, ((NumberValue) eval("2 * 3")).value);
    }

    @Test
    void division() {
        assertEquals(3, ((NumberValue) eval("6 / 2")).value);
    }

    @Test
    void unaryMinus() {
        assertEquals(-5, ((NumberValue) eval("-5")).value);
    }

    @Test
    void comparisonLessThan() {
        assertTrue(((BoolValue) eval("3 < 5")).value);
    }

    @Test
    void comparisonGreaterThan() {
        assertTrue(((BoolValue) eval("5 > 3")).value);
    }

    @Test
    void comparisonEquals() {
        assertTrue(((BoolValue) eval("3 == 3")).value);
    }

    @Test
    void comparisonNotEquals() {
        assertTrue(((BoolValue) eval("3 != 4")).value);
    }

    @Test
    void booleanAnd() {
        assertFalse(((BoolValue) eval("true && false")).value);
    }

    @Test
    void booleanOr() {
        assertTrue(((BoolValue) eval("true || false")).value);
    }

    @Test
    void booleanNot() {
        assertFalse(((BoolValue) eval("!true")).value);
    }

    // ── 7. Type Ascription ───────────────────────────────────────

    @Test
    void typeAscription() {
        Value v = eval("(42: Int)");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    // ── 8. Type Alias ────────────────────────────────────────────

    @Test
    void typeAlias() {
        Value v = eval("type Seconds = Int; val x: Seconds = 42; x");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    // ── 9. Tupel ─────────────────────────────────────────────────

    @Test
    void tupleLiteral() {
        Value v = eval("(1, 2)");
        assertInstanceOf(TupleValue.class, v);
        TupleValue tv = (TupleValue) v;
        assertEquals(2, tv.values.size());
        assertEquals(1, ((NumberValue) tv.values.get(0)).value);
        assertEquals(2, ((NumberValue) tv.values.get(1)).value);
    }

    @Test
    void tupleWithMixedTypes() {
        Value v = eval("(1, true)");
        assertInstanceOf(TupleValue.class, v);
        TupleValue tv = (TupleValue) v;
        assertEquals(2, tv.values.size());
        assertEquals(1, ((NumberValue) tv.values.get(0)).value);
        assertTrue(((BoolValue) tv.values.get(1)).value);
    }

    // ── 10. ADTs: enum + Constructor ────────────────────────────

    @Test
    void adtEnumWithNoArgConstructor() {
        Value v = eval("enum Color { case Red; case Blue }\nval x = Red");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Red", cv.name);
        assertTrue(cv.args.isEmpty());
    }

    @Test
    void adtEnumWithOneArg() {
        Value v = eval("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Just", cv.name);
        assertEquals(1, cv.args.size());
        assertEquals(42, ((NumberValue) cv.args.get(0)).value);
    }

    // ── 11. Pattern Matching: Wildcard, Literal, Constructor ────

    @Test
    void matchWildcard() {
        Value v = eval("42 match { case _ => 1 }");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void matchLiteral() {
        Value v = eval("""
            0 match {
              case 0 => "zero"
              case _ => "other"
            }
            """);
        assertInstanceOf(StringValue.class, v);
        assertEquals("zero", ((StringValue) v).value);
    }

    @Test
    void matchConstructor() {
        Value v = eval("""
            enum Maybe[A] { case Nothing; case Just(x: A) }
            val x = Just(42)
            x match {
              case Nothing => 0
              case Just(n) => n
            }
            """);
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    // ── 12. Listen: Nil, Cons, List-Literale ─────────────────────

    @Test
    void listNil() {
        Value v = eval("Nil");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Nil", cv.name);
    }

    @Test
    void listCons() {
        // Curried application: Cons takes 2 args (A, List[A])
        Value v = eval("Cons(1)(Cons(2)(Nil))");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Cons", cv.name);
        assertEquals(2, cv.args.size());
        assertEquals(1, ((NumberValue) cv.args.get(0)).value);
        assertInstanceOf(ConstructorValue.class, cv.args.get(1));
    }

    @Test
    void listLiteralSugar() {
        // [1, 2, 3] desugared to Cons(1)(Cons(2)(Cons(3)(Nil)))
        Value v = eval("[1, 2, 3]");
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("Cons", cv.name);
        assertEquals(2, cv.args.size());
        assertEquals(1, ((NumberValue) cv.args.get(0)).value);
    }

    // ── 13. Range ────────────────────────────────────────────────

    @Test
    void rangeLiteral() {
        Value v = eval("1..5");
        assertInstanceOf(TupleValue.class, v);
        TupleValue tv = (TupleValue) v;
        assertEquals(2, tv.values.size());
        assertEquals(1, ((NumberValue) tv.values.get(0)).value);
        assertEquals(5, ((NumberValue) tv.values.get(1)).value);
    }

    // ── 14. Unicode-Operatoren ───────────────────────────────────

    @Test
    void unicodeGreaterOrEqual() {
        assertTrue(((BoolValue) eval("5 ≥ 3")).value);
        assertFalse(((BoolValue) eval("2 ≥ 3")).value);
    }

    @Test
    void unicodeLessOrEqual() {
        assertTrue(((BoolValue) eval("3 ≤ 5")).value);
        assertFalse(((BoolValue) eval("5 ≤ 3")).value);
    }

    @Test
    void unicodeNotEqual() {
        assertTrue(((BoolValue) eval("3 ≠ 4")).value);
        assertFalse(((BoolValue) eval("3 ≠ 3")).value);
    }

    // ── 15. Infix-Constructor :: ─────────────────────────────────

    @Test
    void infixConsInPattern() {
        // :: nur in Pattern-Position als Sugar, benötigt enum-Deklaration
        Value v = eval("""
            enum MyList {
              case MyNil
              case ::(x: MyList, xs: MyList)
            }
            val xs = MyNil
            xs match {
              case x :: xs => x
              case MyNil => MyNil
            }
            """);
        assertInstanceOf(ConstructorValue.class, v);
        ConstructorValue cv = (ConstructorValue) v;
        assertEquals("MyNil", cv.name);
    }

    // ── 16. Typ-Polymorphismus (let-generalization) ──────────────

    @Test
    void letPolyTopLevel() {
        // val id = (x) => x; id(42); id(true)
        Value v = eval("""
            val id = (x) => x
            id(42)
            id(true)
            """);
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void letPolyInBlock() {
        Value v = eval("""
            val result = {
              val id = (x) => x
              id(42)
              id(true)
            }
            result
            """);
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void letPolyBlockBothUses() {
        Value v = eval("""
            val result = {
              val id = (x) => x
              (id(42), id("hello"))
            }
            result
            """);
        assertInstanceOf(TupleValue.class, v);
    }

    @Test
    void letPolyBlockNested() {
        Value v = eval("""
            val r = {
              val id = (x) => x
              {
                id(42)
                id(true)
              }
            }
            r
            """);
        assertInstanceOf(BoolValue.class, v);
        assertTrue(((BoolValue) v).value);
    }

    @Test
    void letPolyBlockWithNonPoly() {
        Value v = eval("""
            val r = {
              val x = 42
              val id = (y) => y
              id(x)
            }
            r
            """);
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void letPolyBlockReturnsId() {
        // Block returns the polymorphic id itself
        Value v = eval("""
            val id = (x) => x
            val f = { id }
            f(42)
            """);
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void letPolyBlockShadow() {
        // Shadowing redefines id; the second definition is used
        Value v = eval("""
            val r = {
              val id = (x) => x
              val id = (y) => y + 1
              id(41)
            }
            r
            """);
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void letPolyRecDefInBlock() {
        Value v = eval("""
            val result = {
              def loop(x: Int): Int = if (x ≤ 0) 0 else loop(x - 1)
              loop(5)
            }
            result
            """);
        assertInstanceOf(NumberValue.class, v);
        assertEquals(0, ((NumberValue) v).value);
    }

    // ── 17. Gesamtabdeckung: all_features.sp ─────────────────────

    @Test
    void allFeaturesDemo() {
        // type alias + def + recursion + unicode + if-without-else + ascription
        Value v = eval("""
            type Seconds = Int
            def wait(t: Seconds): Unit = if (t ≤ 0) () else wait(t - 1)
            wait(3)
            """);
        assertInstanceOf(UnitValue.class, v);
    }

    @Test
    void tupleInLet() {
        Value v = eval("val p = (1, true); p");
        assertInstanceOf(TupleValue.class, v);
    }

    @Test
    void ascriptionInBlock() {
        Value v = eval("{ val x = (42: Int); x }");
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void rangeInVariable() {
        Value v = eval("val r = 1..10; r");
        assertInstanceOf(TupleValue.class, v);
        TupleValue tv = (TupleValue) v;
        assertEquals(1, ((NumberValue) tv.values.get(0)).value);
        assertEquals(10, ((NumberValue) tv.values.get(1)).value);
    }
}
