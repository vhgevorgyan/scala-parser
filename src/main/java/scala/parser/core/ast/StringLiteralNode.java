package scala.parser.core.ast;

public class StringLiteralNode extends ExpressionNode {

    public final String value;

    public StringLiteralNode(String value, int line, int column) {
        super(line, column);
        this.value = value;
    }
}
