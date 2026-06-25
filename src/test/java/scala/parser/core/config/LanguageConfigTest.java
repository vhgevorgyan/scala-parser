package scala.parser.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import scala.parser.core.interpreter.*;
import scala.parser.lsp.CompilerPipeline;

public class LanguageConfigTest {

    private CompilerPipeline pipeline(LanguageConfig config) {
        return new CompilerPipeline(config);
    }

    // ── Bottom-up: Pure λ-calculus ───────────────────────────────

    @Test
    void pureLambdaAcceptsIdentity() {
        var c = LanguageConfig.upTo(LanguageFeature.LET);
        // Die Reihenfolge in LanguageFeature.values() beginnt mit LET
        // upTo(LET) = {LET} → kein IF_UNIT
        // Also: basale λ-Terme (Identifier, Lambda, Application) sind immer an
        Value v = pipeline(c).analyze("((x: Int) => x)(42)").value();
        assertInstanceOf(NumberValue.class, v);
        assertEquals(42, ((NumberValue) v).value);
    }

    // ── Jede Erweiterung deaktivieren ────────────────────────────

    @Test
    void letDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.LET, false);
        var r = pipeline(c).analyze("val x = 42");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("let"));
    }

    @Test
    void ifUnitDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.IF_UNIT, false);
        var r = pipeline(c).analyze("if (true) 1 else 2");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("if_unit"));
    }

    @Test
    void adtDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.ADT, false);
        var r = pipeline(c).analyze("enum Bool { case True; case False }");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("adt"));
    }

    @Test
    void patternMatchDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.PATTERN_MATCH, false);
        var r = pipeline(c).analyze("42 match { case _ => 0 }");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("pattern_match"));
    }

    @Test
    void arithmeticDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.ARITHMETIC, false);
        var r = pipeline(c).analyze("1 + 2");
        assertFalse(r.parserResult().errors.isEmpty());
    }

    @Test
    void tupleDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.TUPLE, false);
        var r = pipeline(c).analyze("(1, 2)");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("tuple"));
    }

    @Test
    void ascriptionDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.ASCRIPTION, false);
        var r = pipeline(c).analyze("(42: Int)");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("ascription"));
    }

    @Test
    void typeAliasDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.TYPE_ALIAS, false);
        var r = pipeline(c).analyze("type Seconds = Int");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("type_alias"));
    }

    @Test
    void listSugarDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.LIST_SUGAR, false);
        var r = pipeline(c).analyze("[1, 2, 3]");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("list_sugar"));
    }

    @Test
    void rangeSugarDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.RANGE_SUGAR, false);
        var r = pipeline(c).analyze("1..10");
        assertFalse(r.parserResult().errors.isEmpty());
        assertTrue(r.parserResult().errors.get(0).message.contains("range_sugar"));
    }

    @Test
    void infixConsDisabled() {
        var c = LanguageConfig.allEnabled().with(LanguageFeature.INFIX_CONS, false);
        // :: in pattern context sollte jetzt einen Fehler geben
        var r = pipeline(c).analyze("""
            enum MyList { case MyNil; case ::(x: MyList, xs: MyList) }
            val xs = MyNil
            xs match { case x :: xs => x }
            """);
        assertFalse(r.parserResult().errors.isEmpty());
    }

    // ── Bottom-up: Pro Layer ein Test ────────────────────────────

    @Test
    void letBindingWorks() {
        var c = LanguageConfig.allEnabled();
        Value v = pipeline(c).analyze("val x = 42; x").value();
        assertEquals(42, ((NumberValue) v).value);
    }

    @Test
    void ifWorksWhenEnabled() {
        Value v = pipeline(LanguageConfig.allEnabled())
            .analyze("if (true) 1 else 2").value();
        assertEquals(1, ((NumberValue) v).value);
    }

    @Test
    void tupleWorksWhenEnabled() {
        Value v = pipeline(LanguageConfig.allEnabled())
            .analyze("(1, 2)").value();
        assertInstanceOf(TupleValue.class, v);
    }

    @Test
    void listSugarWorksWhenEnabled() {
        Value v = pipeline(LanguageConfig.allEnabled())
            .analyze("[1, 2, 3]").value();
        assertInstanceOf(ConstructorValue.class, v);
    }

    @Test
    void rangeWorksWhenEnabled() {
        Value v = pipeline(LanguageConfig.allEnabled())
            .analyze("1..5").value();
        assertInstanceOf(TupleValue.class, v);
    }

    // ── Unicode operators ───────────────────────────────────────

    @Test
    void unicodeOpsWorkWhenArithmeticEnabled() {
        Value v = pipeline(LanguageConfig.allEnabled())
            .analyze("3 ≥ 1").value();
        assertTrue(((BoolValue) v).value);
    }

    // ── Config immutability ─────────────────────────────────────

    @Test
    void configWithDoesNotMutate() {
        var all = LanguageConfig.allEnabled();
        var restricted = all.with(LanguageFeature.TUPLE, false);
        assertTrue(all.isEnabled(LanguageFeature.TUPLE));
        assertFalse(restricted.isEnabled(LanguageFeature.TUPLE));
    }

    @Test
    void upToIncludesFeatures() {
        // LET ist das erste Feature in der Enum-Reihenfolge
        var c = LanguageConfig.upTo(LanguageFeature.LET);
        assertTrue(c.isEnabled(LanguageFeature.LET));
        assertFalse(c.isEnabled(LanguageFeature.IF_UNIT));
    }
}
