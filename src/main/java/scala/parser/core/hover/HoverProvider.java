package scala.parser.core.hover;

import scala.parser.core.ast.*;

public class HoverProvider {

    public static String getHoverText(ASTNode node) {
        if (node == null) return null;

        if (node instanceof BoolNode b) {
            return "Boolean: " + b.value;
        }

        if (node instanceof LiteralNode lit) {
            return "Literal: " + lit.value;
        }

        if (node instanceof IdentifierNode id) {
            return "Identifier: " + id.name;
        }

        if (node instanceof BinaryExprNode bin) {
            return "Binary Expression: " + bin.operator;
        }

        if (node instanceof IfNode i) {
            return "If expression";
        }

        if (node instanceof LambdaNode lam) {
            return "Lambda: \\" + lam.param + " -> ...";
        }

        if (node instanceof ApplicationNode app) {
            return "Function application";
        }

        if (node instanceof LetStatementNode let) {
            return "Variable declaration: " + let.name;
        }

        return null;
    }
}
