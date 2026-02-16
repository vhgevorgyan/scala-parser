package scala.parser.core.ast;

public class BinaryExprNode extends ExpressionNode {

    public final ExpressionNode left;
    public final String operator;
    public final ExpressionNode right;

    public BinaryExprNode(ExpressionNode left, String operator, ExpressionNode right, int line, int column) {
        super(line, column);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}
