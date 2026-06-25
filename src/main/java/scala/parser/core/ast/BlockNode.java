package scala.parser.core.ast;

import java.util.List;

public class BlockNode extends ExpressionNode {

    public final List<ASTNode> statements;

    public BlockNode(List<ASTNode> statements, int line, int column) {
        super(line, column);
        this.statements = statements;
    }
}
