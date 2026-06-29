package scala.parser.core.semantic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import scala.parser.core.ast.*;

class DesugarerTest {

    @Test
    void desugarIfThenWithoutElse() {
        // if true then 1 → if true then 1 else ()
        var cond = new BoolNode(1, 1, true);
        var thenBr = new LiteralNode("1", 1, 1);
        var ifn = new IfNode(cond, thenBr, null);
        assertEquals(null, ifn.elseBranch);
        ExpressionNode result = Desugarer.desugarExpr(ifn);
        assertInstanceOf(IfNode.class, result);
        assertNotNull(((IfNode) result).elseBranch);
        assertInstanceOf(UnitLiteralNode.class, ((IfNode) result).elseBranch);
    }

    @Test
    void desugarIfThenElseLeftUnchanged() {
        var cond = new BoolNode(1, 1, true);
        var thenBr = new LiteralNode("1", 1, 1);
        var elseBr = new LiteralNode("2", 1, 1);
        var ifn = new IfNode(cond, thenBr, elseBr);
        ExpressionNode result = Desugarer.desugarExpr(ifn);
        assertSame(ifn, result);
    }
}
