package scala.parser.core.ast;

public class IfNode extends ExpressionNode {

    public final ExpressionNode condition, thenBranch;
    public final ExpressionNode elseBranch; // null if no else clause

    public IfNode(ExpressionNode c, ExpressionNode t, ExpressionNode e) {
        super(c.line, c.column);
        this.condition = c;
        this.thenBranch = t;
        this.elseBranch = e;
    }
}
