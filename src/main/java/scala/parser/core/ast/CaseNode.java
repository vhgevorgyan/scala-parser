package scala.parser.core.ast;

import java.util.List;

public class CaseNode extends ExpressionNode {

    public final ExpressionNode expr;
    public final List<CaseBranch> branches;

    public CaseNode(ExpressionNode expr, List<CaseBranch> branches, int line, int column) {
        super(line, column);
        this.expr = expr;
        this.branches = branches;
    }

    public static record Binding(String name, int line, int column) {}
    public static record CaseBranch(String constructor, List<Binding> bindings, ExpressionNode body, ExpressionNode literalPattern, String asName, ExpressionNode guard) {
        public CaseBranch(String constructor, List<Binding> bindings, ExpressionNode body) {
            this(constructor, bindings, body, null, null, null);
        }
        public CaseBranch(String constructor, List<Binding> bindings, ExpressionNode body, ExpressionNode literalPattern) {
            this(constructor, bindings, body, literalPattern, null, null);
        }
    }
}
