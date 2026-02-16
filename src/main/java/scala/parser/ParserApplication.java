package scala.parser;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import scala.parser.core.lexer.Tokenizer;
import scala.parser.lsp.LanguageServerLauncher;

@SpringBootApplication
public class ParserApplication {

    public static void main(String[] args) throws Exception {
        String code = "let x = 5 + 3";
        Tokenizer t = new Tokenizer(code);
        var tokens = t.tokenize();
        tokens.forEach(System.out::println);
        LanguageServerLauncher.start();
    }

}
