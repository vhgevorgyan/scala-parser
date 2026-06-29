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
        Desugarer.desugarExpr(ifn);
        assertNotNull(ifn.elseBranch);
        assertInstanceOf(UnitLiteralNode.class, ifn.elseBranch);
    }

    @Test
    void desugarIfThenElseLeftUnchanged() {
        var cond = new BoolNode(1, 1, true);
        var thenBr = new LiteralNode("1", 1, 1);
        var elseBr = new LiteralNode("2", 1, 1);
        var ifn = new IfNode(cond, thenBr, elseBr);
        Desugarer.desugarExpr(ifn);
        assertSame(elseBr, ifn.elseBranch);
    }
}
