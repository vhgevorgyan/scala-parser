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

    public static record CaseBranch(String constructor, List<String> bindings, ExpressionNode body) {}
}
