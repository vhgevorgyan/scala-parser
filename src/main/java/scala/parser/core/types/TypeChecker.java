package scala.parser.core.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import scala.parser.core.ParserError;
import scala.parser.core.ast.*;

public class TypeChecker {

    private record Substitution(Map<String, Type> map) {
        public Substitution() { this(Map.of()); }
        public Substitution(String var, Type type) { this(Map.of(var, type)); }
    }

    private record InferResult(Substitution sub, Type type) {}

    private int varCounter = 0;
    private final List<ParserError> errors;
    private final Map<String, TypeScheme> env = new HashMap<>();
    private final Map<ASTNode, Type> inferredTypes = new HashMap<>();
    private final Map<String, DataDeclNode> dataTypes = new HashMap<>();

    public TypeChecker(List<ParserError> errors) {
        this.errors = errors;
        initBuiltins();
    }

    private void initBuiltins() {
        Type num = new TypeNumber();
        Type bool = new TypeBool();
        env.put("succ", new TypeScheme(List.of(), new TypeFun(num, num)));
        env.put("pred", new TypeScheme(List.of(), new TypeFun(num, num)));
        env.put("iszero", new TypeScheme(List.of(), new TypeFun(num, new TypeBool())));
        env.put("print", new TypeScheme(List.of(), new TypeFun(num, num)));
    }

    public Map<ASTNode, Type> getInferredTypes() { return inferredTypes; }

