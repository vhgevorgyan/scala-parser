package scala.parser.core.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scala.parser.core.ParserError;
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
import scala.parser.core.ast.StringLiteralNode;
import scala.parser.core.ast.UnaryExprNode;

public class TypeChecker {

    private record Substitution(Map<String, Type> map) {
        public Substitution() {
            this(Map.of());
        }

        public Substitution(String var, Type type) {
            this(Map.of(var, type));
        }
    }

    private static class TypeErrorException extends Exception {
        public TypeErrorException(String message) {
            super(message);
        }
    }

    private record InferResult(Substitution sub, Type type) {}

    private int varCounter = 0;
    private final List<ParserError> errors;
    private final Map<String, TypeScheme> env = new HashMap<>();
    private final Map<ASTNode, Type> inferredTypes = new HashMap<>();

    public TypeChecker(List<ParserError> errors) {
        this.errors = errors;
        initBuiltins();
    }

    private void initBuiltins() {
        Type num = new TypeNumber();
        Type bool = new TypeBool();
        Type numToNum = new TypeFun(num, num);
        Type numToBool = new TypeFun(num, bool);

        env.put("succ", new TypeScheme(List.of(), numToNum));
        env.put("pred", new TypeScheme(List.of(), numToNum));
        env.put("iszero", new TypeScheme(List.of(), numToBool));
        env.put("print", new TypeScheme(List.of(), numToNum));
    }

    public Map<ASTNode, Type> getInferredTypes() {
        return inferredTypes;
    }

