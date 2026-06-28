package scala.parser.core.ast;

import java.util.List;

public class MutualLetNode extends ASTNode {

    public final List<LetStatementNode> defs;

    public MutualLetNode(List<LetStatementNode> defs, int line, int column) {
        super(line, column);
        this.defs = defs;
    }
}
