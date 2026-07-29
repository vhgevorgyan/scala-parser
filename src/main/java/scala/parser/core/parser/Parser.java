package scala.parser.core.parser;

import java.util.ArrayList;
import java.util.List;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.config.LanguageConfig;
import scala.parser.core.config.LanguageFeature;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.TokenType;
import scala.parser.core.types.*;

public class Parser {

    private final List<Token> tokens;
    private final LanguageConfig config;
    private int pos = 0;
    private int lastLine = 0;
    private final List<ParserError> errors = new ArrayList<>();

    public Parser(List<Token> tokens) {
        this(tokens, LanguageConfig.allEnabled());
    }

    public Parser(List<Token> tokens, LanguageConfig config) {
        this.tokens = tokens;
        this.config = config;
    }

    private void requireFeature(LanguageFeature feature, Token token) {
        if (!config.isEnabled(feature)) {
            error("Feature '" + feature.name().toLowerCase() + "' is not enabled", token);
        }
    }

    public ParserResult parse() {
        List<ASTNode> statements = new ArrayList<>();

        while (!isAtEnd()) {
            int before = pos;
            ASTNode stmt = parseStatement();
            statements.add(stmt);
            if (pos == before) advance();
        }

        statements = mergeMutualDefs(statements);
        ProgramNode program = new ProgramNode(statements);
        return new ParserResult(program, errors);
    }

    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(pos); }
    private Token advance() { if (!isAtEnd()) { lastLine = peek().line; pos++; } return previous(); }
    private Token previous() { return tokens.get(pos - 1); }
    private Token peek(int ahead) { int idx = pos + ahead; return idx < tokens.size() ? tokens.get(idx) : tokens.get(tokens.size() - 1); }

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
            if (t == TokenType.VAL || t == TokenType.DEF || t == TokenType.ENUM || t == TokenType.TYPE || t == TokenType.IF || t == TokenType.WHILE) return;
            advance();
        }
    }

    // ---------------------------------------------------------
    // Statements
    // ---------------------------------------------------------
    private ASTNode parseStatement() {
        // Skip annotations: @Ident, @Ident(...), @Ident(...).(...) etc.
        while (peek().type == TokenType.AT) {
            skipAnnotation();
        }

        // Skip sealed keyword (consumed, ignored)
        match(TokenType.SEALED);

        // Skip package declarations: package dotted.name { ... }
        if (match(TokenType.PACKAGE)) {
            skipPackage();
            return new LiteralNode("0", -1, -1);
        }

        // Skip import declarations: import dotted.name.{...}
        if (match(TokenType.IMPORT)) {
            skipImport();
            return new LiteralNode("0", -1, -1);
        }

        if (match(TokenType.SEMICOLON)) return new LiteralNode("0", -1, -1);

        if (match(TokenType.WHILE)) {
            requireFeature(LanguageFeature.WHILE_LOOP, previous());
            return parseWhile();
        }

        if (match(TokenType.TYPE)) {
            requireFeature(LanguageFeature.TYPE_ALIAS, previous());
            return parseTypeAlias();
        }

        if (match(TokenType.ENUM)) {
            requireFeature(LanguageFeature.ADT, previous());
            return parseEnumDecl();
        }

        if (match(TokenType.VAL)) {
            requireFeature(LanguageFeature.LET, previous());
            return parseValDef(false);
        }

        if (match(TokenType.DEF)) {
            requireFeature(LanguageFeature.LET, previous());
            return parseValDef(true);
        }

        return parseExpression();
    }

    private void skipAnnotation() {
        advance(); // consume @
        Token name = advance(); // consume annotation name
        if (match(TokenType.LPAREN)) {
            int depth = 1;
            while (!isAtEnd() && depth > 0) {
                if (peek().type == TokenType.LPAREN) depth++;
                else if (peek().type == TokenType.RPAREN) depth--;
                if (depth > 0) advance();
            }
            if (peek().type == TokenType.RPAREN) advance();
        }
    }

    private void skipPackage() {
        while (!isAtEnd() && peek().type == TokenType.IDENT) {
            advance();
            if (peek().type == TokenType.DOT) advance();
            else break;
        }
        if (match(TokenType.LBRACE)) {
            int depth = 1;
            while (!isAtEnd() && depth > 0) {
                if (peek().type == TokenType.LBRACE) depth++;
                else if (peek().type == TokenType.RBRACE) depth--;
                if (depth > 0) advance();
            }
            if (peek().type == TokenType.RBRACE) advance();
        }
    }

    private void skipImport() {
        boolean first = true;
        while (!isAtEnd() && peek().type == TokenType.IDENT) {
            advance();
            if (peek().type == TokenType.DOT) {
                advance();
                if (peek().type == TokenType.UNDERSCORE) { advance(); return; } // import foo._
                if (peek().type == TokenType.LBRACE) {   // import foo.{...}
                    advance();
                    while (!isAtEnd() && peek().type != TokenType.RBRACE) {
                        advance();
                        if (peek().type == TokenType.COMMA) advance();
                    }
                    if (peek().type == TokenType.RBRACE) advance();
                    return;
                }
            } else {
                return;
            }
        }
    }

    private ASTNode parseTypeAlias() {
        Token name = expect(TokenType.IDENT, "Expected type alias name after 'type'");
        expect(TokenType.EQUAL, "Expected '=' after type alias name");
        Type t = parseType();
        return new TypeAliasNode(name.text, t, name.line, name.column);
    }

    private ASTNode parseEnumDecl() {
        Token name = expect(TokenType.IDENT, "Expected type name after 'enum'");
        if (name.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", name.line, name.column); }

        List<String> params = new ArrayList<>();
        if (match(TokenType.LBRACKET)) {
            do {
                Token p = expect(TokenType.IDENT, "Expected type parameter name");
                if (p.type == TokenType.IDENT) params.add(p.text);
            } while (match(TokenType.COMMA));
            expect(TokenType.RBRACKET, "Expected ']' after type parameters");
        }

        expect(TokenType.LBRACE, "Expected '{' after enum name");

        List<EnumDeclNode.ConstructorDef> constructors = new ArrayList<>();
        while (peek().type != TokenType.RBRACE && !isAtEnd()) {
            if (match(TokenType.SEMICOLON)) continue;
            expect(TokenType.CASE, "Expected 'case' in enum body");
            Token ctor = expect(TokenType.IDENT, "Expected constructor name");
            if (ctor.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", ctor.line, ctor.column); }

            List<String> argTypes = new ArrayList<>();
            if (match(TokenType.LPAREN)) {
                do {
                    expect(TokenType.IDENT, "Expected field name"); // field name, ignored
                    if (match(TokenType.COLON)) {
                        argTypes.add(parseType().toString()); // parse type, stringify
                    }
                } while (match(TokenType.COMMA));
                expect(TokenType.RPAREN, "Expected ')' after constructor args");
            }

            constructors.add(new EnumDeclNode.ConstructorDef(ctor.text, argTypes));
        }

        expect(TokenType.RBRACE, "Expected '}' after enum body");
        return new EnumDeclNode(name.text, params, constructors, name.line, name.column);
    }

    private ASTNode parseValDef(boolean isDef) {
        Token name = expect(TokenType.IDENT, "Expected identifier after '" + (isDef ? "def" : "val") + "'");
        if (name.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", name.line, name.column); }

        ExpressionNode body = null;
        Type retType = null;

        // Check for function parameters: def f(x: Type, y: Type)(z: Type): RetType = body
        if (isDef && peek().type == TokenType.LPAREN) {
            int paramLine = name.line;
            int paramCol = name.column;

            // Build nested lambdas OUTERMOST FIRST by accumulating
            List<List<String>> paramGroups = new ArrayList<>();
            List<List<Type>> typeGroups = new ArrayList<>();

            while (peek().type == TokenType.LPAREN) {
                advance(); // consume (
                List<String> paramNames = new ArrayList<>();
                List<Type> paramTypes = new ArrayList<>();
                // Handle empty parameter list: ()
                if (peek().type != TokenType.RPAREN) {
                    do {
                        Token param = expect(TokenType.IDENT, "Expected parameter name");
                        Type paramType = null;
                        if (match(TokenType.COLON)) {
                            paramType = parseType();
                        }
                        paramNames.add(param.text);
                        paramTypes.add(paramType);
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN, "Expected ')' after parameters");
                paramGroups.add(paramNames);
                typeGroups.add(paramTypes);
            }

            if (match(TokenType.COLON)) {
                retType = parseType();
            }

            expect(TokenType.EQUAL, "Expected '='");
            ExpressionNode innerBody = parseExpression();

            // Build nested lambdas from innermost to outermost
            body = innerBody;
            for (int g = paramGroups.size() - 1; g >= 0; g--) {
                List<String> names = paramGroups.get(g);
                List<Type> types = typeGroups.get(g);
                for (int i = names.size() - 1; i >= 0; i--) {
                    body = new LambdaNode(names.get(i), body, types.get(i), paramLine, paramCol);
                }
            }
            return new LetStatementNode(name.text, body, true, retType, name.line, name.column);
        }

        // val x = expr  or  def x = expr (no params)
        Type declaredType = null;
        if (match(TokenType.COLON)) {
            declaredType = parseType();
        }

        expect(TokenType.EQUAL, "Expected '=' after identifier" + (declaredType != null ? " / type annotation" : ""));

        ExpressionNode value = parseExpression();

        return new LetStatementNode(name.text, value, isDef, declaredType, name.line, name.column);
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
        if (match(TokenType.QUESTION)) {
            return new TypeDynamic();
        }
        if (match(TokenType.IDENT)) {
            String text = previous().text;
            return switch (text) {
                case "Int" -> new TypeNumber();
                case "Boolean" -> new TypeBool();
                case "String" -> new TypeString();
                case "Char" -> new TypeChar();
                case "Unit" -> new TypeUnit();
                default -> new TypeConstructor(text, List.of());
            };
        }
        if (match(TokenType.LPAREN)) {
            Type t = parseType();
            if (match(TokenType.COMMA)) {
                requireFeature(LanguageFeature.TUPLE, previous());
                List<Type> types = new ArrayList<>();
                types.add(t);
                do { types.add(parseType()); } while (match(TokenType.COMMA));
                expect(TokenType.RPAREN, "Expected ')' after tuple type");
                return new TypeTuple(types);
            }
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
        if (match(TokenType.IF)) {
            requireFeature(LanguageFeature.IF_UNIT, previous());
            return parseIf();
        }

        ExpressionNode expr = config.isEnabled(LanguageFeature.ARITHMETIC)
            ? parseLogicalOr() : parseApplication();

        if (match(TokenType.MATCH)) {
            requireFeature(LanguageFeature.PATTERN_MATCH, previous());
            return parseMatchBody(expr);
        }

        // Range: expr .. expr
        if (match(TokenType.DOTDOT)) {
            requireFeature(LanguageFeature.RANGE_SUGAR, previous());
            ExpressionNode right = config.isEnabled(LanguageFeature.ARITHMETIC)
                ? parseLogicalOr() : parseApplication();
            expr = new BinaryExprNode(expr, "..", right, expr.line, expr.column);
            if (match(TokenType.COLON)) {
                requireFeature(LanguageFeature.ASCRIPTION, previous());
                Type t = parseType();
                return new AscriptionNode(expr, t, expr.line, expr.column);
            }
            return expr;
        }

        // Type ascription: expr : Type
        if (match(TokenType.COLON)) {
            requireFeature(LanguageFeature.ASCRIPTION, previous());
            Type t = parseType();
            return new AscriptionNode(expr, t, expr.line, expr.column);
        }

        return expr;
    }

    private ExpressionNode parseWhile() {
        expect(TokenType.LPAREN, "Expected '(' after 'while'");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')' after while condition");
        ExpressionNode body = parseExpression();
        return new WhileNode(cond, body, cond.line, cond.column);
    }

    private ExpressionNode parseIf() {
        expect(TokenType.LPAREN, "Expected '(' after 'if'");
        ExpressionNode cond = parseExpression();
        expect(TokenType.RPAREN, "Expected ')' after if condition");

        ExpressionNode thenBranch = parseExpression();
        ExpressionNode elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseExpression();
        }
        return new IfNode(cond, thenBranch, elseBranch);
    }

    private ExpressionNode parseMatchBody(ExpressionNode expr) {
        int line = expr.line;
        int col = expr.column;
        expect(TokenType.LBRACE, "Expected '{' after 'match'");

        List<CaseNode.CaseBranch> branches = new ArrayList<>();
        while (peek().type != TokenType.RBRACE && !isAtEnd()) {
            if (match(TokenType.SEMICOLON)) continue;
            expect(TokenType.CASE, "Expected 'case' in match branch");

            // Optional as-pattern: case x @ pattern =>
            String asName = null;
            if (peek().type == TokenType.IDENT && peek(1).type == TokenType.AT) {
                asName = advance().text;
                advance(); // consume @
            }

            String ctorName = null;
            ExpressionNode literalPattern = null;
            List<CaseNode.Binding> bindings = List.of();

            // Wildcard pattern: case _ =>
            if (match(TokenType.UNDERSCORE)) {
                // ctorName stays null (wildcard)
            }
            // Literal pattern: case 0 =>  or  case true =>
            else if (peek().type == TokenType.NUMBER || peek().type == TokenType.TRUE || peek().type == TokenType.FALSE) {
                literalPattern = parsePrimary();
            }
            else {
                Token ctor = expect(TokenType.IDENT, "Expected constructor name in match branch");
                if (ctor.type != TokenType.IDENT) { synchronize(); return new LiteralNode("0", ctor.line, ctor.column); }

                // Infix pattern: x :: xs =>  desugars to  ::(x, xs)
                if (peek().type == TokenType.IDENT && peek().text.equals("::")) {
                    requireFeature(LanguageFeature.INFIX_CONS, peek());
                    advance(); // consume ::
                    Token right = expect(TokenType.IDENT, "Expected binding name after '::'");
                    ctorName = "::";
                    bindings = List.of(
                        new CaseNode.Binding(ctor.text, ctor.line, ctor.column),
                        new CaseNode.Binding(right.text, right.line, right.column));
                } else {
                    ctorName = ctor.text;
                    List<CaseNode.Binding> b = new ArrayList<>();
                    if (match(TokenType.LPAREN)) {
                        do {
                            Token t = expect(TokenType.IDENT, "Expected binding name");
                            b.add(new CaseNode.Binding(t.text, t.line, t.column));
                        } while (match(TokenType.COMMA));
                        expect(TokenType.RPAREN, "Expected ')' after pattern bindings");
                    }
                    bindings = b;
                }
            }

            // Optional guard: case P if cond =>
            ExpressionNode guard = null;
            if (match(TokenType.IF)) {
                guard = parseExpression();
            }

            expect(TokenType.ARROW, "Expected '=>' after match pattern");
            ExpressionNode body = parseExpression();
            branches.add(new CaseNode.CaseBranch(ctorName, bindings, body, literalPattern, asName, guard));
        }

        expect(TokenType.RBRACE, "Expected '}' after match body");
        return new CaseNode(expr, branches, line, col);
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
            if (t == TokenType.LPAREN) {
                // Don't cross line boundaries
                if (peek().line > expr.line) break;
                // Handle f(x, y, z) → f(x)(y)(z)
                advance(); // consume (
                ExpressionNode firstArg = parseExpression();
                if (peek().type == TokenType.COMMA) {
                    List<ExpressionNode> args = new ArrayList<>();
                    args.add(firstArg);
                    while (match(TokenType.COMMA)) {
                        args.add(parseExpression());
                    }
                    expect(TokenType.RPAREN, "Expected ')' after arguments");
                    for (ExpressionNode arg : args) {
                        expr = new ApplicationNode(expr, arg, expr.line, expr.column);
                    }
                } else {
                    expect(TokenType.RPAREN, "Expected ')' after argument");
                    expr = new ApplicationNode(expr, firstArg, expr.line, expr.column);
                }
            } else if (t == TokenType.LBRACE) {
                if (peek().line > expr.line) break;
                expr = new ApplicationNode(expr, parseUnary(), expr.line, expr.column);
            } else break;
        }
        return expr;
    }

    private ExpressionNode parseUnary() {
        if (match(TokenType.NOT)) {
            requireFeature(LanguageFeature.ARITHMETIC, previous());
            Token op = previous();
            return new UnaryExprNode(op.text, parseUnary(), op.line, op.column);
        }
        if (match(TokenType.MINUS)) {
            requireFeature(LanguageFeature.ARITHMETIC, previous());
            Token op = previous();
            return new UnaryExprNode(op.text, parseUnary(), op.line, op.column);
        }
        return parsePrimary();
    }

    private ExpressionNode parsePrimary() {
        Token token = peek();

        if (match(TokenType.NUMBER)) return new LiteralNode(token.text, token.line, token.column);
        if (match(TokenType.STRING)) return new StringLiteralNode(token.text, token.line, token.column);
        if (match(TokenType.CHAR)) {
            requireFeature(LanguageFeature.CHAR_LITERAL, token);
            String text = token.text;
            return new CharLiteralNode(text.isEmpty() ? 0 : text.charAt(0), token.line, token.column);
        }
        if (match(TokenType.IDENT)) return new IdentifierNode(token.text, token.line, token.column);

        if (match(TokenType.TRUE)) return new BoolNode(previous().line, previous().column, true);
        if (match(TokenType.FALSE)) return new BoolNode(previous().line, previous().column, false);

        if (match(TokenType.LBRACE)) return parseBlock();

        if (match(TokenType.LBRACKET)) {
            requireFeature(LanguageFeature.LIST_SUGAR, previous());
            return parseListLiteral(token);
        }

        if (peek().type == TokenType.LPAREN) {
            if (isLambdaStart()) {
                advance(); // consume (
                return parseLambda();
            }
            Token lparen = advance(); // consume (
            if (peek().type == TokenType.RPAREN) {
                advance();
                requireFeature(LanguageFeature.IF_UNIT, lparen);
                return new UnitLiteralNode(lparen.line, lparen.column);
            }
            ExpressionNode first = parseExpression();
            if (match(TokenType.COMMA)) {
                requireFeature(LanguageFeature.TUPLE, previous());
                List<ExpressionNode> exprs = new ArrayList<>();
                exprs.add(first);
                do { exprs.add(parseExpression()); } while (match(TokenType.COMMA));
                expect(TokenType.RPAREN, "Expected ')' after tuple");
                return new TupleNode(exprs, lparen.line, lparen.column);
            }
            expect(TokenType.RPAREN, "Expected ')' after expression");
            return first;
        }

        error("Unexpected token: " + token.text, token);
        advance();
        return new LiteralNode("0", token.line, token.column);
    }

    private boolean isLambdaStart() {
        if (peek().type != TokenType.LPAREN) return false;
        int saved = pos;
        advance(); // skip (
        if (peek().type != TokenType.IDENT) { pos = saved; return false; }
        advance(); // skip first ident
        // Check for (ident, ... ) =>  or (ident: Type, ... ) =>
        while (peek().type == TokenType.COMMA) {
            advance(); // skip ,
            if (peek().type != TokenType.IDENT) { pos = saved; return false; }
            advance(); // skip ident
        }
        // Now we're at either :Type ), ) =>, or something else
        if (peek().type == TokenType.COLON) {
            advance(); // skip :
            int depth = 1;
            while (!isAtEnd() && depth > 0) {
                TokenType t = peek().type;
                if (t == TokenType.LPAREN) depth++;
                else if (t == TokenType.RPAREN) depth--;
                if (depth > 0) advance();
            }
            if (isAtEnd()) { pos = saved; return false; }
            advance(); // skip )
            boolean result = peek().type == TokenType.ARROW;
            pos = saved;
            return result;
        } else if (peek().type == TokenType.RPAREN) {
            advance(); // skip )
            boolean result = peek().type == TokenType.ARROW;
            pos = saved;
            return result;
        }
        pos = saved;
        return false;
    }

    private ExpressionNode parseLambda() {
        // LPAREN already consumed by caller
        // Parse parameter list: (x: T, y: U, ...)
        List<String> paramNames = new ArrayList<>();
        List<Type> paramTypes = new ArrayList<>();
        int paramLine = peek().line;
        int paramCol = peek().column;
        do {
            Token param = expect(TokenType.IDENT, "Expected parameter name");
            Type declaredType = null;
            if (match(TokenType.COLON)) {
                declaredType = parseType();
            }
            paramNames.add(param.text);
            paramTypes.add(declaredType);
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "Expected ')'");
        expect(TokenType.ARROW, "Expected '=>'");
        ExpressionNode body = parseExpression();
        // Build nested lambdas from last to first parameter
        for (int i = paramNames.size() - 1; i >= 0; i--) {
            body = new LambdaNode(paramNames.get(i), body, paramTypes.get(i), paramLine, paramCol);
        }
        return body;
    }

    private ExpressionNode parseBlock() {
        Token start = previous();
        List<ASTNode> stmts = new ArrayList<>();
        while (!isAtEnd() && peek().type != TokenType.RBRACE) {
            if (match(TokenType.SEMICOLON)) continue;
            stmts.add(parseStatement());
        }
        expect(TokenType.RBRACE, "Expected '}'");
        stmts = mergeMutualDefs(stmts);
        return new BlockNode(stmts, start.line, start.column);
    }

    /** Merge adjacent recursive LetStatementNodes into MutualLetNodes. */
    private List<ASTNode> mergeMutualDefs(List<ASTNode> stmts) {
        List<ASTNode> result = new ArrayList<>();
        int i = 0;
        while (i < stmts.size()) {
            if (stmts.get(i) instanceof LetStatementNode let && let.isRec) {
                List<LetStatementNode> mutual = new ArrayList<>();
                mutual.add(let);
                int j = i + 1;
                // Skip semicolon artifacts between defs
                while (j < stmts.size() && isSemicolonArtifact(stmts.get(j))) j++;
                while (j < stmts.size() && stmts.get(j) instanceof LetStatementNode next && next.isRec) {
                    mutual.add(next);
                    j++;
                    while (j < stmts.size() && isSemicolonArtifact(stmts.get(j))) j++;
                }
                if (mutual.size() > 1) {
                    result.add(new MutualLetNode(new ArrayList<>(mutual), let.line, let.column));
                } else {
                    result.add(let);
                }
                i = j;
            } else {
                if (!isSemicolonArtifact(stmts.get(i))) {
                    result.add(stmts.get(i));
                }
                i++;
            }
        }
        return result;
    }

    private boolean isSemicolonArtifact(ASTNode node) {
        return node instanceof LiteralNode l && l.value.equals("0") && l.line == -1 && l.column == -1;
    }

    private ExpressionNode parseListLiteral(Token start) {
        List<ExpressionNode> elements = new ArrayList<>();
        if (peek().type != TokenType.RBRACKET) {
            do {
                elements.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RBRACKET, "Expected ']' at end of list literal");

        // Build nested Cons calls from right to left
        ExpressionNode result = new IdentifierNode("Nil", start.line, start.column);
        for (int i = elements.size() - 1; i >= 0; i--) {
            result = new ApplicationNode(
                new ApplicationNode(new IdentifierNode("Cons", start.line, start.column),
                    elements.get(i), start.line, start.column),
                result, start.line, start.column);
        }
        return result;
    }
}
