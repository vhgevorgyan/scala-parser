package scala.parser.core.interpreter;

import scala.parser.core.ast.ExpressionNode;
import scala.parser.core.ast.LambdaNode;

public final class ClosureValue extends Value {
    public final String param;
    public final ExpressionNode body;
    public final Env env;

    public ClosureValue(String param, ExpressionNode body, Env env) {
        this.param = param;
        this.body = body;
        this.env = env;
    }

    public static ClosureValue fromLambda(LambdaNode node, Env env) {
        return new ClosureValue(node.param, node.body, env);
    }

    @Override
    public String toString() {
        return "<closure>";
    }
}
