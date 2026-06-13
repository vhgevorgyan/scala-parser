package scala.parser.core.hover;

import java.util.Map;

import scala.parser.core.ast.*;
import scala.parser.core.types.*;

public class HoverProvider {

    public static String getHoverText(ASTNode node, Map<ExpressionNode, Type> inferredTypes) {
        String base = getHoverText(node);
        if (base == null) return null;

        if (node instanceof ExpressionNode expr && inferredTypes != null) {
            Type type = inferredTypes.get(expr);
            if (type != null) {
                return base + " — " + typeToString(type);
            }
        }

        return base;
    }

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

    private static String typeToString(Type t) {
        if (t instanceof TypeNumber) return "Number";
        if (t instanceof TypeBool) return "Bool";
        if (t instanceof TypeVar v) return v.name();
        if (t instanceof TypeFun f) {
            String from = typeToString(f.from());
            String to = typeToString(f.to());
            if (f.from() instanceof TypeFun) from = "(" + from + ")";
            return from + " -> " + to;
        }
        return t.toString();
    }
}
