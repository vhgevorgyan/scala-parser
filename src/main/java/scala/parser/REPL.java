package scala.parser;

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
        System.out.println("Scala Parser REPL — type expressions, :type <expr>, or :quit");
        Env env = Evaluator.initEnv();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) continue;
            if (line.equals(":quit") || line.equals(":q")) break;

            if (line.startsWith(":type ") || line.startsWith(":t ")) {
                String expr = line.startsWith(":t ") ? line.substring(3).trim() : line.substring(6).trim();
                printType(expr);
                continue;
            }

            if (line.startsWith(":env")) {
                System.out.println("Environment: " + env);
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
