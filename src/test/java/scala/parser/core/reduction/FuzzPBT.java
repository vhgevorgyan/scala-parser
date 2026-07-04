package scala.parser.core.reduction;

import java.util.*;
import net.jqwik.api.*;
import scala.parser.core.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;

public class FuzzPBT {

    @Property(tries = 5000)
    void tokenizerNeverThrows(@ForAll("randomBytes") byte[] bytes) {
        try {
            String src = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            new Tokenizer(src).tokenize();
        } catch (Exception e) {
            throw new RuntimeException("Tokenizer crashed on input: " + bytes.length + " bytes", e);
        }
    }

    @Property(tries = 2000)
    void parserNeverThrows(@ForAll("randomBytes") byte[] bytes) {
        try {
            String src = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            var tokens = new Tokenizer(src).tokenize();
            new Parser(tokens).parse();
        } catch (Exception e) {
            throw new RuntimeException("Parser crashed on input: " + bytes.length + " bytes", e);
        }
    }

    @Provide
    Arbitrary<byte[]> randomBytes() {
        return Arbitraries.integers().between(1, 200)
            .flatMap(size -> Arbitraries.bytes().array(byte[].class).ofSize(size));
    }

    @Property(tries = 500)
    void fullPipelineNeverThrows(@ForAll("printableStrings") String src) {
        try {
            var tokens = new Tokenizer(src).tokenize();
            var result = new Parser(tokens).parse();
            if (!result.errors.isEmpty()) return;
            var errors = new ArrayList<ParserError>();
            new scala.parser.core.types.TypeChecker(errors).check(result.ast);
        } catch (Exception e) {
            throw new RuntimeException("Pipeline crashed on input: " + src.substring(0, Math.min(80, src.length())), e);
        }
    }

    @Provide
    Arbitrary<String> printableStrings() {
        return Arbitraries.strings()
            .withCharRange((char) 32, (char) 126)
            .ofMinLength(1).ofMaxLength(100);
    }
}
