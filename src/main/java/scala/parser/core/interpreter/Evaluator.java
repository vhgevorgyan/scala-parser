package scala.parser.core.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import scala.parser.core.ast.*;
import scala.parser.core.types.*;

public class Evaluator {

    private int maxDepth = 1000;
    private int depth = 0;

    public Evaluator() {}

    public Evaluator(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

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
        // List constructors
        env.define("Nil", new ConstructorValue("Nil", "List", new ArrayList<>()));
        env.define("Cons", new ConstructorBuilder("List", "Cons", 2));
        return env;
    }

    private Value eval(ASTNode node, Env env) {
        if (node instanceof TypeAliasNode) {
            return null; // type aliases are erased at runtime
        }

        if (node instanceof EnumDeclNode data) {
            for (EnumDeclNode.ConstructorDef ctor : data.constructors) {
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

        if (node instanceof MutualLetNode mutual) {
            Env sharedEnv = new Env(env);
            Value lastValue = null;
            for (LetStatementNode def : mutual.defs) {
                Value v = eval(def.value, env);
                if (v instanceof ClosureValue clos) {
                    ClosureValue recClos = new ClosureValue(clos.param, clos.body, sharedEnv);
                    sharedEnv.define(def.name, recClos);
                    env.define(def.name, recClos);
                    lastValue = recClos;
                } else {
                    sharedEnv.define(def.name, v);
                    env.define(def.name, v);
                    lastValue = v;
                }
            }
            return lastValue;
        }

        if (node instanceof ExpressionNode expr) {
            return evalExpr(expr, env);
        }

        throw new RuntimeException("Cannot evaluate node: " + node.getClass().getSimpleName());
    }

    public Value evalExpr(ExpressionNode node, Env env) {
        if (depth >= maxDepth) throw new RuntimeException("Recursion depth limit exceeded");
        depth++;

        try {
            return evalExprImpl(node, env);
        } finally {
            depth--;
        }
    }

    private Value evalExprImpl(ExpressionNode node, Env env) {
        if (node instanceof UnitLiteralNode) {
            return new UnitValue();
        }

        if (node instanceof CharLiteralNode c) {
            return new CharValue(c.value);
        }

        if (node instanceof StringLiteralNode s) {
            return new StringValue(s.value);
        }

        if (node instanceof BoolNode b) {
            return new BoolValue(b.value);
        }

        if (node instanceof LiteralNode lit) {
            return new NumberValue(Integer.parseInt(lit.value));
        }

        if (node instanceof TupleNode tuple) {
            List<Value> values = new ArrayList<>();
            for (ExpressionNode elem : tuple.elements) {
                values.add(evalExpr(elem, env));
            }
            return new TupleValue(values);
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
            if (un.operator.equals("-")) {
                if (operand instanceof NumberValue n) return new NumberValue(-n.value);
                throw new RuntimeException("- requires numeric operand");
            }
            throw new RuntimeException("Unknown unary operator: " + un.operator);
        }

        if (node instanceof IfNode i) {
            Value cond = evalExpr(i.condition, env);
            if (!(cond instanceof BoolValue b)) throw new RuntimeException("Condition must be boolean");
            if (b.value) return evalExpr(i.thenBranch, env);
            if (i.elseBranch != null) return evalExpr(i.elseBranch, env);
            return new UnitValue();
        }

        if (node instanceof WhileNode w) {
            while (true) {
                Value cond = evalExpr(w.condition, env);
                if (!(cond instanceof BoolValue b)) throw new RuntimeException("While condition must be boolean");
                if (!b.value) break;
                evalExpr(w.body, env);
            }
            return new UnitValue();
        }

        if (node instanceof BinaryExprNode bin) {
            Value left = evalExpr(bin.left, env);
            Value right = evalExpr(bin.right, env);

            // Range operator
            if (bin.operator.equals("..")) {
                if (left instanceof NumberValue l && right instanceof NumberValue r) {
                    return new TupleValue(List.of(left, right));
                }
                throw new RuntimeException("Range '..' requires numeric operands, got " + left + " and " + right);
            }

            if (left instanceof NumberValue l && right instanceof NumberValue r) return evalNumberOp(bin.operator, l, r);
            if (left instanceof BoolValue l && right instanceof BoolValue r) return evalBoolOp(bin.operator, l, r);
            if (left instanceof StringValue ls && right instanceof StringValue rs) return evalStringOp(bin.operator, ls, rs);

            throw new RuntimeException("Operator '" + bin.operator + "' not supported for " +
                    left.getClass().getSimpleName() + " and " + right.getClass().getSimpleName());
        }

        if (node instanceof CaseNode caseNode) {
            Value expr = evalExpr(caseNode.expr, env);
            if (!(expr instanceof ConstructorValue cv)) {
                for (CaseNode.CaseBranch branch : caseNode.branches) {
                    if (branch.literalPattern() != null) {
                        Value pat = evalLiteralPattern(branch.literalPattern());
                        if (valuesEqual(expr, pat)) {
                            Env be = new Env(env);
                            if (branch.asName() != null) be.define(branch.asName(), expr);
                            if (branch.guard() != null && !guardPasses(be, branch.guard())) continue;
                            return evalExpr(branch.body(), be);
                        }
                    }
                    if (branch.constructor() == null && branch.literalPattern() == null) {
                        Env be = new Env(env);
                        if (branch.asName() != null) be.define(branch.asName(), expr);
                        if (branch.guard() != null && !guardPasses(be, branch.guard())) continue;
                        return evalExpr(branch.body(), be);
                    }
                }
                throw new RuntimeException("No matching branch for value: " + expr);
            }

            for (CaseNode.CaseBranch branch : caseNode.branches) {
                if (branch.constructor() == null && branch.literalPattern() == null) {
                    Env be = new Env(env);
                    if (branch.asName() != null) be.define(branch.asName(), expr);
                    if (branch.guard() != null && !guardPasses(be, branch.guard())) continue;
                    return evalExpr(branch.body(), be);
                }

                if (branch.constructor() != null && branch.constructor().equals(cv.name)) {
                    Env be = new Env(env);
                    if (branch.asName() != null) be.define(branch.asName(), expr);
                    for (int i = 0; i < branch.bindings().size() && i < cv.args.size(); i++) {
                        be.define(branch.bindings().get(i).name(), cv.args.get(i));
                    }
                    if (branch.guard() != null && !guardPasses(be, branch.guard())) continue;
                    return evalExpr(branch.body(), be);
                }
            }

            throw new RuntimeException("No matching branch for constructor '" + cv.name + "'");
        }

        if (node instanceof BlockNode block) {
            Env blockEnv = new Env(env);
            Value last = null;
            for (ASTNode stmt : block.statements) {
                last = eval(stmt, blockEnv);
            }
            return last;
        }

        if (node instanceof AscriptionNode asc) {
            Value v = evalExpr(asc.expr, env);
            Type dyn = asc.declaredType;
            if (!(dyn instanceof TypeDynamic) && !runtimeTypeMatches(v, dyn)) {
                throw new RuntimeException("Dynamic type error: expected " + dyn + " but got " + runtimeTypeOf(v));
            }
            return v;
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

    private Value evalLiteralPattern(ExpressionNode node) {
        if (node instanceof LiteralNode lit) return new NumberValue(Integer.parseInt(lit.value));
        if (node instanceof BoolNode b) return new BoolValue(b.value);
        throw new RuntimeException("Unexpected literal pattern type: " + node.getClass().getSimpleName());
    }

    private boolean valuesEqual(Value a, Value b) {
        if (a instanceof NumberValue na && b instanceof NumberValue nb) return na.value == nb.value;
        if (a instanceof BoolValue ba && b instanceof BoolValue bb) return ba.value == bb.value;
        if (a instanceof StringValue sa && b instanceof StringValue sb) return sa.value.equals(sb.value);
        return false;
    }

    private boolean runtimeTypeMatches(Value v, Type t) {
        if (t instanceof TypeDynamic) return true;
        if (t instanceof TypeNumber) return v instanceof NumberValue;
        if (t instanceof TypeBool) return v instanceof BoolValue;
        if (t instanceof TypeString) return v instanceof StringValue;
        if (t instanceof TypeChar) return v instanceof CharValue;
        if (t instanceof TypeUnit) return v instanceof UnitValue;
        if (t instanceof TypeTuple tt) {
            if (!(v instanceof TupleValue tv)) return false;
            if (tv.values.size() != tt.members().size()) return false;
            for (int i = 0; i < tv.values.size(); i++) {
                if (!runtimeTypeMatches(tv.values.get(i), tt.members().get(i))) return false;
            }
            return true;
        }
        if (t instanceof TypeFun) return v instanceof ClosureValue || v instanceof BuiltinValue;
        if (t instanceof TypeConstructor) return v instanceof ConstructorValue;
        return true;
    }

    private Type runtimeTypeOf(Value v) {
        if (v instanceof NumberValue) return new TypeNumber();
        if (v instanceof BoolValue) return new TypeBool();
        if (v instanceof StringValue) return new TypeString();
        if (v instanceof CharValue) return new TypeChar();
        if (v instanceof UnitValue) return new TypeUnit();
        if (v instanceof TupleValue tv) {
            return new TypeTuple(tv.values.stream().map(this::runtimeTypeOf).toList());
        }
        if (v instanceof ClosureValue || v instanceof BuiltinValue) {
            return new TypeFun(new TypeDynamic(), new TypeDynamic());
        }
        if (v instanceof ConstructorValue cv) {
            return new TypeConstructor(cv.name, cv.args.stream().map(a -> (Type) new TypeDynamic()).toList());
        }
        return new TypeDynamic();
    }

    private boolean guardPasses(Env env, ExpressionNode guard) {
        Value v = evalExpr(guard, env);
        if (v instanceof BoolValue bv) return bv.value;
        return true;
    }
}
