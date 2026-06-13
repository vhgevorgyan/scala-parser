package scala.parser.core.interpreter;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ast.*;

public class Evaluator {

    private static final int MAX_DEPTH = 1000;
    private int depth = 0;

    public Value evalProgram(ProgramNode program) {
        Env env = initEnv();
        Value last = null;
        for (ASTNode stmt : program.statements) {
            last = eval(stmt, env);
        }
        return last;
    }

    public static Env initEnv() {
        Env env = new Env();
        env.define("succ", new BuiltinValue("succ", arg -> {
            if (arg instanceof NumberValue n) return new NumberValue(n.value + 1);
            throw new RuntimeException("succ expects a number");
        }));
        env.define("pred", new BuiltinValue("pred", arg -> {
            if (arg instanceof NumberValue n) return new NumberValue(n.value - 1);
            throw new RuntimeException("pred expects a number");
        }));
        env.define("iszero", new BuiltinValue("iszero", arg -> {
            if (arg instanceof NumberValue n) return new BoolValue(n.value == 0);
            throw new RuntimeException("iszero expects a number");
        }));
        env.define("print", new BuiltinValue("print", arg -> {
            System.out.println(arg);
            return arg;
        }));
        return env;
    }

    private Value eval(ASTNode node, Env env) {
        if (node instanceof DataDeclNode data) {
            for (DataDeclNode.ConstructorDef ctor : data.constructors) {
                if (ctor.argTypes().isEmpty()) {
                    env.define(ctor.name(), new ConstructorValue(ctor.name(), data.name, new ArrayList<>()));
                } else {
                    env.define(ctor.name(), new ConstructorBuilder(data.name, ctor.name(), ctor.argTypes().size()));
                }
            }
            return null;
        }

        if (node instanceof LetStatementNode let) {
            Value v = eval(let.value, env);

            if (let.isRec && v instanceof ClosureValue clos) {
                Env recEnv = new Env(clos.env);
                ClosureValue recClos = new ClosureValue(clos.param, clos.body, recEnv);
                recEnv.define(let.name, recClos);
                env.define(let.name, recClos);
                return recClos;
            }

            env.define(let.name, v);
            return v;
        }

        if (node instanceof ExpressionNode expr) {
            return evalExpr(expr, env);
        }

        throw new RuntimeException("Cannot evaluate node: " + node.getClass().getSimpleName());
    }

    public Value evalExpr(ExpressionNode node, Env env) {
        if (depth >= MAX_DEPTH) throw new RuntimeException("Recursion depth limit exceeded");
        depth++;

        try {
            return evalExprImpl(node, env);
        } finally {
            depth--;
        }
    }

    private Value evalExprImpl(ExpressionNode node, Env env) {
        if (node instanceof StringLiteralNode s) {
            return new StringValue(s.value);
        }

        if (node instanceof BoolNode b) {
            return new BoolValue(b.value);
        }

        if (node instanceof LiteralNode lit) {
            return new NumberValue(Integer.parseInt(lit.value));
        }

        if (node instanceof IdentifierNode id) {
            return env.lookup(id.name);
        }

        if (node instanceof UnaryExprNode un) {
            Value operand = evalExpr(un.operand, env);
            if (un.operator.equals("!")) {
                if (operand instanceof BoolValue b) return new BoolValue(!b.value);
                throw new RuntimeException("! requires boolean operand");
            }
            throw new RuntimeException("Unknown unary operator: " + un.operator);
        }

        if (node instanceof IfNode i) {
            Value cond = evalExpr(i.condition, env);
            if (!(cond instanceof BoolValue b)) throw new RuntimeException("Condition must be boolean");
            return b.value ? evalExpr(i.thenBranch, env) : evalExpr(i.elseBranch, env);
        }

        if (node instanceof BinaryExprNode bin) {
            Value left = evalExpr(bin.left, env);
            Value right = evalExpr(bin.right, env);

            if (left instanceof NumberValue l && right instanceof NumberValue r) return evalNumberOp(bin.operator, l, r);
            if (left instanceof BoolValue l && right instanceof BoolValue r) return evalBoolOp(bin.operator, l, r);
            if (left instanceof StringValue ls && right instanceof StringValue rs) return evalStringOp(bin.operator, ls, rs);

            throw new RuntimeException("Operator '" + bin.operator + "' not supported for " +
                    left.getClass().getSimpleName() + " and " + right.getClass().getSimpleName());
        }

        if (node instanceof CaseNode caseNode) {
            Value expr = evalExpr(caseNode.expr, env);
            if (!(expr instanceof ConstructorValue cv)) {
                throw new RuntimeException("Case expression requires a constructor value");
            }

            for (CaseNode.CaseBranch branch : caseNode.branches) {
                if (branch.constructor().equals(cv.name)) {
                    Env branchEnv = new Env(env);
                    for (int i = 0; i < branch.bindings().size() && i < cv.args.size(); i++) {
                        branchEnv.define(branch.bindings().get(i).name(), cv.args.get(i));
                    }
                    return evalExpr(branch.body(), branchEnv);
                }
            }

            throw new RuntimeException("No matching branch for constructor '" + cv.name + "'");
        }

        if (node instanceof LambdaNode lam) {
            return new ClosureValue(lam.param, lam.body, env);
        }

        if (node instanceof ApplicationNode app) {
            Value fn = evalExpr(app.function, env);
            Value arg = evalExpr(app.argument, env);

            if (fn instanceof ClosureValue clos) {
                Env newEnv = new Env(clos.env);
                newEnv.define(clos.param, arg);
                return evalExpr(clos.body, newEnv);
            }

            if (fn instanceof BuiltinValue builtin) {
                return builtin.apply(arg);
            }

            // Constructor builder: accumulate args
            if (fn instanceof ConstructorBuilder cb) {
                return cb.apply(arg);
            }

            throw new RuntimeException("Trying to call a non-function");
        }

        throw new RuntimeException("Unknown expression: " + node.getClass().getSimpleName());
    }

    private Value evalNumberOp(String op, NumberValue l, NumberValue r) {
        return switch (op) {
            case "+" -> new NumberValue(l.value + r.value);
            case "-" -> new NumberValue(l.value - r.value);
            case "*" -> new NumberValue(l.value * r.value);
            case "/" -> { if (r.value == 0) throw new RuntimeException("Division by zero"); yield new NumberValue(l.value / r.value); }
            case "==" -> new BoolValue(l.value == r.value);
            case "!=" -> new BoolValue(l.value != r.value);
            case "<" -> new BoolValue(l.value < r.value);
            case ">" -> new BoolValue(l.value > r.value);
            case "<=" -> new BoolValue(l.value <= r.value);
            case ">=" -> new BoolValue(l.value >= r.value);
            default -> throw new RuntimeException("Unknown operator: " + op);
        };
    }

    private Value evalBoolOp(String op, BoolValue l, BoolValue r) {
        return switch (op) {
            case "==" -> new BoolValue(l.value == r.value);
            case "!=" -> new BoolValue(l.value != r.value);
            case "&&" -> new BoolValue(l.value && r.value);
            case "||" -> new BoolValue(l.value || r.value);
            default -> throw new RuntimeException("Operator '" + op + "' not supported for booleans");
        };
    }

    private Value evalStringOp(String op, StringValue l, StringValue r) {
        return switch (op) {
            case "==" -> new BoolValue(l.value.equals(r.value));
            case "!=" -> new BoolValue(!l.value.equals(r.value));
            case "+" -> new StringValue(l.value + r.value);
            default -> throw new RuntimeException("Operator '" + op + "' not supported for strings");
        };
    }
}
