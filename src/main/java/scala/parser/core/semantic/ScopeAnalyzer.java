package scala.parser.core.semantic;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ast.*;

public class ScopeAnalyzer {

    public static List<Definition> collectDefinitions(ASTNode root) {
        List<Definition> defs = new ArrayList<>();
        walkDefinitions(root, defs);
        return defs;
    }

    public static List<Reference> collectReferences(ASTNode root, String targetName) {
        List<Reference> refs = new ArrayList<>();
        walkReferences(root, targetName, refs);
        return refs;
    }

    public static Definition findDefinition(ASTNode root, IdentifierNode id) {
        List<Definition> defs = new ArrayList<>();
        walkDefinitions(root, defs);

        Definition best = null;
        for (Definition d : defs) {
            if (d.name.equals(id.name)) {
                if (best == null || d.line > best.line ||
                        (d.line == best.line && d.column > best.column)) {
                    best = d;
                }
            }
        }
        return best;
    }

    private static void walkDefinitions(ASTNode node, List<Definition> out) {
        if (node == null) return;

        if (node instanceof ProgramNode p) {
            for (ASTNode stmt : p.statements) walkDefinitions(stmt, out);
            return;
        }

        if (node instanceof LetStatementNode let) {
            out.add(new Definition(let.name, let.line, let.column));
            walkDefinitions(let.value, out);
            return;
        }

        if (node instanceof EnumDeclNode data) {
            for (EnumDeclNode.ConstructorDef ctor : data.constructors) {
                out.add(new Definition(ctor.name(), data.line, data.column));
            }
            return;
        }

        if (node instanceof LambdaNode lam) {
            out.add(new Definition(lam.param, lam.line, lam.column));
            walkDefinitions(lam.body, out);
            return;
        }

        if (node instanceof CaseNode c) {
            walkDefinitions(c.expr, out);
            for (CaseNode.CaseBranch br : c.branches) {
                for (CaseNode.Binding b : br.bindings()) {
                    out.add(new Definition(b.name(), b.line(), b.column()));
                }
                if (br.asName() != null) {
                    out.add(new Definition(br.asName(), c.line, c.column));
                }
                if (br.guard() != null) walkDefinitions(br.guard(), out);
                if (br.literalPattern() != null) walkDefinitions(br.literalPattern(), out);
                walkDefinitions(br.body(), out);
            }
            return;
        }

        if (node instanceof BlockNode block) {
            for (ASTNode s : block.statements) walkDefinitions(s, out);
            return;
        }
    }

    private static void walkReferences(ASTNode node, String targetName, List<Reference> out) {
        if (node == null) return;

        if (node instanceof ProgramNode p) {
            for (ASTNode stmt : p.statements) walkReferences(stmt, targetName, out);
            return;
        }

        if (node instanceof LetStatementNode let) {
            if (!let.name.equals(targetName)) walkReferences(let.value, targetName, out);
            return;
        }

        if (node instanceof IdentifierNode id) {
            if (id.name.equals(targetName)) out.add(new Reference(id.line, id.column, id.name.length()));
            return;
        }

        if (node instanceof LambdaNode lam) {
            if (!lam.param.equals(targetName)) walkReferences(lam.body, targetName, out);
            return;
        }

        if (node instanceof BinaryExprNode bin) {
            walkReferences(bin.left, targetName, out); walkReferences(bin.right, targetName, out); return;
        }

        if (node instanceof IfNode i) {
            walkReferences(i.condition, targetName, out);
            walkReferences(i.thenBranch, targetName, out);
            if (i.elseBranch != null) walkReferences(i.elseBranch, targetName, out); return;
        }

        if (node instanceof AscriptionNode asc) {
            walkReferences(asc.expr, targetName, out); return;
        }

        if (node instanceof ApplicationNode app) {
            walkReferences(app.function, targetName, out); walkReferences(app.argument, targetName, out); return;
        }

        if (node instanceof UnaryExprNode un) {
            walkReferences(un.operand, targetName, out); return;
        }

        if (node instanceof CaseNode c) {
            walkReferences(c.expr, targetName, out);
            for (CaseNode.CaseBranch br : c.branches) {
                for (CaseNode.Binding b : br.bindings()) {
                    if (b.name().equals(targetName)) return;
                }
                if (br.asName() != null && br.asName().equals(targetName)) return;
                if (br.guard() != null) walkReferences(br.guard(), targetName, out);
                if (br.literalPattern() != null) walkReferences(br.literalPattern(), targetName, out);
                walkReferences(br.body(), targetName, out);
            }
            return;
        }

        if (node instanceof BlockNode block) {
            for (ASTNode s : block.statements) walkReferences(s, targetName, out);
            return;
        }
    }

    public record Definition(String name, int line, int column) {}
    public record Reference(int line, int column, int length) {}
}
