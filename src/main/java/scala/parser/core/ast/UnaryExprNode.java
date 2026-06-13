package scala.parser.core.ast;

public class UnaryExprNode extends ExpressionNode {

    public final String operator;
    public final ExpressionNode operand;

    public UnaryExprNode(String operator, ExpressionNode operand, int line, int column) {
        super(line, column);
        this.operator = operator;
        this.operand = operand;
    }
}
