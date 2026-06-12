package scala.parser.core.interpreter;

import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.ApplicationNode;
import scala.parser.core.ast.BinaryExprNode;
import scala.parser.core.ast.BoolNode;
import scala.parser.core.ast.ExpressionNode;
import scala.parser.core.ast.IdentifierNode;
import scala.parser.core.ast.IfNode;
import scala.parser.core.ast.LambdaNode;
import scala.parser.core.ast.LetStatementNode;
import scala.parser.core.ast.LiteralNode;
import scala.parser.core.ast.ProgramNode;

public class Evaluator {

    public Value evalProgram(ProgramNode program) {
        Env env = new Env();
        Value last = null;
        for (ASTNode stmt : program.statements) {
            last = eval(stmt, env);
        }
        return last;
    }

    private Value eval(ASTNode node, Env env) {
        if (node instanceof LetStatementNode let) {
            Value v = eval(let.value, env);
            env.define(let.name, v);
            return v;
        }

        if (node instanceof ExpressionNode expr) {
            return evalExpr(expr, env);
        }

        throw new RuntimeException("Cannot evaluate node: " + node.getClass().getSimpleName());
    }

    private Value evalExpr(ExpressionNode node, Env env) {

        // Boolean literal
        if (node instanceof BoolNode b) {
            return new BoolValue(b.value);
        }

        // Number literal
        if (node instanceof LiteralNode lit) {
            return new NumberValue(Integer.parseInt(lit.value));
        }

        // Identifier
        if (node instanceof IdentifierNode id) {
            return env.lookup(id.name);
        }

        // If expression
        if (node instanceof IfNode i) {
            Value cond = evalExpr(i.condition, env);

            if (!(cond instanceof BoolValue b)) {
                throw new RuntimeException("Condition must be boolean");
            }

            return b.value
                    ? evalExpr(i.thenBranch, env)
                    : evalExpr(i.elseBranch, env);
        }

        // Binary operations (arithmetik + vergleich)
        if (node instanceof BinaryExprNode bin) {
            Value left = evalExpr(bin.left, env);
            Value right = evalExpr(bin.right, env);

            if (left instanceof NumberValue l && right instanceof NumberValue r) {
                return switch (bin.operator) {
                    case "+" -> new NumberValue(l.value + r.value);
                    case "-" -> new NumberValue(l.value - r.value);
                    case "*" -> new NumberValue(l.value * r.value);
                    case "/" -> new NumberValue(l.value / r.value);
                    case "==" -> new BoolValue(l.value == r.value);
                    case "!=" -> new BoolValue(l.value != r.value);
                    case "<" -> new BoolValue(l.value < r.value);
                    case ">" -> new BoolValue(l.value > r.value);
                    case "<=" -> new BoolValue(l.value <= r.value);
                    case ">=" -> new BoolValue(l.value >= r.value);
                    default -> throw new RuntimeException("Unknown operator: " + bin.operator);
                };
            }

            throw new RuntimeException("Binary operator requires numbers");
        }

        // Lambda
        if (node instanceof LambdaNode lam) {
            return new ClosureValue(lam.param, lam.body, env);
        }

        // Function application
        if (node instanceof ApplicationNode app) {
            Value fn = evalExpr(app.function, env);
            Value arg = evalExpr(app.argument, env);

            if (!(fn instanceof ClosureValue clos)) {
                throw new RuntimeException("Trying to call a non-function");
            }

            Env newEnv = new Env(clos.env);
            newEnv.define(clos.param, arg);

            return evalExpr(clos.body, newEnv);
        }

        throw new RuntimeException("Unknown expression: " + node.getClass().getSimpleName());
    }
}
