package scala.parser.core.ast;

public class LiteralNode extends ExpressionNode {

    public final String value;

    public LiteralNode(String value, int line, int column) {
        super(line, column);
        this.value = value;
    }

}

