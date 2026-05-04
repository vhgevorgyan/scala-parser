package scala.parser.core;

import java.util.List;
import scala.parser.core.ast.ProgramNode;

public class ParserResult {

    public final ProgramNode ast;
    public final List<ParserError> errors;

    public ParserResult(ProgramNode ast, List<ParserError> errors) {
        this.ast = ast;
        this.errors = errors;
    }
}
