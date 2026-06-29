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
    private final Map<String, EnumDeclNode> enumTypes = new HashMap<>();
    private final Map<String, Type> typeAliases = new HashMap<>();

    public TypeChecker(List<ParserError> errors) {
        this.errors = errors;
        initBuiltins();
    }

    private void initBuiltins() {
        Type num = new TypeNumber();
        Type bool = new TypeBool();
        Type unit = new TypeUnit();
        env.put("succ", new TypeScheme(List.of(), new TypeFun(num, num)));
        env.put("pred", new TypeScheme(List.of(), new TypeFun(num, num)));
        env.put("iszero", new TypeScheme(List.of(), new TypeFun(num, new TypeBool())));
        env.put("print", new TypeScheme(List.of(), new TypeFun(num, num)));
        // Polymorphic list constructors
        env.put("Nil", new TypeScheme(List.of("a"), new TypeConstructor("List", List.of(new TypeVar("a")))));
        TypeVar a = new TypeVar("a");
        env.put("Cons", new TypeScheme(List.of("a"),
            new TypeFun(a, new TypeFun(
                new TypeConstructor("List", List.of(a)),
                new TypeConstructor("List", List.of(a))))));
    }

    public Map<ASTNode, Type> getInferredTypes() { return inferredTypes; }

    private void reportError(String message, ASTNode location) {
        int span = estimateSpan(location);
        errors.add(new ParserError(message, location.line, location.column, span));
    }

    private void reportTypeError(String context, ASTNode location, Type expected, Type actual) {
        reportError(context + " — expected " + expected + " but got " + actual, location);
    }

    private int estimateSpan(ASTNode node) {
        if (node instanceof IdentifierNode id) return Math.max(1, id.name.length());
        if (node instanceof LiteralNode lit) return Math.max(1, lit.value.length());
        if (node instanceof BoolNode) return 5;
        if (node instanceof StringLiteralNode s) return Math.max(2, s.value.length() + 2);
        if (node instanceof UnitLiteralNode) return 2;
        return 1;
    }

    public void check(ASTNode node) {
        if (node instanceof ProgramNode program) {
            for (ASTNode stmt : program.statements) check(stmt);
            return;
        }

        if (node instanceof EnumDeclNode data) {
            registerEnumDecl(data);
            return;
        }

        if (node instanceof TypeAliasNode alias) {
            typeAliases.put(alias.name, alias.aliasType);
            return;
        }

        if (node instanceof LetStatementNode let) {
            InferResult result;
            Substitution sub;

            if (let.isRec) {
                if (let.value instanceof IdentifierNode id && id.name.equals(let.name)) {
                    reportError("Recursive let '" + let.name + "' directly refers to itself — wrap body in a lambda", let);
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
                    errors.add(new ParserError("Type mismatch in recursive let '" + let.name + "' — expected " + apply(sub, recType) + " but body has type " + type, let.line, let.column));
                    return;
                }
                type = apply(sub, type);
                if (let.declaredType != null) {
                    Type ann = resolveAliases(let.declaredType);
                    Type returnType = type;
                    while (returnType instanceof TypeFun f) { returnType = f.to(); }
                    try {
                        sub = compose(unify(returnType, ann), sub);
                    } catch (TypeErrorException e) {
                        reportTypeError("Type annotation mismatch in '" + let.name + "'", let, ann, returnType);
                        return;
                    }
                    type = apply(sub, type);
                }
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
                    Type ann = resolveAliases(let.declaredType);
                    try {
                        sub = compose(unify(type, ann), sub);
                    } catch (TypeErrorException e) {
                        reportTypeError("Type annotation mismatch in '" + let.name + "'", let, ann, type);
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

        if (node instanceof MutualLetNode mutual) {
            inferMutualGroup(mutual.defs, env);
            return;
        }

        if (node instanceof ExpressionNode expr) {
            InferResult result = inferExpr(env, expr);
            Type type = apply(result.sub, result.type);
            inferredTypes.put(expr, type);
        }
    }

    private void registerEnumDecl(EnumDeclNode data) {
        List<Type> typeArgs = data.params.stream().map(p -> (Type) new TypeVar(p)).toList();
        Type selfType = new TypeConstructor(data.name, typeArgs);

        for (EnumDeclNode.ConstructorDef ctor : data.constructors) {
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

        enumTypes.put(data.name, data);
    }

    private Type resolveTypeName(String name) {
        return switch (name) {
            case "Int", "Number" -> new TypeNumber();
            case "Boolean", "Bool" -> new TypeBool();
            case "String" -> new TypeString();
            case "Unit" -> new TypeUnit();
            default -> {
                Type alias = typeAliases.get(name);
                yield alias != null ? alias : new TypeConstructor(name, List.of());
            }
        };
    }

    private Type resolveAliases(Type t) {
        if (t instanceof TypeConstructor c && c.args().isEmpty()) {
            Type alias = typeAliases.get(c.name());
            return alias != null ? resolveAliases(alias) : t;
        }
        if (t instanceof TypeConstructor c) {
            List<Type> resolved = c.args().stream().map(this::resolveAliases).toList();
            return new TypeConstructor(c.name(), resolved);
        }
        if (t instanceof TypeFun f) {
            return new TypeFun(resolveAliases(f.from()), resolveAliases(f.to()));
        }
        return t;
    }

    private void applySubToEnv(Substitution sub) {
        applySubToEnv(sub, env);
    }

    private void applySubToEnv(Substitution sub, Map<String, TypeScheme> targetEnv) {
        List<Map.Entry<String, TypeScheme>> entries = new ArrayList<>(targetEnv.entrySet());
        for (Map.Entry<String, TypeScheme> entry : entries) {
            targetEnv.put(entry.getKey(), new TypeScheme(entry.getValue().params(), apply(sub, entry.getValue().body())));
        }
    }

    /** Type-check a group of mutually recursive definitions (two-phase). */
    private Type inferMutualGroup(List<LetStatementNode> defs, Map<String, TypeScheme> targetEnv) {
        // Phase 1: allocate fresh type variables for all defs
        Map<String, TypeVar> recVars = new HashMap<>();
        for (LetStatementNode def : defs) {
            TypeVar recType = freshVar();
            recVars.put(def.name, recType);
            targetEnv.put(def.name, new TypeScheme(List.of(), recType));
        }
        Type lastType = null;
        // Phase 2: infer all bodies with all names in scope
        for (LetStatementNode def : defs) {
            TypeVar recType = recVars.get(def.name);
            InferResult result = inferExpr(targetEnv, def.value);
            Substitution sub = result.sub;
            Type type = apply(sub, result.type);
            try {
                sub = compose(unify(apply(sub, recType), type), sub);
            } catch (TypeErrorException e) {
                reportError("Type mismatch in recursive '" + def.name + "' — expected " + apply(sub, recType) + " but body has type " + type, def);
                lastType = new TypeVar("?");
                continue;
            }
            type = apply(sub, type);
            if (def.declaredType != null) {
                Type ann = resolveAliases(def.declaredType);
                Type returnType = type;
                while (returnType instanceof TypeFun f) { returnType = f.to(); }
                try {
                    sub = compose(unify(returnType, ann), sub);
                } catch (TypeErrorException e) {
                    reportTypeError("Type annotation mismatch in '" + def.name + "'", def, ann, returnType);
                    lastType = new TypeVar("?");
                    continue;
                }
                type = apply(sub, type);
            }
            applySubToEnv(sub, targetEnv);
            TypeScheme scheme = generalize(targetEnv, type);
            targetEnv.put(def.name, scheme);
            inferredTypes.put(def.value, type);
            inferredTypes.put(def, scheme.body());
            lastType = scheme.body();
        }
        return lastType;
    }

    private InferResult inferExpr(Map<String, TypeScheme> env, ExpressionNode expr) {
        if (expr instanceof UnitLiteralNode) {
            return new InferResult(new Substitution(), new TypeUnit());
        }

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
                reportError("Undefined variable '" + id.name + "'", id);
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            return new InferResult(new Substitution(), instantiate(scheme));
        }

        if (expr instanceof TupleNode tuple) {
            Substitution s = new Substitution();
            List<Type> memberTypes = new ArrayList<>();
            for (ExpressionNode elem : tuple.elements) {
                InferResult ir = inferExpr(env, elem);
                s = compose(ir.sub, s);
                memberTypes.add(apply(s, ir.type));
                env = apply(s, env);
            }
            return new InferResult(s, new TypeTuple(memberTypes));
        }

        if (expr instanceof UnaryExprNode un) {
            InferResult opResult = inferExpr(env, un.operand);
            Substitution s1 = opResult.sub;
            Type t1 = opResult.type;

            if (un.operator.equals("-")) {
                try {
                    Substitution s2 = unify(apply(s1, t1), new TypeNumber());
                    return new InferResult(compose(s2, s1), new TypeNumber());
                } catch (TypeErrorException e) {
                    String got = apply(s1, t1).toString();
                    reportTypeError("Operator '-'", un.operand, new TypeNumber(), apply(s1, t1));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
            }

            try {
                Substitution s2 = unify(apply(s1, t1), new TypeBool());
                return new InferResult(compose(s2, s1), new TypeBool());
            } catch (TypeErrorException e) {
                String got = apply(s1, t1).toString();
                reportTypeError("Operator '" + un.operator + "'", un.operand, new TypeBool(), apply(s1, t1));
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
                reportTypeError("If condition", i.condition, new TypeBool(), apply(s1, t1));
                s2 = new Substitution();
            }
            Map<String, TypeScheme> envAfterCond = apply(compose(s2, s1), env);
            InferResult thenResult = inferExpr(envAfterCond, i.thenBranch);
            Substitution s3 = thenResult.sub;
            Type tThen = thenResult.type;

            if (i.elseBranch != null) {
                Map<String, TypeScheme> envAfterThen = apply(s3, envAfterCond);
                InferResult elseResult = inferExpr(envAfterThen, i.elseBranch);
                Substitution s4 = elseResult.sub;
                Type tElse = elseResult.type;
                Type appliedTThen = apply(s4, tThen);
                Substitution s5;
                try { s5 = unify(appliedTThen, tElse); }
                catch (TypeErrorException e) {
                    String thenS = appliedTThen.toString(), elseS = tElse.toString();
                    reportError("If branches must have the same type — then: " + thenS + ", else: " + elseS, i.condition);
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                return new InferResult(compose(s5, compose(s4, compose(s3, compose(s2, s1)))), apply(s5, appliedTThen));
            } else {
                return new InferResult(compose(s3, compose(s2, s1)), apply(s3, tThen));
            }
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

            // Range operator: ..  produces (Int, Int)
            if (op.equals("..")) {
                Substitution s3;
                try { s3 = unify(appliedT1, new TypeNumber()); }
                catch (TypeErrorException e) {
                    reportTypeError("Range '..' — left operand", bin.left, new TypeNumber(), appliedT1);
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                Substitution s4;
                try { s4 = unify(apply(s3, t2), new TypeNumber()); }
                catch (TypeErrorException e) {
                    reportTypeError("Range '..' — right operand", bin.right, new TypeNumber(), apply(s3, t2));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                return new InferResult(compose(s4, compose(s3, compose(s2, s1))),
                    new TypeTuple(List.of(new TypeNumber(), new TypeNumber())));
            }

            boolean isArithmetic = op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/");
            boolean isComparison = op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=");
            boolean isLogical = op.equals("&&") || op.equals("||");

            if (!isArithmetic && !isComparison && !isLogical) {
                reportError("Unknown operator '" + op + "'", bin);
                return new InferResult(new Substitution(), new TypeVar("?"));
            }

            // String concatenation: allow + on strings
            if (op.equals("+") && appliedT1 instanceof TypeString) {
                Substitution s3;
                try { s3 = unify(apply(s2, t1), new TypeString()); }
                catch (TypeErrorException e) {
                    reportTypeError("Operator '+' — left operand", bin.left, new TypeString(), apply(s2, t1));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                Substitution s4;
                try { s4 = unify(apply(s3, t2), new TypeString()); }
                catch (TypeErrorException e) {
                    reportTypeError("Operator '+' — right operand", bin.right, new TypeString(), apply(s3, t2));
                    return new InferResult(new Substitution(), new TypeVar("?"));
                }
                return new InferResult(compose(s4, compose(s3, compose(s2, s1))), new TypeString());
            }

            Type operandType = isLogical ? new TypeBool() : new TypeNumber();
            Substitution s3;
            try { s3 = unify(appliedT1, operandType); }
            catch (TypeErrorException e) {
                reportTypeError("Operator '" + op + "' — left operand", bin.left, operandType, appliedT1);
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            Substitution s4;
            try { s4 = unify(apply(s3, t2), operandType); }
            catch (TypeErrorException e) {
                reportTypeError("Operator '" + op + "' — right operand", bin.right, operandType, apply(s3, t2));
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
                // Wildcard or literal pattern
                if (branch.constructor() == null) {
                    if (branch.literalPattern() != null) {
                        // Literal pattern: verify type matches scrutinee
                        InferResult patResult = inferExpr(apply(s, env), branch.literalPattern());
                        s = compose(patResult.sub, s);
                        Type patType = apply(s, patResult.type);
                        try {
                            s = compose(unify(apply(s, exprType), patType), s);
                        } catch (TypeErrorException e) {
                            reportTypeError("Literal pattern in match", branch.literalPattern(), apply(s, exprType), patType);
                            return new InferResult(new Substitution(), new TypeVar("?"));
                        }
                    }
                    // Wildcard (no literal pattern): always matches, no bindings
                    InferResult branchResult = inferExpr(new HashMap<>(apply(s, env)), branch.body());
                    s = compose(branchResult.sub, s);
                    Type branchType = apply(s, branchResult.type);
                    if (resultType == null) {
                        resultType = branchType;
                    } else {
                        try {
                            Substitution unif = unify(apply(s, resultType), apply(s, branchType));
                            s = compose(unif, s);
                        } catch (TypeErrorException e) {
                            String prev = apply(s, resultType).toString(), cur = apply(s, branchType).toString();
                            reportError("Case branches must have the same type — previous: " + prev + ", this branch: " + cur, branch.body());
                            return new InferResult(new Substitution(), new TypeVar("?"));
                        }
                    }
                    continue;
                }

                TypeScheme ctorScheme = env.get(branch.constructor());
                if (ctorScheme == null) {
                    reportError("Unknown constructor '" + branch.constructor() + "'", caseNode.expr);
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
                    reportTypeError("Constructor '" + branch.constructor() + "' in match", caseNode.expr, apply(s, appliedCtor), apply(s, exprType));
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
                        String prev = apply(s, resultType).toString(), cur = apply(s, branchType).toString();
                        reportError("Case branches must have the same type — previous: " + prev + ", this branch: " + cur, branch.body());
                        return new InferResult(new Substitution(), new TypeVar("?"));
                    }
                }
            }

            Type resolved = apply(s, exprType);
            if (resolved instanceof TypeConstructor tc) {
                EnumDeclNode decl = enumTypes.get(tc.name());
                if (decl != null) {
                    Set<String> matched = new HashSet<>();
                    for (var br : caseNode.branches) matched.add(br.constructor());
                    for (var ctor : decl.constructors) {
                        if (!matched.contains(ctor.name())) {
                            reportError("Non-exhaustive pattern: missing constructor '" + ctor.name() + "' for type '" + decl.name + "'", caseNode.expr);
                        }
                    }
                }
            }
            return new InferResult(s, resultType != null ? apply(s, resultType) : new TypeVar("?"));
        }

        if (expr instanceof LambdaNode lam) {
            Type paramType = lam.declaredType != null ? resolveAliases(lam.declaredType) : freshVar();
            Map<String, TypeScheme> extEnv = new HashMap<>(env);
            extEnv.put(lam.param, new TypeScheme(List.of(), paramType));
            InferResult body = inferExpr(extEnv, lam.body);
            Substitution s1 = body.sub;
            Type bodyType = body.type;
            return new InferResult(s1, new TypeFun(apply(s1, paramType), bodyType));
        }

        if (expr instanceof BlockNode block) {
            return inferBlock(block, env);
        }

        if (expr instanceof AscriptionNode asc) {
            InferResult inner = inferExpr(env, asc.expr);
            Type targetType = resolveAliases(asc.declaredType);
            try {
                Substitution s = unify(apply(inner.sub, inner.type), targetType);
                return new InferResult(compose(s, inner.sub), targetType);
            } catch (TypeErrorException e) {
                reportTypeError("Type ascription", asc, targetType, apply(inner.sub, inner.type));
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
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
                if (appliedT1 instanceof TypeFun funType) {
                    reportTypeError("Argument type mismatch in application",
                        app.argument, funType.from(), t2);
                } else {
                    reportError("Cannot apply expression of type " + appliedT1 + " as a function (expected " + t2 + " → ?)", app.function);
                }
                return new InferResult(new Substitution(), new TypeVar("?"));
            }
            return new InferResult(compose(s3, compose(s2, s1)), apply(s3, resultType));
        }

        reportError("Unknown expression: " + expr.getClass().getSimpleName(), expr);
        return new InferResult(new Substitution(), new TypeVar("?"));
    }

    private TypeVar freshVar() { return new TypeVar("t" + varCounter++); }

    private Substitution unify(Type a, Type b) throws TypeErrorException {
        if (a instanceof TypeVar va) return unifyVar(va.name(), b);
        if (b instanceof TypeVar vb) return unifyVar(vb.name(), a);
        if (a instanceof TypeNumber && b instanceof TypeNumber) return new Substitution();
        if (a instanceof TypeBool && b instanceof TypeBool) return new Substitution();
        if (a instanceof TypeString && b instanceof TypeString) return new Substitution();
        if (a instanceof TypeUnit && b instanceof TypeUnit) return new Substitution();
        if (a instanceof TypeTuple ta && b instanceof TypeTuple tb) {
            if (ta.members().size() != tb.members().size()) {
                throw new TypeErrorException("Cannot unify tuple " + a + " with " + b + ": different lengths", a, b);
            }
            Substitution result = new Substitution();
            for (int i = 0; i < ta.members().size(); i++) {
                result = compose(unify(apply(result, ta.members().get(i)), apply(result, tb.members().get(i))), result);
            }
            return result;
        }
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
        if (t instanceof TypeTuple tt) {
            for (Type m : tt.members()) {
                if (occurs(name, m)) return true;
            }
            return false;
        }
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
        if (t instanceof TypeTuple tt) {
            List<Type> newMembers = tt.members().stream().map(m -> apply(sub, m)).toList();
            return new TypeTuple(newMembers);
        }
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
        else if (t instanceof TypeTuple tt) { for (Type m : tt.members()) collectFreeVars(m, vars); }
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
        if (t instanceof TypeTuple tt) {
            return new TypeTuple(tt.members().stream().map(m -> replaceParams(m, params, freshVars)).toList());
        }
        if (t instanceof TypeConstructor c) {
            return new TypeConstructor(c.name(), c.args().stream().map(a -> replaceParams(a, params, freshVars)).toList());
        }
        return t;
    }

    private InferResult inferBlock(BlockNode block, Map<String, TypeScheme> outerEnv) {
        Map<String, TypeScheme> localEnv = new HashMap<>(outerEnv);
        Substitution s = new Substitution();
        Type lastType = new TypeVar("?");
        for (ASTNode stmt : block.statements) {
            if (stmt instanceof LetStatementNode let) {
                InferResult ir;
                if (let.isRec) {
                    if (let.value instanceof IdentifierNode id && id.name.equals(let.name)) {
                        reportError("Recursive val '" + let.name + "' directly refers to itself — wrap body in a lambda", let);
                        return new InferResult(new Substitution(), new TypeVar("?"));
                    }
                    TypeVar recType = freshVar();
                    localEnv.put(let.name, new TypeScheme(List.of(), recType));
                    ir = inferExpr(localEnv, let.value);
                    s = compose(ir.sub, s);
                    Type type = apply(s, ir.type);
                    try {
                        s = compose(unify(apply(s, recType), type), s);
                    } catch (TypeErrorException e) {
                        reportError("Type mismatch in recursive val '" + let.name + "' — expected " + apply(s, recType) + " but body has type " + type, let);
                        return new InferResult(new Substitution(), new TypeVar("?"));
                    }
                    type = apply(s, type);
                    if (let.declaredType != null) {
                        Type ann = resolveAliases(let.declaredType);
                        Type returnType = type;
                        while (returnType instanceof TypeFun f) { returnType = f.to(); }
                        try {
                            s = compose(unify(returnType, ann), s);
                        } catch (TypeErrorException e) {
                            reportTypeError("Type annotation mismatch in '" + let.name + "'", let, ann, returnType);
                            return new InferResult(new Substitution(), new TypeVar("?"));
                        }
                        type = apply(s, type);
                    }
                    localEnv = apply(s, localEnv);
                    TypeScheme scheme = generalize(localEnv, type);
                    localEnv.put(let.name, scheme);
                    inferredTypes.put(let.value, type);
                    inferredTypes.put(let, scheme.body());
                } else {
                    ir = inferExpr(localEnv, let.value);
                    s = compose(ir.sub, s);
                    Type type = apply(s, ir.type);
                    if (let.declaredType != null) {
                        Type ann = resolveAliases(let.declaredType);
                        try {
                            s = compose(unify(type, ann), s);
                        } catch (TypeErrorException e) {
                            reportTypeError("Type annotation mismatch in '" + let.name + "'", let, ann, type);
                            return new InferResult(new Substitution(), new TypeVar("?"));
                        }
                        type = apply(s, type);
                    }
                    localEnv = apply(s, localEnv);
                    TypeScheme scheme = generalize(localEnv, type);
                    localEnv.put(let.name, scheme);
                    inferredTypes.put(let.value, type);
                    inferredTypes.put(let, scheme.body());
                }
                lastType = inferredTypes.get(let);
            } else if (stmt instanceof MutualLetNode mutual) {
                lastType = inferMutualGroup(mutual.defs, localEnv);
            } else if (stmt instanceof ExpressionNode e) {
                InferResult ir = inferExpr(localEnv, e);
                s = compose(ir.sub, s);
                lastType = apply(s, ir.type);
                localEnv = apply(s, localEnv);
            }
        }
        return new InferResult(s, lastType);
    }
}