    public void check(ASTNode node) {
        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) {
                check(stmt);
            }
            return;
        }

        if (node instanceof LetStatementNode let) {
            InferResult result = inferExpr(env, let.value);
            Substitution sub = result.sub;
            Type type = apply(sub, result.type);

            // Apply substitution to the current environment
            List<Map.Entry<String, TypeScheme>> entries = new ArrayList<>(env.entrySet());
            for (Map.Entry<String, TypeScheme> entry : entries) {
                env.put(entry.getKey(), new TypeScheme(entry.getValue().params(), apply(sub, entry.getValue().body())));
            }

            TypeScheme scheme = generalize(env, type);
            env.put(let.name, scheme);
            inferredTypes.put(let.value, type);
            inferredTypes.put(let, scheme.body());
            return;
        }

        if (node instanceof ExpressionNode expr) {
            inferExpr(env, expr);
            // Expression statements don't add bindings, but any substitution
            // is applied for completeness
        }
    }

    private InferResult inferExpr(Map<String, TypeScheme> env, ExpressionNode expr) {
        if (expr instanceof LiteralNode) {
            return new InferResult(new Substitution(), new TypeNumber());
        }

        if (expr instanceof StringLiteralNode) {
            return new InferResult(new Substitution(), new TypeString());
        }

        if (expr instanceof BoolNode) {
            return new InferResult(new Substitution(), new TypeBool());
        }

        if (expr instanceof IdentifierNode id) {
            TypeScheme scheme = env.get(id.name);
            if (scheme == null) {
                errors.add(new ParserError("Undefined variable '" + id.name + "'", id.line, id.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            return new InferResult(new Substitution(), instantiate(scheme));
        }

        if (expr instanceof BinaryExprNode bin) {
            InferResult left = inferExpr(env, bin.left);
            Substitution s1 = left.sub;
            Type t1 = left.type;

            Map<String, TypeScheme> env2 = apply(s1, env);
            InferResult right = inferExpr(env2, bin.right);
            Substitution s2 = right.sub;
            Type t2 = right.type;

            Type appliedT1 = apply(s2, t1);
            String op = bin.operator;

            boolean isArithmetic = op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/");
            boolean isComparison = op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
            boolean isLogical = op.equals("&&") || op.equals("||");

            if (!isArithmetic && !isComparison && !isLogical) {
                errors.add(new ParserError("Unknown operator '" + op + "'", bin.line, bin.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            Type operandType = isLogical ? new TypeBool() : new TypeNumber();

            Substitution s3;
            try {
                s3 = unify(appliedT1, operandType);
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "Operator '" + op + "' requires " + operandType + ", got " + appliedT1,
                        bin.left.line, bin.left.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            Substitution s4;
            try {
                s4 = unify(apply(s3, t2), operandType);
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "Operator '" + op + "' requires " + operandType + ", got " + apply(s3, t2),
                        bin.right.line, bin.right.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            Type resultType = isArithmetic ? new TypeNumber() : new TypeBool();
            return new InferResult(compose(s4, compose(s3, compose(s2, s1))), resultType);
        }

        if (expr instanceof IfNode i) {
            InferResult cond = inferExpr(env, i.condition);
            Substitution s1 = cond.sub;
            Type t1 = cond.type;

            Substitution s2;
            try {
                s2 = unify(apply(s1, t1), new TypeBool());
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "If condition must be boolean, got " + apply(s1, t1),
                        i.condition.line, i.condition.column));
                s2 = new Substitution();
            }

            Map<String, TypeScheme> envAfterCond = apply(compose(s2, s1), env);
            InferResult thenResult = inferExpr(envAfterCond, i.thenBranch);
            Substitution s3 = thenResult.sub;
            Type tThen = thenResult.type;

            Map<String, TypeScheme> envAfterThen = apply(s3, envAfterCond);
            InferResult elseResult = inferExpr(envAfterThen, i.elseBranch);
            Substitution s4 = elseResult.sub;
            Type tElse = elseResult.type;

            Type appliedTThen = apply(s4, tThen);
            Substitution s5;
            try {
                s5 = unify(appliedTThen, tElse);
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "If branches must have the same type, got " + appliedTThen + " and " + tElse,
                        i.line, i.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            return new InferResult(
                    compose(s5, compose(s4, compose(s3, compose(s2, s1)))),
                    apply(s5, appliedTThen));
        }

        if (expr instanceof UnaryExprNode un) {
            InferResult opResult = inferExpr(env, un.operand);
            Substitution s1 = opResult.sub;
            Type t1 = opResult.type;

            Substitution s2;
            try {
                s2 = unify(apply(s1, t1), new TypeBool());
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "Operator '" + un.operator + "' requires boolean, got " + apply(s1, t1),
                        un.operand.line, un.operand.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            return new InferResult(compose(s2, s1), new TypeBool());
        }

        if (expr instanceof LambdaNode lam) {
            TypeVar paramType = freshVar();
            Map<String, TypeScheme> extEnv = new HashMap<>(env);
            extEnv.put(lam.param, new TypeScheme(List.of(), paramType));

            InferResult body = inferExpr(extEnv, lam.body);
            Substitution s1 = body.sub;
            Type bodyType = body.type;

            return new InferResult(s1, new TypeFun(apply(s1, paramType), bodyType));
        }

        if (expr instanceof ApplicationNode app) {
            InferResult fun = inferExpr(env, app.function);
            Substitution s1 = fun.sub;
            Type t1 = fun.type;

            Map<String, TypeScheme> envAfterFun = apply(s1, env);
            InferResult arg = inferExpr(envAfterFun, app.argument);
            Substitution s2 = arg.sub;
            Type t2 = arg.type;

            TypeVar resultType = freshVar();
            Type appliedT1 = apply(s2, t1);

            Substitution s3;
            try {
                s3 = unify(appliedT1, new TypeFun(t2, resultType));
            } catch (TypeErrorException e) {
                errors.add(new ParserError(
                        "Type error: cannot apply " + appliedT1 + " to " + t2,
                        app.line, app.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            return new InferResult(compose(s3, compose(s2, s1)), apply(s3, resultType));
        }

        errors.add(new ParserError(
                "Unknown expression: " + expr.getClass().getSimpleName(),
                expr.line, expr.column));
        return new InferResult(new Substitution(), new TypeVar("?"));
    }

    // --- Fresh variable ---

    private TypeVar freshVar() {
        return new TypeVar("t" + varCounter++);
    }

    // --- Unification ---

    private Substitution unify(Type a, Type b) throws TypeErrorException {
        if (a instanceof TypeVar va) {
            return unifyVar(va.name(), b);
        }
        if (b instanceof TypeVar vb) {
            return unifyVar(vb.name(), a);
        }
        if (a instanceof TypeNumber && b instanceof TypeNumber) {
            return new Substitution();
        }
        if (a instanceof TypeBool && b instanceof TypeBool) {
            return new Substitution();
        }
        if (a instanceof TypeString && b instanceof TypeString) {
            return new Substitution();
        }
        if (a instanceof TypeFun fa && b instanceof TypeFun fb) {
            Substitution s1 = unify(fa.from(), fb.from());
            Substitution s2 = unify(apply(s1, fa.to()), apply(s1, fb.to()));
            return compose(s2, s1);
        }
        throw new TypeErrorException("Cannot unify " + a + " with " + b);
    }

    private Substitution unifyVar(String name, Type t) throws TypeErrorException {
        if (t instanceof TypeVar v && v.name().equals(name)) {
            return new Substitution();
        }
        if (occurs(name, t)) {
            throw new TypeErrorException("Occurs check: " + name + " occurs in " + t);
        }
        return new Substitution(name, t);
    }

    private boolean occurs(String name, Type t) {
        if (t instanceof TypeVar v) {
            return v.name().equals(name);
        }
        if (t instanceof TypeFun f) {
            return occurs(name, f.from()) || occurs(name, f.to());
        }
        return false; // TypeNumber, TypeBool, TypeString have no variables
    }

    // --- Substitution application ---

    private Type apply(Substitution sub, Type t) {
        if (t instanceof TypeVar v) {
            Type replacement = sub.map().get(v.name());
            return replacement != null ? replacement : v;
        }
        if (t instanceof TypeFun f) {
            return new TypeFun(apply(sub, f.from()), apply(sub, f.to()));
        }
        return t;
    }

    private Map<String, TypeScheme> apply(Substitution sub, Map<String, TypeScheme> env) {
        Map<String, TypeScheme> result = new HashMap<>();
        for (Map.Entry<String, TypeScheme> entry : env.entrySet()) {
            result.put(entry.getKey(),
                    new TypeScheme(entry.getValue().params(), apply(sub, entry.getValue().body())));
        }
        return result;
    }

    // --- Composition: s1 ∘ s2 means apply s2 then s1 ---

    private Substitution compose(Substitution s1, Substitution s2) {
        Map<String, Type> result = new HashMap<>(s2.map());
        // Apply s1 to all values in s2
        for (Map.Entry<String, Type> entry : s2.map().entrySet()) {
            result.put(entry.getKey(), apply(s1, entry.getValue()));
        }
        // Add entries from s1 that are not in s2
        for (Map.Entry<String, Type> entry : s1.map().entrySet()) {
            if (!s2.map().containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        // Remove identity mappings
        result.entrySet().removeIf(e ->
                e.getValue() instanceof TypeVar v && v.name().equals(e.getKey()));
        return new Substitution(result);
    }

    // --- Generalization and instantiation ---

    private TypeScheme generalize(Map<String, TypeScheme> env, Type t) {
        Set<String> freeInT = freeVars(t);
        Set<String> freeInEnv = new HashSet<>();
        for (TypeScheme scheme : env.values()) {
            freeInEnv.addAll(freeVars(scheme.body()));
        }
        freeInT.removeAll(freeInEnv);
        return new TypeScheme(new ArrayList<>(freeInT), t);
    }

    private Set<String> freeVars(Type t) {
        Set<String> vars = new HashSet<>();
        collectFreeVars(t, vars);
        return vars;
    }

    private void collectFreeVars(Type t, Set<String> vars) {
        if (t instanceof TypeVar v) {
            vars.add(v.name());
        } else if (t instanceof TypeFun f) {
            collectFreeVars(f.from(), vars);
            collectFreeVars(f.to(), vars);
        }
    }

    private Type instantiate(TypeScheme scheme) {
        if (scheme.params().isEmpty()) {
            return scheme.body();
        }
        List<TypeVar> freshVars = new ArrayList<>();
        for (String param : scheme.params()) {
            freshVars.add(freshVar());
        }
        return replaceParams(scheme.body(), scheme.params(), freshVars);
    }

    private Type replaceParams(Type t, List<String> params, List<TypeVar> freshVars) {
        if (t instanceof TypeVar v) {
            int idx = params.indexOf(v.name());
            if (idx >= 0) {
                return freshVars.get(idx);
            }
            return v;
        }
        if (t instanceof TypeFun f) {
            return new TypeFun(
                    replaceParams(f.from(), params, freshVars),
                    replaceParams(f.to(), params, freshVars));
        }
        return t;
    }
}
