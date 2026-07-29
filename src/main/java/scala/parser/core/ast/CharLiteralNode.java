package scala.parser.core.ast;

public class CharLiteralNode extends ExpressionNode {
    public final char value;

    public CharLiteralNode(char value, int line, int column) {
        super(line, column);
        this.value = value;
    }
}
