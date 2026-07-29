package scala.parser.core.ast;

public class WhileNode extends ExpressionNode {
    public final ExpressionNode condition;
    public final ExpressionNode body;

    public WhileNode(ExpressionNode condition, ExpressionNode body, int line, int column) {
        super(line, column);
        this.condition = condition;
        this.body = body;
    }
}
