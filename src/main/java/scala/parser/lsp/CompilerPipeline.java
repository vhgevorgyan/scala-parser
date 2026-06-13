package scala.parser.lsp;

import java.util.List;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;
import scala.parser.core.interpreter.EvaluationResult;
import scala.parser.core.interpreter.Evaluator;
import scala.parser.core.interpreter.Value;

public class CompilerPipeline {

    public EvaluationResult analyze(String text) {
        Tokenizer tokenizer = new Tokenizer(text);
        List<Token> tokens = tokenizer.tokenize();
        Parser parser = new Parser(tokens);
        ParserResult result = parser.parse();

        TypeChecker typeChecker = new TypeChecker(result.errors);
        typeChecker.check(result.ast);

        Value value = null;
        if (result.errors.isEmpty()) {
            try {
                Evaluator evaluator = new Evaluator();
                value = evaluator.evalProgram(result.ast);
            } catch (RuntimeException e) {
                result.errors.add(new ParserError("Runtime error: " + e.getMessage(), 0, 0));
            }
        }

        return new EvaluationResult(result, value, typeChecker.getInferredTypes());
    }
}
