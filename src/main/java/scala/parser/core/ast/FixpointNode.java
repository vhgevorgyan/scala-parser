package scala.parser.core.ast;

public class FixpointNode extends ExpressionNode {

    public final ExpressionNode body;

    public FixpointNode(ExpressionNode body, int line, int column) {
        super(line, column);
        this.body = body;
    }
}
