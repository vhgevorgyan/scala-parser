package scala.parser.core.ast;

public class AssignNode extends ExpressionNode {
    public final String name;
    public final ExpressionNode value;

    public AssignNode(String name, ExpressionNode value, int line, int column) {
        super(line, column);
        this.name = name;
        this.value = value;
    }
}
