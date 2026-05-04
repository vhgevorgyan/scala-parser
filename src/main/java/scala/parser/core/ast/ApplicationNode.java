package scala.parser.core.ast;

public class ApplicationNode extends ExpressionNode {
    public final ExpressionNode function;
    public final ExpressionNode argument;

    public ApplicationNode(ExpressionNode function, ExpressionNode argument, int line, int column) {
        super(line, column);
        this.function = function;
        this.argument = argument;
    }
}

