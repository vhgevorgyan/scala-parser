package scala.parser.core.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import scala.parser.core.ast.*;

/** Desugars Scala surface syntax into λ-Core: normalizes if-then (no else) → if-then-else (), a..b → (a, b), etc. */
public class Desugarer {

    public static ProgramNode desugar(ProgramNode program) {
        var newStmts = new ArrayList<ASTNode>();
        for (ASTNode stmt : program.statements) {
            if (stmt instanceof ExpressionNode e) {
                newStmts.add(desugarExpr(e));
            } else if (stmt instanceof LetStatementNode let) {
                ExpressionNode newValue = desugarExpr(let.value);
                if (newValue != let.value) {
                    newStmts.add(new LetStatementNode(let.name, newValue, let.isRec, let.declaredType, let.line, let.column));
                } else {
                    newStmts.add(let);
                }
            } else {
                newStmts.add(stmt);
            }
        }
        program.statements.clear();
        program.statements.addAll(newStmts);
        return program;
    }

    public static ExpressionNode desugarExpr(ExpressionNode node) {
        if (node instanceof IfNode i) {
            ExpressionNode cond = desugarExpr(i.condition);
            ExpressionNode thenBr = desugarExpr(i.thenBranch);
            ExpressionNode elseBr = i.elseBranch != null ? desugarExpr(i.elseBranch)
                : new UnitLiteralNode(i.line, i.column + 2);
            if (cond != i.condition || thenBr != i.thenBranch || elseBr != i.elseBranch) {
                var newIf = new IfNode(cond, thenBr, elseBr);
                return newIf;
            }
            return i;
        }
        if (node instanceof BinaryExprNode bin) {
            ExpressionNode left = desugarExpr(bin.left);
            ExpressionNode right = desugarExpr(bin.right);
            if (bin.operator.equals("..")) {
                return new TupleNode(List.of(left, right), bin.line, bin.column);
            }
            if (left != bin.left || right != bin.right) {
                return new BinaryExprNode(left, bin.operator, right, bin.line, bin.column);
            }
            return bin;
        }
        if (node instanceof UnaryExprNode un) {
            ExpressionNode operand = desugarExpr(un.operand);
            if (operand != un.operand) {
                return new UnaryExprNode(un.operator, operand, un.line, un.column);
            }
            return un;
        }
        if (node instanceof ApplicationNode app) {
            ExpressionNode fn = desugarExpr(app.function);
            ExpressionNode arg = desugarExpr(app.argument);
            if (fn != app.function || arg != app.argument) {
                return new ApplicationNode(fn, arg, app.line, app.column);
            }
            return app;
        }
        if (node instanceof AscriptionNode asc) {
            ExpressionNode expr = desugarExpr(asc.expr);
            if (expr != asc.expr) {
                return new AscriptionNode(expr, asc.declaredType, asc.line, asc.column);
            }
            return asc;
        }
        if (node instanceof LambdaNode lam) {
            ExpressionNode body = desugarExpr(lam.body);
            if (body != lam.body) {
                return new LambdaNode(lam.param, body, lam.declaredType, lam.line, lam.column);
            }
            return lam;
        }
        if (node instanceof CaseNode c) {
            ExpressionNode expr = desugarExpr(c.expr);
            var newBranches = c.branches.stream()
                .map(br -> {
                    ExpressionNode newBody = desugarExpr(br.body());
                    if (newBody != br.body()) {
                        return new CaseNode.CaseBranch(br.constructor(), br.bindings(), newBody, br.literalPattern(), br.asName(), br.guard());
                    }
                    return br;
                })
                .collect(Collectors.toList());
            if (expr != c.expr || !newBranches.equals(c.branches)) {
                return new CaseNode(expr, newBranches, c.line, c.column);
            }
            return c;
        }
        if (node instanceof BlockNode block) {
            var newStmts = new ArrayList<ASTNode>();
            boolean changed = false;
            for (ASTNode stmt : block.statements) {
                if (stmt instanceof ExpressionNode e) {
                    ExpressionNode de = desugarExpr(e);
                    newStmts.add(de);
                    if (de != e) changed = true;
                } else if (stmt instanceof LetStatementNode let) {
                    ExpressionNode newValue = desugarExpr(let.value);
                    if (newValue != let.value) {
                        newStmts.add(new LetStatementNode(let.name, newValue, let.isRec, let.declaredType, let.line, let.column));
                        changed = true;
                    } else {
                        newStmts.add(let);
                    }
                } else {
                    newStmts.add(stmt);
                }
            }
            if (changed) {
                return new BlockNode(newStmts, block.line, block.column);
            }
            return block;
        }
        if (node instanceof TupleNode t) {
            var newElems = t.elements.stream()
                .map(e -> desugarExpr(e))
                .collect(Collectors.toList());
            boolean changed = false;
            for (int i = 0; i < newElems.size(); i++) {
                if (newElems.get(i) != t.elements.get(i)) { changed = true; break; }
            }
            if (changed) {
                return new TupleNode(newElems, t.line, t.column);
            }
            return t;
        }
        return node;
    }
}
