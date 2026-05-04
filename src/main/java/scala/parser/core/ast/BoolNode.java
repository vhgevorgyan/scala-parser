package scala.parser.core.ast;

public class BoolNode extends ExpressionNode {
    public final boolean value;

    public BoolNode(int line, int column, boolean value) {
        super(line, column);
        this.value = value;
    }
}
