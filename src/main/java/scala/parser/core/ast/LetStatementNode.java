package scala.parser.core.ast;

public class LetStatementNode extends ASTNode {

    public final String name;
    public final ExpressionNode value;

    public LetStatementNode(String name, ExpressionNode value, int line, int column) {
        super(line, column);
        this.name = name;
        this.value = value;
    }
}
