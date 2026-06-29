package scala.parser.core.semantic;

import scala.parser.core.ast.*;

/** Desugars Scala surface syntax into λ-Core: normalizes if-then (no else) → if-then-else (), etc. */
public class Desugarer {

    public static ProgramNode desugar(ProgramNode program) {
        for (ASTNode stmt : program.statements) {
            if (stmt instanceof ExpressionNode e) desugarExpr(e);
            if (stmt instanceof LetStatementNode let) desugarExpr(let.value);
        }
        return program;
    }

    public static ExpressionNode desugarExpr(ExpressionNode node) {
        if (node instanceof IfNode i) {
            if (i.elseBranch == null) {
                i.elseBranch = new UnitLiteralNode(i.line, i.column + 2);
            }
            desugarExpr(i.condition);
            desugarExpr(i.thenBranch);
            desugarExpr(i.elseBranch);
        }
        if (node instanceof BinaryExprNode bin) {
            desugarExpr(bin.left);
            desugarExpr(bin.right);
        }
        if (node instanceof UnaryExprNode un) {
            desugarExpr(un.operand);
        }
        if (node instanceof ApplicationNode app) {
            desugarExpr(app.function);
            desugarExpr(app.argument);
        }
        if (node instanceof AscriptionNode asc) {
            desugarExpr(asc.expr);
        }
        if (node instanceof LambdaNode lam) {
            desugarExpr(lam.body);
        }
        if (node instanceof CaseNode c) {
            desugarExpr(c.expr);
            for (CaseNode.CaseBranch br : c.branches) desugarExpr(br.body());
        }
        if (node instanceof BlockNode block) {
            for (ASTNode stmt : block.statements) {
                if (stmt instanceof ExpressionNode e) desugarExpr(e);
            }
        }
        if (node instanceof TupleNode t) {
            for (ExpressionNode e : t.elements) desugarExpr(e);
        }
        return node;
    }
}
