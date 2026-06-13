package scala.parser.core.parser;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.TokenType;
import scala.parser.core.types.*;

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
            int before = pos;
            ASTNode stmt = parseStatement();
            statements.add(stmt);
            if (pos == before) advance();
        }

        ProgramNode program = new ProgramNode(statements);
        return new ParserResult(program, errors);
    }

    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(pos); }
    private Token advance() { if (!isAtEnd()) pos++; return previous(); }
    private Token previous() { return tokens.get(pos - 1); }

    private boolean match(TokenType type) {
        if (peek().type == type) { advance(); return true; }
        return false;
    }

    private Token expect(TokenType type, String message) {
        if (peek().type == type) return advance();
        error(message, peek());
        return advance();
    }

    private void error(String message, Token token) {
        errors.add(new ParserError(message, token.line, token.column));
    }

    private void error(String message, Token token, int length) {
        errors.add(new ParserError(message, token.line, token.column, length));
    }

    // ---------------------------------------------------------
    // Synchronization
    // ---------------------------------------------------------
    private void synchronize() {
        while (!isAtEnd()) {
            TokenType t = peek().type;
            if (t == TokenType.LET || t == TokenType.DATA || t == TokenType.IF) return;
            advance();
        }
    }

    // ---------------------------------------------------------
    // Statements
    // ---------------------------------------------------------
    private ASTNode parseStatement() {
        if (match(TokenType.SEMICOLON)) return new LiteralNode("0", -1, -1);

        if (match(TokenType.DATA)) return parseDataDecl();

        if (match(TokenType.LET)) {
            boolean isRec = match(TokenType.REC);
            return parseLetStatement(isRec);
        }

        return parseExpression();
    }

    private ASTNode parseDataDecl() {
        Token name = expect(TokenType.IDENT, "Expected type name after 'data'");
        if (name.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", name.line, name.column); }

        List<String> params = new ArrayList<>();
        while (peek().type == TokenType.IDENT && !peek().text.equals("=")) {
            params.add(advance().text);
        }

        expect(TokenType.EQUAL, "Expected '=' after type name");

        List<DataDeclNode.ConstructorDef> constructors = new ArrayList<>();
        do {
            Token ctor = expect(TokenType.IDENT, "Expected constructor name");
            if (ctor.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", ctor.line, ctor.column); }

            List<String> argTypes = new ArrayList<>();
            while (peek().type == TokenType.IDENT) {
                argTypes.add(advance().text);
            }

            constructors.add(new DataDeclNode.ConstructorDef(ctor.text, argTypes));
        } while (match(TokenType.PIPE));

        return new DataDeclNode(name.text, params, constructors, name.line, name.column);
    }

    private ASTNode parseLetStatement(boolean isRec) {
        Token name = expect(TokenType.IDENT, "Expected identifier after 'let'");
        if (name.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", name.line, name.column); }

        Type declaredType = null;
        if (match(TokenType.COLON)) {
            declaredType = parseType();
        }

        expect(TokenType.EQUAL, "Expected '=' after identifier" + (declaredType != null ? " / type annotation" : ""));

        ExpressionNode value = parseExpression();

        return new LetStatementNode(name.text, value, isRec, declaredType, name.line, name.column);
    }

    // ---------------------------------------------------------
    // Type parsing (for annotations)
    // ---------------------------------------------------------
    private Type parseType() {
        return parseTypeArrow();
    }

    private Type parseTypeArrow() {
        Type base = parseTypeAtom();
        if (match(TokenType.ARROW)) {
            Type right = parseTypeArrow();
            return new TypeFun(base, right);
        }
        return base;
    }

    private Type parseTypeAtom() {
        if (match(TokenType.IDENT)) {
            String text = previous().text;
            return switch (text) {
                case "Number" -> new TypeNumber();
                case "Bool" -> new TypeBool();
                case "String" -> new TypeString();
                default -> new TypeConstructor(text, List.of());
            };
        }
        if (match(TokenType.LPAREN)) {
            Type t = parseType();
            expect(TokenType.RPAREN, "Expected ')' after type");
            return t;
        }
        error("Expected type", peek());
        advance();
        return new TypeNumber();
    }

    // ---------------------------------------------------------
    // Expressions
    // ---------------------------------------------------------
    private ExpressionNode parseExpression() {
        if (match(TokenType.BACKSLASH)) return parseLambda();

        if (match(TokenType.IF)) {
            ExpressionNode cond = parseExpression();
            expect(TokenType.THEN, "Expected 'then'");
            ExpressionNode thenBranch = parseExpression();
            expect(TokenType.ELSE, "Expected 'else'");
            ExpressionNode elseBranch = parseExpression();
            return new IfNode(cond, thenBranch, elseBranch);
        }

        if (match(TokenType.CASE)) return parseCase();

        return parseLogicalOr();
    }

    private ExpressionNode parseCase() {
        ExpressionNode expr = parseExpression();
        expect(TokenType.OF, "Expected 'of' after case expression");

        List<CaseNode.CaseBranch> branches = new ArrayList<>();
        do {
            Token ctor = expect(TokenType.IDENT, "Expected constructor name in case branch");
            if (ctor.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", ctor.line, ctor.column); }

            List<CaseNode.Binding> bindings = new ArrayList<>();
            while (peek().type == TokenType.IDENT && !peek().text.equals("->")) {
                Token t = advance();
                bindings.add(new CaseNode.Binding(t.text, t.line, t.column));
            }

            expect(TokenType.ARROW, "Expected '->' after constructor pattern");
            ExpressionNode body = parseExpression();
            branches.add(new CaseNode.CaseBranch(ctor.text, bindings, body));
        } while (match(TokenType.PIPE));

        return new CaseNode(expr, branches, expr.line, expr.column);
    }

    private ExpressionNode parseLambda() {
        Token param = expect(TokenType.IDENT, "Expected parameter name after '\\'");
        if (param.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", param.line, param.column); }

        Token arrow = expect(TokenType.ARROW, "Expected '->' after lambda parameter");
        if (arrow.type != TokenType.ARROW) { synchronize(); return new LiteralNode("0", param.line, param.column); }

        return new LambdaNode(param.text, parseExpression(), param.line, param.column);
    }

    private ExpressionNode parseLogicalOr() {
        ExpressionNode expr = parseLogicalAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseLogicalAnd(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseLogicalAnd() {
        ExpressionNode expr = parseEquality();
        while (match(TokenType.AND)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseEquality(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseEquality() {
        ExpressionNode expr = parseComparison();
        while (match(TokenType.EQ) || match(TokenType.NEQ)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseComparison(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseComparison() {
        ExpressionNode expr = parseTerm();
        while (match(TokenType.LT) || match(TokenType.GT) || match(TokenType.LE) || match(TokenType.GE)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseTerm(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseTerm() {
        ExpressionNode expr = parseFactor();
        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseFactor(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseFactor() {
        ExpressionNode expr = parseApplication();
        while (match(TokenType.STAR) || match(TokenType.SLASH)) {
            Token op = previous();
            expr = new BinaryExprNode(expr, op.text, parseApplication(), op.line, op.column);
        }
        return expr;
    }

    private ExpressionNode parseApplication() {
        ExpressionNode expr = parseUnary();
        while (true) {
            TokenType t = peek().type;
            if (t == TokenType.IDENT || t == TokenType.NUMBER || t == TokenType.STRING
                    || t == TokenType.LPAREN || t == TokenType.BACKSLASH
                    || t == TokenType.TRUE || t == TokenType.FALSE || t == TokenType.NOT) {
                expr = new ApplicationNode(expr, parseUnary(), expr.line, expr.column);
            } else break;
        }
        return expr;
    }

    private ExpressionNode parseUnary() {
        if (match(TokenType.NOT)) {
            Token op = previous();
            return new UnaryExprNode(op.text, parseUnary(), op.line, op.column);
        }
        return parsePrimary();
    }

    private ExpressionNode parsePrimary() {
        Token token = peek();

        if (match(TokenType.NUMBER)) return new LiteralNode(token.text, token.line, token.column);
        if (match(TokenType.STRING)) return new StringLiteralNode(token.text, token.line, token.column);
        if (match(TokenType.IDENT)) return new IdentifierNode(token.text, token.line, token.column);

        if (match(TokenType.TRUE)) return new BoolNode(previous().line, previous().column, true);
        if (match(TokenType.FALSE)) return new BoolNode(previous().line, previous().column, false);

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
