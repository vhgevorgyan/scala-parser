package scala.parser.core.ast;

import scala.parser.core.types.Type;

public class LetStatementNode extends ASTNode {

    public final String name;
    public final ExpressionNode value;
    public final boolean isRec;
    public final Type declaredType;

    public LetStatementNode(String name, ExpressionNode value, int line, int column) {
        this(name, value, false, null, line, column);
    }

    public LetStatementNode(String name, ExpressionNode value, boolean isRec, Type declaredType, int line, int column) {
        super(line, column);
        this.name = name;
        this.value = value;
        this.isRec = isRec;
        this.declaredType = declaredType;
    }
}
