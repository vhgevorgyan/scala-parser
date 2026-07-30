package scala.parser.core.reduction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import scala.parser.core.ast.*;

public class Substitution {

    public static ExpressionNode subst(String var, ExpressionNode replacement, ExpressionNode expr) {
        if (expr instanceof IdentifierNode id) {
            if (id.name.equals(var)) return replacement;
            return id;
        }

        if (expr instanceof LambdaNode lam) {
            if (lam.param.equals(var)) return lam; // shadowed
            Set<String> fv = freeVars(replacement);
            if (fv.contains(lam.param)) {
                String fresh = freshName(lam.param, fv);
                ExpressionNode renamedBody = renameVar(lam.param, fresh, lam.body);
                return new LambdaNode(fresh, subst(var, replacement, renamedBody), lam.declaredType, lam.line, lam.column);
            }
            return new LambdaNode(lam.param, subst(var, replacement, lam.body), lam.declaredType, lam.line, lam.column);
        }

        if (expr instanceof ApplicationNode app) {
            return new ApplicationNode(
                subst(var, replacement, app.function),
                subst(var, replacement, app.argument),
                app.line, app.column);
        }

        if (expr instanceof IfNode i) {
            return new IfNode(
                subst(var, replacement, i.condition),
                subst(var, replacement, i.thenBranch),
                i.elseBranch != null ? subst(var, replacement, i.elseBranch) : null);
        }

        if (expr instanceof BinaryExprNode bin) {
            return new BinaryExprNode(
                subst(var, replacement, bin.left),
                bin.operator,
                subst(var, replacement, bin.right),
                bin.line, bin.column);
        }

        if (expr instanceof UnaryExprNode un) {
            return new UnaryExprNode(un.operator,
                subst(var, replacement, un.operand),
                un.line, un.column);
        }

        if (expr instanceof TupleNode tup) {
            var newEls = tup.elements.stream()
                .map(e -> subst(var, replacement, e))
                .toList();
            return new TupleNode(newEls, tup.line, tup.column);
        }

        if (expr instanceof AscriptionNode asc) {
            return new AscriptionNode(
                subst(var, replacement, asc.expr),
                asc.declaredType, asc.line, asc.column);
        }

        if (expr instanceof FixpointNode fp) {
            return new FixpointNode(
                subst(var, replacement, fp.body),
                fp.line, fp.column);
        }

        if (expr instanceof AssignNode assign) {
            return new AssignNode(assign.name,
                subst(var, replacement, assign.value),
                assign.line, assign.column);
        }

        if (expr instanceof BlockNode block) {
            var newStmts = block.statements.stream()
                .map(s -> substInBlockStatement(var, replacement, s))
                .toList();
            return new BlockNode(newStmts, block.line, block.column);
        }

        if (expr instanceof CaseNode c) {
            var newBranches = c.branches.stream()
                .map(br -> new CaseNode.CaseBranch(br.constructor(), br.bindings(),
                    subst(var, replacement, br.body()), br.literalPattern(),
                    br.asName(), br.guard() != null ? subst(var, replacement, br.guard()) : null))
                .toList();
            // scrutinee is always a value, but subst to be safe
            return new CaseNode(subst(var, replacement, c.expr), newBranches, c.line, c.column);
        }

        if (expr instanceof WhileNode w) {
            return new WhileNode(
                subst(var, replacement, w.condition),
                subst(var, replacement, w.body),
                w.line, w.column);
        }

        return expr;
    }

    public static ExpressionNode renameVar(String oldName, String newName, ExpressionNode expr) {
        return subst(oldName, new IdentifierNode(newName, 0, 0), expr);
    }

    public static ASTNode substInBlockStatement(String var, ExpressionNode replacement, ASTNode node) {
        if (node instanceof ExpressionNode e) return subst(var, replacement, e);
        if (node instanceof LetStatementNode l) {
            ExpressionNode newBody = subst(var, replacement, l.value);
            return new LetStatementNode(l.name, newBody, l.isRec, l.isMutable, l.declaredType, l.line, l.column);
        }
        if (node instanceof MutualLetNode m) {
            List<LetStatementNode> newDefs = new ArrayList<>();
            for (LetStatementNode def : m.defs) {
                ExpressionNode newBody = subst(var, replacement, def.value);
                newDefs.add(new LetStatementNode(def.name, newBody, def.isRec, def.isMutable, def.declaredType, def.line, def.column));
            }
            return new MutualLetNode(newDefs, m.line, m.column);
        }
        return node;
    }

    public static Set<String> freeVars(ExpressionNode expr) {
        Set<String> fv = new HashSet<>();
        collectFree(expr, fv, new HashSet<>());
        return fv;
    }

    private static void collectFree(ExpressionNode expr, Set<String> fv, Set<String> bound) {
        if (expr instanceof IdentifierNode id) {
            if (!bound.contains(id.name)) fv.add(id.name);
            return;
        }
        if (expr instanceof LambdaNode lam) {
            bound.add(lam.param);
            collectFree(lam.body, fv, bound);
            bound.remove(lam.param);
            return;
        }
        if (expr instanceof ApplicationNode app) {
            collectFree(app.function, fv, bound);
            collectFree(app.argument, fv, bound);
            return;
        }
        if (expr instanceof IfNode i) {
            collectFree(i.condition, fv, bound);
            collectFree(i.thenBranch, fv, bound);
            if (i.elseBranch != null) collectFree(i.elseBranch, fv, bound);
            return;
        }
        if (expr instanceof BinaryExprNode bin) {
            collectFree(bin.left, fv, bound);
            collectFree(bin.right, fv, bound);
            return;
        }
        if (expr instanceof UnaryExprNode un) {
            collectFree(un.operand, fv, bound);
            return;
        }
        if (expr instanceof TupleNode tup) {
            tup.elements.forEach(e -> collectFree(e, fv, bound));
            return;
        }
        if (expr instanceof AscriptionNode asc) {
            collectFree(asc.expr, fv, bound);
            return;
        }
        if (expr instanceof BlockNode block) {
            for (var s : block.statements) {
                if (s instanceof ExpressionNode e) collectFree(e, fv, bound);
                if (s instanceof LetStatementNode l) collectFree(l.value, fv, bound);
                if (s instanceof MutualLetNode m) {
                    for (var def : m.defs) collectFree(def.value, fv, bound);
                }
            }
            return;
        }
        if (expr instanceof FixpointNode fp) {
            collectFree(fp.body, fv, bound);
            return;
        }
        if (expr instanceof CaseNode c) {
            collectFree(c.expr, fv, bound);
            for (var br : c.branches) {
                if (br.constructor() != null && br.bindings() != null) {
                    for (var b : br.bindings()) bound.add(b.name());
                }
                collectFree(br.body(), fv, bound);
                if (br.constructor() != null && br.bindings() != null) {
                    for (var b : br.bindings()) bound.remove(b.name());
                }
            }
            return;
        }
        if (expr instanceof WhileNode w) {
            collectFree(w.condition, fv, bound);
            collectFree(w.body, fv, bound);
            return;
        }
        if (expr instanceof AssignNode assign) {
            collectFree(assign.value, fv, bound);
            return;
        }
    }

    private static int counter = 0;
    private static String freshName(String base, Set<String> avoid) {
        String candidate;
        do { candidate = base + "_" + (counter++); } while (avoid.contains(candidate));
        return candidate;
    }
}
