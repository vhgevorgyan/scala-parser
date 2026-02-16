package scala.parser.core.ast;

public class IdentifierNode extends ExpressionNode {

    public final String name;

    public IdentifierNode(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }
}
