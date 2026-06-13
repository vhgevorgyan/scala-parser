package scala.parser;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.interpreter.*;
import scala.parser.core.lexer.*;
import scala.parser.core.parser.Parser;
import scala.parser.core.types.TypeChecker;

public class REPL {

    public static void main(String[] args) {
        System.out.println("Scala Parser REPL — :help for commands");
        Env env = Evaluator.initEnv();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine();

            if (line.trim().isEmpty()) continue;

            // Remove trailing ; which is treated as a no-op statement
            if (line.endsWith(";")) line = line.substring(0, line.length() - 1);

            if (line.equals(":quit") || line.equals(":q")) break;

            if (line.equals(":help") || line.equals(":h")) {
                System.out.println("Commands:");
                System.out.println("  :help / :h      Show this help");
                System.out.println("  :quit / :q      Exit REPL");
                System.out.println("  :type <e> / :t  Show type of expression");
                System.out.println("  :env            Show environment");
                System.out.println("  :load <file>    Load and evaluate a file");
                System.out.println("  <expr>          Evaluate expression");
                continue;
            }

            if (line.startsWith(":type ") || line.startsWith(":t ")) {
                String expr = line.startsWith(":t ") ? line.substring(3).trim() : line.substring(6).trim();
                printType(expr);
                continue;
            }

            if (line.startsWith(":env")) {
                System.out.print(env);
                continue;
            }

            if (line.startsWith(":load ")) {
                String path = line.substring(6).trim();
                try {
                    String content = Files.readString(Paths.get(path));
                    System.out.println("Loading: " + path);
                    evaluate(content, env);
                } catch (Exception e) {
                    System.err.println("Error loading file: " + e.getMessage());
                }
                continue;
            }

            evaluate(line, env);
        }
    }

    private static void evaluate(String input, Env env) {
        try {
            Tokenizer tokenizer = new Tokenizer(input);
            List<Token> tokens = tokenizer.tokenize();
            Parser parser = new Parser(tokens);
            ParserResult result = parser.parse();

            if (!result.errors.isEmpty()) {
                for (ParserError err : result.errors) {
                    System.err.println("Error: " + err.message);
                }
                return;
            }

            ProgramNode program = result.ast;

            TypeChecker typeChecker = new TypeChecker(result.errors);
            typeChecker.check(program);

            if (!result.errors.isEmpty()) {
                for (ParserError err : result.errors) {
                    System.err.println("Type error: " + err.message);
                }
                return;
            }

            // Evaluate using a fresh Evaluator for the builtins,
            // but re-evaluate let-bindings against the REPL env
            Evaluator evaluator = new Evaluator();
            Value last = null;

            for (ASTNode stmt : program.statements) {
                if (stmt instanceof DataDeclNode) {
                    System.out.println("=> (data type defined)");
                    continue;
                }

                if (stmt instanceof LetStatementNode let) {
                    Value v = evaluator.evalExpr(let.value, env);
                    env.define(let.name, v);
                    last = v;
                    System.out.println("=> " + v);
                    continue;
                }

                if (stmt instanceof ExpressionNode expr) {
                    last = evaluator.evalExpr(expr, env);
                    System.out.println("=> " + last);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void printType(String input) {
        try {
            Tokenizer tokenizer = new Tokenizer(input);
            List<Token> tokens = tokenizer.tokenize();
            Parser parser = new Parser(tokens);
            ParserResult result = parser.parse();

            if (!result.errors.isEmpty()) {
                for (ParserError err : result.errors) {
                    System.err.println("Parse error: " + err.message);
                }
                return;
            }

            TypeChecker typeChecker = new TypeChecker(result.errors);
            typeChecker.check(result.ast);

            if (!result.errors.isEmpty()) {
                for (ParserError err : result.errors) {
                    System.err.println("Type error: " + err.message);
                }
                return;
            }

            var types = typeChecker.getInferredTypes();
            for (var entry : types.entrySet()) {
                if (entry.getKey() instanceof ExpressionNode) {
                    System.out.println(entry.getValue());
                    return;
                }
            }

            System.out.println("(no type info)");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
