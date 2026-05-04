package scala.parser.core.ast;

public class LambdaNode extends ExpressionNode {

    public final String param;
    public final ExpressionNode body;

    public LambdaNode(String param, ExpressionNode body, int line, int column) {
        super(line, column);
        this.param = param;
        this.body = body;
    }
}
