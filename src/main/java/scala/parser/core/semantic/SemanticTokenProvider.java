package scala.parser.core.semantic;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ast.*;

public class SemanticTokenProvider {

    public static List<SemanticToken> collect(ASTNode root) {
        List<SemanticToken> tokens = new ArrayList<>();
        walk(root, tokens);
        return tokens;
    }

    private static void walk(ASTNode node, List<SemanticToken> out) {
        if (node == null) return;

        if (node instanceof BoolNode b) {
            out.add(new SemanticToken(b.line, b.column, String.valueOf(b.value).length(), "keyword")); return;
        }

        if (node instanceof IdentifierNode id) {
            out.add(new SemanticToken(id.line, id.column, id.name.length(), "variable"));
        }

        if (node instanceof StringLiteralNode s) {
            out.add(new SemanticToken(s.line, s.column, s.value.length() + 2, "string"));
        }

        if (node instanceof LiteralNode lit) {
            out.add(new SemanticToken(lit.line, lit.column, lit.value.length(), "number"));
        }

        if (node instanceof UnaryExprNode un) {
            out.add(new SemanticToken(un.line, un.column, un.operator.length(), "operator"));
            walk(un.operand, out); return;
        }

        if (node instanceof BinaryExprNode bin) {
            out.add(new SemanticToken(bin.line, bin.column, bin.operator.length(), "operator"));
            walk(bin.left, out); walk(bin.right, out); return;
        }

        if (node instanceof IfNode i) {
            out.add(new SemanticToken(i.line, i.column, 2, "keyword"));
            walk(i.condition, out); walk(i.thenBranch, out);
            if (i.elseBranch != null) walk(i.elseBranch, out); return;
        }

        if (node instanceof AscriptionNode asc) {
            walk(asc.expr, out); return;
        }

        if (node instanceof WhileNode w) {
            out.add(new SemanticToken(w.line, w.column, 5, "keyword"));
            walk(w.condition, out); walk(w.body, out); return;
        }

        if (node instanceof LambdaNode lam) {
            out.add(new SemanticToken(lam.line, lam.column, 1, "keyword"));
            walk(lam.body, out); return;
        }

        if (node instanceof ApplicationNode app) {
            walk(app.function, out); walk(app.argument, out); return;
        }

        if (node instanceof LetStatementNode let) {
            out.add(new SemanticToken(let.line, let.column, 3, "keyword"));
            int nameCol = let.column + 4;
            out.add(new SemanticToken(let.line, nameCol, let.name.length(), "variable"));
            walk(let.value, out); return;
        }

        if (node instanceof MutualLetNode mutual) {
            out.add(new SemanticToken(mutual.line, mutual.column, 6, "keyword"));
            int col = mutual.column + 7;
            for (int i = 0; i < mutual.defs.size(); i++) {
                LetStatementNode def = mutual.defs.get(i);
                out.add(new SemanticToken(mutual.line, col, def.name.length(), "function"));
                col += def.name.length() + 2;
            }
            for (LetStatementNode def : mutual.defs) walk(def.value, out); return;
        }

        if (node instanceof EnumDeclNode data) {
            out.add(new SemanticToken(data.line, data.column, 4, "keyword"));
            return;
        }

        if (node instanceof CaseNode c) {
            out.add(new SemanticToken(c.line, c.column, 5, "keyword"));
            walk(c.expr, out);
            for (CaseNode.CaseBranch br : c.branches) {
                for (CaseNode.Binding b : br.bindings()) {
                    out.add(new SemanticToken(b.line(), b.column(), b.name().length(), "variable"));
                }
                if (br.asName() != null) {
                    out.add(new SemanticToken(c.line, c.column, br.asName().length(), "variable"));
                }
                if (br.guard() != null) walk(br.guard(), out);
                if (br.literalPattern() != null) walk(br.literalPattern(), out);
                walk(br.body(), out);
            }
            return;
        }

        if (node instanceof BlockNode block) {
            for (ASTNode s : block.statements) walk(s, out);
            return;
        }

        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) walk(stmt, out);
        }
    }
}
