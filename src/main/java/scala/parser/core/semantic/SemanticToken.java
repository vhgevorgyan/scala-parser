package scala.parser.core.semantic;

public class SemanticToken {
    public final int line;
    public final int column;
    public final int length;
    public final String type;

    public SemanticToken(int line, int column, int length, String type) {
        this.line = line;
        this.column = column;
        this.length = length;
        this.type = type;
    }
}