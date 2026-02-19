package scala.parser.lsp;

import java.util.List;

import scala.parser.core.ParserResult;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

public class CompilerPipeline {

    public ParserResult analyze(String text) {
        Tokenizer tokenizer = new Tokenizer(text);
        List<Token> tokens = tokenizer.tokenize();
        Parser parser = new Parser(tokens);
        ParserResult result = parser.parse();
        var typeChecker = new TypeChecker(result.errors);
        typeChecker.check(result.ast);

        return result;
    }
}
