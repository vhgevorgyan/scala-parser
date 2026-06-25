package scala.parser.core.ast;

import scala.parser.core.types.Type;

public class LambdaNode extends ExpressionNode {

    public final String param;
    public final ExpressionNode body;
    public final Type declaredType;

    public LambdaNode(String param, ExpressionNode body, int line, int column) {
        this(param, body, null, line, column);
    }

    public LambdaNode(String param, ExpressionNode body, Type declaredType, int line, int column) {
        super(line, column);
        this.param = param;
        this.body = body;
        this.declaredType = declaredType;
    }
}
