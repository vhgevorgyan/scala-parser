package scala.parser.core.ast;

import java.util.List;

public class TupleNode extends ExpressionNode {
    public final List<ExpressionNode> elements;

    public TupleNode(List<ExpressionNode> elements, int line, int column) {
        super(line, column);
        this.elements = elements;
    }
}
