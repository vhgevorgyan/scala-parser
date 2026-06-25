package scala.parser.core.ast;

import scala.parser.core.types.Type;

public class AscriptionNode extends ExpressionNode {
    public final ExpressionNode expr;
    public final Type declaredType;

    public AscriptionNode(ExpressionNode expr, Type declaredType, int line, int column) {
        super(line, column);
        this.expr = expr;
        this.declaredType = declaredType;
    }
}
