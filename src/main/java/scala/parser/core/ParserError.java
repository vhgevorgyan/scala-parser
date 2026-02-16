package scala.parser.core;

public class ParserError {

    public final String message;
    public final int line;
    public final int column;

    public ParserError(String message, int line, int column) {
        this.message = message;
        this.line = line;
        this.column = column;
    }
}
