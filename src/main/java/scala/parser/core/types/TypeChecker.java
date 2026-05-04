package scala.parser.core.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import scala.parser.core.ParserError;
import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.ApplicationNode;
import scala.parser.core.ast.BinaryExprNode;
import scala.parser.core.ast.IdentifierNode;
import scala.parser.core.ast.LambdaNode;
import scala.parser.core.ast.LetStatementNode;
import scala.parser.core.ast.LiteralNode;
import scala.parser.core.ast.ProgramNode;

public class TypeChecker {

    private final Map<String, Type> symbols = new HashMap<>();
    private final List<ParserError> errors;

    public TypeChecker(List<ParserError> errors) {
        this.errors = errors;
    }

    public void check(ASTNode node) {
        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) {
                check(stmt);
            }
        }

        if (node instanceof LetStatementNode let) {
            Type valueType = infer(let.value);
            symbols.put(let.name, valueType);
        }
    }

    private Type infer(ASTNode node) {
        if (node instanceof LiteralNode) {
            return Type.NUMBER;
        }

        if (node instanceof IdentifierNode id) {
            if (!symbols.containsKey(id.name)) {
                errors.add(new ParserError(
                        "Variable '" + id.name + "' is not defined",
                        id.line,
                        id.column
                ));
                return Type.UNKNOWN;
            }
            return symbols.get(id.name);
        }

        if (node instanceof BinaryExprNode bin) {
            Type left = infer(bin.left);
            Type right = infer(bin.right);

            if (left != Type.NUMBER || right != Type.NUMBER) {
                errors.add(new ParserError(
                        "Operator '" + bin.operator + "' requires numbers",
                        bin.line,
                        bin.column
                ));
                return Type.UNKNOWN;
            }

            return Type.NUMBER;
        }

        if (node instanceof LambdaNode lam) {
            symbols.put(lam.param, Type.UNKNOWN);
            infer(lam.body);
            return Type.FUNCTION;
        }

        if (node instanceof ApplicationNode app) {
            Type fun = infer(app.function);
            infer(app.argument);

            if (fun != Type.FUNCTION) {
                errors.add(new ParserError(
                        "Trying to call a non-function",
                        app.line,
                        app.column
                ));
            }

            return Type.UNKNOWN;
        }

        return Type.UNKNOWN;
    }
}
