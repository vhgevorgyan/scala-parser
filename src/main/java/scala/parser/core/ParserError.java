package scala.parser.core;

public class ParserError {

    public final String message;
    public final int line;
    public final int column;
    public final int length;

    public ParserError(String message, int line, int column) {
        this(message, line, column, 1);
    }

    public ParserError(String message, int line, int column, int length) {
        this.message = message;
        this.line = line;
        this.column = column;
        this.length = length;
    }
}
