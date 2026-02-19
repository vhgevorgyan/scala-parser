package scala.parser.core.semantic;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.BinaryExprNode;
import scala.parser.core.ast.IdentifierNode;
import scala.parser.core.ast.LetStatementNode;
import scala.parser.core.ast.LiteralNode;
import scala.parser.core.ast.ProgramNode;

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

        if (node instanceof LetStatementNode let) {
            out.add(new SemanticToken(let.line, let.column, 3, "keyword")); // "let"
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
