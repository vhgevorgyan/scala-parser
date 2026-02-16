package scala.parser.core.parser;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.ASTNode;
import scala.parser.core.ast.BinaryExprNode;
import scala.parser.core.ast.ExpressionNode;
import scala.parser.core.ast.IdentifierNode;
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

    private ExpressionNode parseExpression() {
        ExpressionNode expr = parseTerm();

        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token operator = previous();
            ExpressionNode right = parseTerm();

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

    private ExpressionNode parseTerm() {
        ExpressionNode expr = parseFactor();

        while (match(TokenType.STAR) || match(TokenType.SLASH)) {
            Token operator = previous();
            ExpressionNode right = parseFactor();

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

    private ExpressionNode parseFactor() {
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

        error("Unexpected token: " + token.text, token);
        advance();
        return new LiteralNode("0", token.line, token.column);
    }
}
