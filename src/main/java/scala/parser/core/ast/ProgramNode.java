package scala.parser.core.ast;

import java.util.List;

public class ProgramNode extends ASTNode {

    public final List<ASTNode> statements;

    public ProgramNode(List<ASTNode> statements) {
        super(0, 0); 
        this.statements = statements;
    }
}

