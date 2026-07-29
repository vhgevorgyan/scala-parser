package scala.parser.core.hover;

import java.util.Map;

import scala.parser.core.ast.*;
import scala.parser.core.types.*;

public class HoverProvider {

    public static String getHoverText(ASTNode node, Map<ASTNode, Type> inferredTypes) {
        String base = getHoverText(node);
        if (base == null) return null;

        if (inferredTypes != null) {
            Type type = inferredTypes.get(node);
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
            return "Lambda: (" + lam.param + (lam.declaredType != null ? ": " + lam.declaredType : "") + ") => ...";
        }

        if (node instanceof ApplicationNode app) {
            return "Function application";
        }

        if (node instanceof LetStatementNode let) {
            return "Variable declaration: " + let.name;
        }

        if (node instanceof EnumDeclNode en) {
            String params = en.params.isEmpty() ? "" : "[" + String.join(", ", en.params) + "]";
            String ctors = en.constructors.stream().map(c ->
                c.name() + (c.argTypes().isEmpty() ? "" : "(" + String.join(", ", c.argTypes()) + ")")
            ).reduce((a, b) -> a + "; " + b).orElse("");
            return "Enum type: " + en.name + params + " { " + ctors + " }";
        }

        if (node instanceof UnitLiteralNode) {
            return "Unit literal: ()";
        }

        if (node instanceof CharLiteralNode c) {
            return "Char: '" + escapeChar(c.value) + "'";
        }

        if (node instanceof WhileNode w) {
            return "While loop";
        }

        if (node instanceof AscriptionNode asc) {
            return "Type ascription: " + asc.declaredType;
        }

        if (node instanceof MutualLetNode mutual) {
            return "Mutual definitions: " + mutual.defs.stream()
                .map(d -> d.name)
                .collect(java.util.stream.Collectors.joining(", "));
        }

        return null;
    }

    private static String escapeChar(char c) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            default -> String.valueOf(c);
        };
    }

    private static String typeToString(Type t) {
        return t.toString();
    }
}
