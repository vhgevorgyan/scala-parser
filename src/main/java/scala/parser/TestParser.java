package scala.parser;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import scala.parser.core.*;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;
import scala.parser.core.interpreter.*;

/**
 * CLI tool: parse, typecheck, and evaluate a .sp or .scala file.
 * Usage: java TestParser <file>
 */
public class TestParser {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: gradlew runTestParser --args=\"<file.sp>\"");
            System.exit(1);
        }

        String path = args[0];
        String source = Files.readString(Paths.get(path));

        System.out.println("=== Source ===\n" + source);

        // Tokenize
        System.out.println("=== Tokens ===");
        List<Token> tokens = new Tokenizer(source).tokenize();
        for (var t : tokens) {
            System.out.println("  " + t.type + "(\"" + t.text + "\") Ln " + t.line + " Col " + t.column);
        }

        // Parse
        System.out.println("\n=== Parse ===");
        Parser parser = new Parser(tokens);
        ParserResult result = parser.parse();

        if (!result.errors.isEmpty()) {
            System.out.println("Errors:");
            for (ParserError e : result.errors) {
                System.out.println("  [" + e.line + ":" + e.column + "] " + e.message);
            }
        } else {
            System.out.println("✓ No parse errors");
        }

        // AST dump
        System.out.println("\n=== AST ===");
        System.out.println(astToString(result.ast, 0));

        // Type check
        System.out.println("=== Type Check ===");
        TypeChecker tc = new TypeChecker(result.errors);
        tc.check(result.ast);

        if (!result.errors.isEmpty()) {
            System.out.println("Errors:");
            for (ParserError e : result.errors) {
                System.out.println("  [" + e.line + ":" + e.column + "] " + e.message);
            }
            return;
        }
        System.out.println("✓ No type errors");

        // Show inferred types
        System.out.println("\n=== Inferred Types ===");
        var types = tc.getInferredTypes();
        for (var entry : types.entrySet()) {
            if (entry.getKey() instanceof ASTNode node) {
                System.out.println("  " + node.getClass().getSimpleName()
                    + (node instanceof IdentifierNode id ? "(" + id.name + ")" : "")
                    + " → " + entry.getValue());
            }
        }

        // Evaluate
        System.out.println("\n=== Evaluate ===");
        try {
            Evaluator ev = new Evaluator();
            Value v = ev.evalProgram(result.ast);
            System.out.println("=> " + v);
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
        }
    }

    private static String astToString(ASTNode node, int indent) {
        String pad = "  ".repeat(indent);
        if (node instanceof ProgramNode p) {
            var sb = new StringBuilder(pad + "ProgramNode");
            for (var s : p.statements)
                sb.append("\n").append(astToString(s, indent + 1));
            return sb.toString();
        }
        if (node instanceof LetStatementNode l) {
            String kw = l.isRec ? "Def" : l.isMutable ? "Var" : "Val";
            return pad + kw + " " + l.name
                + (l.declaredType != null ? " : " + l.declaredType : "")
                + "\n" + astToString(l.value, indent + 1);
        }

        if (node instanceof AssignNode a) {
            return pad + "Assign " + a.name
                + "\n" + astToString(a.value, indent + 1);
        }
        if (node instanceof EnumDeclNode d) {
            return pad + "EnumDecl " + d.name + "(" + String.join(" ", d.params) + ")"
                + "\n" + pad + "  Constructors: "
                + d.constructors.stream().map(c -> c.name() + "(" + String.join(" ", c.argTypes()) + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
        }
        if (node instanceof LambdaNode lam) {
            return pad + "Lambda (" + lam.param
                + (lam.declaredType != null ? " : " + lam.declaredType : "") + ")"
                + "\n" + astToString(lam.body, indent + 1);
        }
        if (node instanceof ApplicationNode app) {
            return pad + "Apply"
                + "\n" + astToString(app.function, indent + 1)
                + "\n" + astToString(app.argument, indent + 1);
        }
        if (node instanceof IfNode i) {
            var sb = new StringBuilder(pad + "If");
            sb.append("\n").append(pad).append("  Cond: ").append(astToString(i.condition, indent + 1));
            sb.append("\n").append(pad).append("  Then: ").append(astToString(i.thenBranch, indent + 1));
            if (i.elseBranch != null)
                sb.append("\n").append(pad).append("  Else: ").append(astToString(i.elseBranch, indent + 1));
            return sb.toString();
        }
        if (node instanceof BinaryExprNode b) {
            return pad + "Binary " + b.operator
                + "\n" + astToString(b.left, indent + 1)
                + "\n" + astToString(b.right, indent + 1);
        }
        if (node instanceof UnaryExprNode u) {
            return pad + "Unary " + u.operator
                + "\n" + astToString(u.operand, indent + 1);
        }
        if (node instanceof CaseNode c) {
            var sb = new StringBuilder(pad + "Case");
            sb.append("\n").append(pad).append("  Expr: ").append(astToString(c.expr, indent + 1));
            for (var br : c.branches) {
                if (br.constructor() != null) {
                    sb.append("\n").append(pad).append("  Branch ").append(br.constructor()).append(" [");
                    for (var b : br.bindings()) sb.append(" ").append(b.name());
                    sb.append(" ]");
                } else if (br.literalPattern() != null) {
                    sb.append("\n").append(pad).append("  Branch literal ").append(astToString(br.literalPattern(), 0));
                } else {
                    sb.append("\n").append(pad).append("  Branch _");
                }
                sb.append("\n").append(astToString(br.body(), indent + 2));
            }
            return sb.toString();
        }
        if (node instanceof BlockNode bn) {
            var sb = new StringBuilder(pad + "Block");
            for (var s : bn.statements)
                sb.append("\n").append(astToString(s, indent + 1));
            return sb.toString();
        }
        if (node instanceof LiteralNode lit) return pad + "Lit(" + lit.value + ")";
        if (node instanceof BoolNode b) return pad + "Bool(" + b.value + ")";
        if (node instanceof UnitLiteralNode) return pad + "Unit()";
        if (node instanceof AscriptionNode asc) {
            return pad + "Ascription(" + asc.declaredType + ")\n" + astToString(asc.expr, indent + 1);
        }
        if (node instanceof StringLiteralNode s) return pad + "Str(\"" + s.value + "\")";
        if (node instanceof CharLiteralNode c) return pad + "Char('" + c.value + "')";
        if (node instanceof WhileNode w) {
            return pad + "While\n" + pad + "  Cond: " + astToString(w.condition, indent + 1)
                + "\n" + pad + "  Body: " + astToString(w.body, indent + 1);
        }
        if (node instanceof IdentifierNode id) return pad + "Ident(" + id.name + ")";
        return pad + node.getClass().getSimpleName() + "@" + System.identityHashCode(node);
    }
}