    public void check(ASTNode node) {
        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) check(stmt);
            return;
        }

        if (node instanceof DataDeclNode data) {
            registerDataDecl(data);
            return;
        }

        if (node instanceof LetStatementNode let) {
            InferResult result;
            Substitution sub;

            if (let.isRec) {
                if (let.value instanceof IdentifierNode id && id.name.equals(let.name)) {
                    errors.add(new ParserError("Recursive let '" + let.name + "' directly refers to itself; wrap body in a lambda", let.line, let.column));
                    return;
                }
                TypeVar recType = freshVar();
                env.put(let.name, new TypeScheme(List.of(), recType));
                result = inferExpr(env, let.value);
                sub = result.sub;
                Type type = apply(sub, result.type);
                try {
                    sub = compose(unify(apply(sub, recType), type), sub);
                } catch (TypeErrorException e) {
                    errors.add(new ParserError("Type mismatch in recursive let '" + let.name + "'", let.line, let.column));
                    return;
                }
                type = apply(sub, type);
                applySubToEnv(sub);
                TypeScheme scheme = generalize(env, type);
                env.put(let.name, scheme);
                inferredTypes.put(let.value, type);
                inferredTypes.put(let, scheme.body());
            } else {
                result = inferExpr(env, let.value);
                sub = result.sub;
                Type type = apply(sub, result.type);

                if (let.declaredType != null) {
                    try {
                        sub = compose(unify(type, let.declaredType), sub);
                    } catch (TypeErrorException e) {
                        String detail = e.left != null ? "cannot unify " + e.left + " with " + e.right
                            : "declared " + let.declaredType + " vs inferred " + type;
                        errors.add(new ParserError(
                                "Type annotation mismatch in '" + let.name + "': " + detail,
                                let.line, let.column));
                        return;
                    }
                    type = apply(sub, type);
                }

                applySubToEnv(sub);
                TypeScheme scheme = generalize(env, type);
                env.put(let.name, scheme);
                inferredTypes.put(let.value, type);
                inferredTypes.put(let, scheme.body());
            }
            return;
        }

        if (node instanceof ExpressionNode expr) {
            inferExpr(env, expr);
        }
    }

    private void registerDataDecl(DataDeclNode data) {
        List<Type> typeArgs = data.params.stream().map(p -> (Type) new TypeVar(p)).toList();
        Type selfType = new TypeConstructor(data.name, typeArgs);

        for (DataDeclNode.ConstructorDef ctor : data.constructors) {
            List<Type> paramTypes = new ArrayList<>();
            for (String arg : ctor.argTypes()) {
                int idx = data.params.indexOf(arg);
                if (idx >= 0) {
                    paramTypes.add(new TypeVar(data.params.get(idx)));
                } else {
                    paramTypes.add(resolveTypeName(arg));
                }
            }

            Type ctorType;
            if (paramTypes.isEmpty()) {
                ctorType = selfType;
            } else {
                ctorType = new TypeFun(paramTypes.get(paramTypes.size() - 1), selfType);
                for (int i = paramTypes.size() - 2; i >= 0; i--) {
                    ctorType = new TypeFun(paramTypes.get(i), ctorType);
                }
            }

            Set<String> freeVars = new HashSet<>();
            for (String p : data.params) freeVars.add(p);
            env.put(ctor.name(), new TypeScheme(new ArrayList<>(freeVars), ctorType));
        }

        dataTypes.put(data.name, data);
    }

    private Type resolveTypeName(String name) {
        return switch (name) {
            case "Number" -> new TypeNumber();
            case "Bool" -> new TypeBool();
            case "String" -> new TypeString();
            default -> new TypeConstructor(name, List.of());
        };
    }

    private void applySubToEnv(Substitution sub) {
        List<Map.Entry<String, TypeScheme>> entries = new ArrayList<>(env.entrySet());
        for (Map.Entry<String, TypeScheme> entry : entries) {
            env.put(entry.getKey(), new TypeScheme(entry.getValue().params(), apply(sub, entry.getValue().body())));
        }
    }

    private InferResult inferExpr(Map<String, TypeScheme> env, ExpressionNode expr) {
        if (expr instanceof StringLiteralNode) {
            return new InferResult(new Substitution(), new TypeString());
        }

        if (expr instanceof LiteralNode) {
            return new InferResult(new Substitution(), new TypeNumber());
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

        if (expr instanceof UnaryExprNode un) {
            InferResult opResult = inferExpr(env, un.operand);
            Substitution s1 = opResult.sub;
            Type t1 = opResult.type;
            try {
                Substitution s2 = unify(apply(s1, t1), new TypeBool());
                return new InferResult(compose(s2, s1), new TypeBool());
            } catch (TypeErrorException e) {
                String got = apply(s1, t1).toString();
                errors.add(new ParserError("Operator '" + un.operator + "' requires Bool, got " + got,
                        un.operand.line, un.operand.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
        }

        if (expr instanceof IfNode i) {
            InferResult cond = inferExpr(env, i.condition);
            Substitution s1 = cond.sub;
            Type t1 = cond.type;
            Substitution s2;
            try { s2 = unify(apply(s1, t1), new TypeBool()); }
            catch (TypeErrorException e) {
                String condType = apply(s1, t1).toString();
                String detail = e.left != null && e.right != null
                    ? "cannot unify " + e.left + " with " + e.right
                    : "got " + condType;
                errors.add(new ParserError("If condition must be Bool, " + detail, i.condition.line, i.condition.column));
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
            try { s5 = unify(appliedTThen, tElse); }
            catch (TypeErrorException e) {
                String thenS = appliedTThen.toString(), elseS = tElse.toString();
                String detail = e.left != null && e.right != null
                    ? "cannot unify " + e.left + " with " + e.right
                    : "then:" + thenS + " else:" + elseS;
                errors.add(new ParserError("If branches must have same type, " + detail,
                    i.thenBranch.line, i.thenBranch.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            return new InferResult(compose(s5, compose(s4, compose(s3, compose(s2, s1)))), apply(s5, appliedTThen));
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

            // String concatenation: allow + on strings
            if (op.equals("+") && appliedT1 instanceof TypeString) {
                Substitution s3;
                try { s3 = unify(apply(s2, t1), new TypeString()); }
                catch (TypeErrorException e) {
                    errors.add(new ParserError("Operator '+' requires string, got " + appliedT1, bin.left.line, bin.left.column));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                Substitution s4;
                try { s4 = unify(apply(s3, t2), new TypeString()); }
                catch (TypeErrorException e) {
                    errors.add(new ParserError("Operator '+' requires string, got " + apply(s3, t2), bin.right.line, bin.right.column));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                return new InferResult(compose(s4, compose(s3, compose(s2, s1))), new TypeString());
            }

            Type operandType = isLogical ? new TypeBool() : new TypeNumber();
            Substitution s3;
            try { s3 = unify(appliedT1, operandType); }
            catch (TypeErrorException e) {
                String detail = e.left != null ? "cannot unify " + e.left + " with " + e.right : "got " + appliedT1;
                errors.add(new ParserError("Operator '" + op + "' requires " + operandType + ", " + detail, bin.left.line, bin.left.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            Substitution s4;
            try { s4 = unify(apply(s3, t2), operandType); }
            catch (TypeErrorException e) {
                String detail = e.left != null ? "cannot unify " + e.left + " with " + e.right : "got " + apply(s3, t2);
                errors.add(new ParserError("Operator '" + op + "' requires " + operandType + ", " + detail, bin.right.line, bin.right.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            Type resultType = isArithmetic ? new TypeNumber() : new TypeBool();
            return new InferResult(compose(s4, compose(s3, compose(s2, s1))), resultType);
        }

        if (expr instanceof CaseNode caseNode) {
            InferResult exprResult = inferExpr(env, caseNode.expr);
            Substitution s = exprResult.sub;
            Type exprType = exprResult.type;

            Type resultType = null;
            for (CaseNode.CaseBranch branch : caseNode.branches) {
                TypeScheme ctorScheme = env.get(branch.constructor());
                if (ctorScheme == null) {
                    errors.add(new ParserError("Unknown constructor '" + branch.constructor() + "'", caseNode.line, caseNode.column, branch.constructor().length()));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }

                Type ctorType = instantiate(ctorScheme);
                Map<String, TypeScheme> branchEnv = new HashMap<>(apply(s, env));

                // For constructors with multiple args, add fresh binding types
                for (int i = 0; i < branch.bindings().size(); i++) {
                    String binding = branch.bindings().get(i).name();
                    TypeVar bindingType = freshVar();
                    branchEnv.put(binding, new TypeScheme(List.of(), bindingType));
                }

                // Decompose constructor type: A -> B -> Ctor A B
                // argTypes = [A, B], appliedCtor = Ctor A B
                Type appliedCtor = ctorType;
                List<Type> argTypes = new ArrayList<>();
                while (appliedCtor instanceof TypeFun f) {
                    argTypes.add(f.from());
                    appliedCtor = f.to();
                }

                Substitution matchSub;
                try {
                    matchSub = unify(apply(s, appliedCtor), apply(s, exprType));
                } catch (TypeErrorException e) {
                    String detail = e.left != null ? "cannot unify " + e.left + " with " + e.right : "";
                    errors.add(new ParserError(
                            "Constructor '" + branch.constructor() + "' incompatible with scrutinee type " + apply(s, exprType) + (detail.isEmpty() ? "" : " (" + detail + ")"),
                            caseNode.expr.line, caseNode.expr.column));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }

                for (int i = 0; i < branch.bindings().size() && i < argTypes.size(); i++) {
                    String binding = branch.bindings().get(i).name();
                    Type bindingType = apply(matchSub, apply(s, argTypes.get(i)));
                    branchEnv.put(binding, new TypeScheme(List.of(), bindingType));
                }

                InferResult branchResult = inferExpr(branchEnv, branch.body());
                s = compose(branchResult.sub, compose(matchSub, s));
                Type branchType = branchResult.type;

                if (resultType == null) {
                    resultType = branchType;
                } else {
                    try {
                        Substitution unif = unify(apply(s, resultType), apply(s, branchType));
                        s = compose(unif, s);
                    } catch (TypeErrorException e) {
                        String detail = e.left != null ? "cannot unify " + e.left + " with " + e.right : "got " + apply(s, resultType) + " vs " + apply(s, branchType);
                        errors.add(new ParserError(
                                "Case branches must have the same type, " + detail,
                                branch.body().line, branch.body().column));
                        return new InferResult(new Substitution(), new TypeVar("?"));
                    }
                }
            }

            Type resolved = apply(s, exprType);
            if (resolved instanceof TypeConstructor tc) {
                DataDeclNode decl = dataTypes.get(tc.name());
                if (decl != null) {
                    Set<String> matched = new HashSet<>();
                    for (var br : caseNode.branches) matched.add(br.constructor());
                    for (var ctor : decl.constructors) {
                        if (!matched.contains(ctor.name())) {
                            errors.add(new ParserError(
                                "Non-exhaustive pattern: missing constructor '" + ctor.name() + "' for type '" + decl.name + "'",
                                caseNode.line, caseNode.column));
                        }
                    }
                }
            }
            return new InferResult(s, resultType != null ? apply(s, resultType) : new TypeVar("?"));
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
            try { s3 = unify(appliedT1, new TypeFun(t2, resultType)); }
            catch (TypeErrorException e) {
                String detail = e.left != null
                    ? "cannot unify " + e.left + " with " + e.right
                    : "cannot apply " + appliedT1 + " to " + t2;
                errors.add(new ParserError("Type error: " + detail, app.function.line, app.function.column));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            return new InferResult(compose(s3, compose(s2, s1)), apply(s3, resultType));
        }

        errors.add(new ParserError("Unknown expression: " + expr.getClass().getSimpleName(), expr.line, expr.column));
        return new InferResult(new Substitution(), new TypeVar("?"));
    }

    private TypeVar freshVar() { return new TypeVar("t" + varCounter++); }

    private Substitution unify(Type a, Type b) throws TypeErrorException {
        if (a instanceof TypeVar va) return unifyVar(va.name(), b);
        if (b instanceof TypeVar vb) return unifyVar(vb.name(), a);
        if (a instanceof TypeNumber && b instanceof TypeNumber) return new Substitution();
        if (a instanceof TypeBool && b instanceof TypeBool) return new Substitution();
        if (a instanceof TypeString && b instanceof TypeString) return new Substitution();
        if (a instanceof TypeFun fa && b instanceof TypeFun fb) {
            Substitution s1 = unify(fa.from(), fb.from());
            Substitution s2 = unify(apply(s1, fa.to()), apply(s1, fb.to()));
            return compose(s2, s1);
        }
        if (a instanceof TypeConstructor ca && b instanceof TypeConstructor cb) {
            if (!ca.name().equals(cb.name()) || ca.args().size() != cb.args().size()) {
                throw new TypeErrorException("Cannot unify " + a + " with " + b, a, b);
            }
            Substitution result = new Substitution();
            for (int i = 0; i < ca.args().size(); i++) {
                result = compose(unify(apply(result, ca.args().get(i)), apply(result, cb.args().get(i))), result);
            }
            return result;
        }
        throw new TypeErrorException("Cannot unify " + a + " with " + b, a, b);
    }

    private Substitution unifyVar(String name, Type t) throws TypeErrorException {
        if (t instanceof TypeVar v && v.name().equals(name)) return new Substitution();
        if (occurs(name, t)) throw new TypeErrorException("Occurs check: " + name + " in " + t, new TypeVar(name), t);
        return new Substitution(name, t);
    }

    private boolean occurs(String name, Type t) {
        if (t instanceof TypeVar v) return v.name().equals(name);
        if (t instanceof TypeFun f) return occurs(name, f.from()) || occurs(name, f.to());
        if (t instanceof TypeConstructor c) {
            for (Type arg : c.args()) {
                if (occurs(name, arg)) return true;
            }
        }
        return false;
    }

    private Type apply(Substitution sub, Type t) {
        if (t instanceof TypeVar v) {
            Type replacement = sub.map().get(v.name());
            return replacement != null ? replacement : v;
        }
        if (t instanceof TypeFun f) return new TypeFun(apply(sub, f.from()), apply(sub, f.to()));
        if (t instanceof TypeConstructor c) {
            List<Type> newArgs = c.args().stream().map(a -> apply(sub, a)).toList();
            return new TypeConstructor(c.name(), newArgs);
        }
        return t;
    }

    private Map<String, TypeScheme> apply(Substitution sub, Map<String, TypeScheme> env) {
        Map<String, TypeScheme> result = new HashMap<>();
        for (Map.Entry<String, TypeScheme> entry : env.entrySet()) {
            result.put(entry.getKey(), new TypeScheme(entry.getValue().params(), apply(sub, entry.getValue().body())));
        }
        return result;
    }

    private Substitution compose(Substitution s1, Substitution s2) {
        Map<String, Type> result = new HashMap<>(s2.map());
        for (Map.Entry<String, Type> entry : s2.map().entrySet()) {
            result.put(entry.getKey(), apply(s1, entry.getValue()));
        }
        for (Map.Entry<String, Type> entry : s1.map().entrySet()) {
            if (!s2.map().containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        result.entrySet().removeIf(e -> e.getValue() instanceof TypeVar v && v.name().equals(e.getKey()));
        return new Substitution(result);
    }

    private TypeScheme generalize(Map<String, TypeScheme> env, Type t) {
        Set<String> freeInT = freeVars(t);
        Set<String> freeInEnv = new HashSet<>();
        for (TypeScheme scheme : env.values()) freeInEnv.addAll(freeVars(scheme.body()));
        freeInT.removeAll(freeInEnv);
        return new TypeScheme(new ArrayList<>(freeInT), t);
    }

    private Set<String> freeVars(Type t) {
        Set<String> vars = new HashSet<>();
        collectFreeVars(t, vars);
        return vars;
    }

    private void collectFreeVars(Type t, Set<String> vars) {
        if (t instanceof TypeVar v) vars.add(v.name());
        else if (t instanceof TypeFun f) { collectFreeVars(f.from(), vars); collectFreeVars(f.to(), vars); }
        else if (t instanceof TypeConstructor c) { for (Type a : c.args()) collectFreeVars(a, vars); }
    }

    private Type instantiate(TypeScheme scheme) {
        if (scheme.params().isEmpty()) return scheme.body();
        List<TypeVar> freshVars = new ArrayList<>();
        for (String param : scheme.params()) freshVars.add(freshVar());
        return replaceParams(scheme.body(), scheme.params(), freshVars);
    }

    private Type replaceParams(Type t, List<String> params, List<TypeVar> freshVars) {
        if (t instanceof TypeVar v) {
            int idx = params.indexOf(v.name());
            if (idx >= 0) return freshVars.get(idx);
            return v;
        }
        if (t instanceof TypeFun f) return new TypeFun(replaceParams(f.from(), params, freshVars), replaceParams(f.to(), params, freshVars));
        if (t instanceof TypeConstructor c) {
            return new TypeConstructor(c.name(), c.args().stream().map(a -> replaceParams(a, params, freshVars)).toList());
        }
        return t;
    }
}
