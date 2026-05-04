package scala.parser.core.parser;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
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
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.TokenType;

public class Parser {

    private final List<Token> tokens;
    private int pos = 0;
    private final List<ParserError> errors = new ArrayList<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ParserResult parse() {
        List<ASTNode> statements = new ArrayList<>();

        while (!isAtEnd()) {
            statements.add(parseStatement());
        }

        ProgramNode program = new ProgramNode(statements);
        return new ParserResult(program, errors);
    }

    // ---------------------------------------------------------
    // Basic helpers
    // ---------------------------------------------------------
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token advance() {
        if (!isAtEnd()) {
            pos++;
        }
        return previous();
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private boolean match(TokenType type) {
        if (peek().type == type) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType type, String message) {
        if (peek().type == type) {
            return advance();
        }
        error(message, peek());
        return advance();
    }

    private void error(String message, Token token) {
        errors.add(new ParserError(message, token.line, token.column));
    }

    // ---------------------------------------------------------
    // Statements
    // ---------------------------------------------------------
    private ASTNode parseStatement() {
        if (match(TokenType.LET)) {
            return parseLetStatement();
        }
        return parseExpression();
    }

    private ASTNode parseLetStatement() {
        Token name = expect(TokenType.IDENT, "Expected identifier after 'let'");
        expect(TokenType.EQUAL, "Expected '=' after identifier");

        ExpressionNode value = parseExpression();

        return new LetStatementNode(
                name.text,
                value,
                name.line,
                name.column
        );
    }

    // ---------------------------------------------------------
    // Expressions (Lambda-Kalkül + Arithmetik)
    // ---------------------------------------------------------
    private ExpressionNode parseExpression() {
        // Lambda hat höchste Priorität
        if (match(TokenType.BACKSLASH)) {
            return parseLambda();
        }
        if (match(TokenType.IF)) {
            ExpressionNode cond = parseExpression();
            consume(TokenType.THEN, "Expected 'then'");
            ExpressionNode thenBranch = parseExpression();
            consume(TokenType.ELSE, "Expected 'else'");
            ExpressionNode elseBranch = parseExpression();
            return new IfNode(cond, thenBranch, elseBranch);
        }

        return parseBinary();
    }

    private ExpressionNode parseLambda() {
        Token param = expect(TokenType.IDENT, "Expected parameter name after '\\'");
        expect(TokenType.ARROW, "Expected '->' after lambda parameter");

        ExpressionNode body = parseExpression();

        return new LambdaNode(param.text, body, param.line, param.column);
    }

    private ExpressionNode parseBinary() {
        ExpressionNode expr = parseApplication();

        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token operator = previous();
            ExpressionNode right = parseApplication();

            expr = new BinaryExprNode(
                    expr,
                    operator.text,
                    right,
                    operator.line,
                    operator.column
            );
        }

        return expr;
    }

    private ExpressionNode parseApplication() {
        ExpressionNode expr = parsePrimary();

        while (true) {
            TokenType t = peek().type;

            // Ein Ausdruck kann starten mit:
            if (t == TokenType.IDENT
                    || t == TokenType.NUMBER
                    || t == TokenType.LPAREN
                    || t == TokenType.BACKSLASH) {

                ExpressionNode arg = parsePrimary();
                expr = new ApplicationNode(expr, arg, expr.line, expr.column);
            } else {
                break;
            }
        }

        return expr;
    }

    private ExpressionNode parsePrimary() {
        Token token = peek();

        if (match(TokenType.NUMBER)) {
            return new LiteralNode(token.text, token.line, token.column);
        }

        if (match(TokenType.IDENT)) {
            return new IdentifierNode(token.text, token.line, token.column);
        }

        if (match(TokenType.LPAREN)) {
            ExpressionNode expr = parseExpression();
            expect(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }
        if (match(TokenType.TRUE)) {
            return new BoolNode(previous().line, previous().column, true);
        }
        if (match(TokenType.FALSE)) {
            return new BoolNode(previous().line, previous().column, false);
        }

        error("Unexpected token: " + token.text, token);
        advance();
        return new LiteralNode("0", token.line, token.column);
    }
}
