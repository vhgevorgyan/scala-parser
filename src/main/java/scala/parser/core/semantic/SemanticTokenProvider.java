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
        if (node == null) {
            return;
        }

        if (node instanceof BoolNode b) {
            String text = String.valueOf(b.value);
            out.add(new SemanticToken(b.line, b.column, text.length(), "keyword"));
            return;
        }

        if (node instanceof IdentifierNode id) {
            out.add(new SemanticToken(id.line, id.column, id.name.length(), "variable"));
        }

        if (node instanceof LiteralNode lit) {
            out.add(new SemanticToken(lit.line, lit.column, lit.value.length(), "number"));
        }

        if (node instanceof BinaryExprNode bin) {
            out.add(new SemanticToken(bin.line, bin.column, bin.operator.length(), "operator"));
            walk(bin.left, out);
            walk(bin.right, out);
            return;
        }

        if (node instanceof IfNode i) {
            out.add(new SemanticToken(i.line, i.column, 2, "keyword"));
            walk(i.condition, out);
            walk(i.thenBranch, out);
            walk(i.elseBranch, out);
            return;
        }

        if (node instanceof LambdaNode lam) {
            out.add(new SemanticToken(lam.line, lam.column, 1, "keyword"));
            walk(lam.body, out);
            return;
        }

        if (node instanceof ApplicationNode app) {
            walk(app.function, out);
            walk(app.argument, out);
            return;
        }

        if (node instanceof LetStatementNode let) {
            out.add(new SemanticToken(let.line, let.column, 3, "keyword"));
            walk(let.value, out);
            return;
        }

        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) {
                walk(stmt, out);
            }
        }
    }
}
