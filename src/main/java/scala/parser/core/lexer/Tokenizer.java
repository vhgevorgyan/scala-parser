package scala.parser.core.lexer;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    private final String input;
    private int pos = 0;
    private int line = 0;
    private int column = 0;

    public Tokenizer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (!isAtEnd()) {
            char c = peek();

            // Whitespace
            if (Character.isWhitespace(c)) {
                skipWhitespace();
                continue;
            }

            // Numbers
            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            // Identifiers / Keywords
            if (Character.isLetter(c)) {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            // ------------------------------------
            // Multi-character operators (MÜSSEN zuerst kommen)
            // ------------------------------------
            // ==
            if (c == '=' && peekNext() == '=') {
                tokens.add(new Token(TokenType.EQ, "==", line, column));
                advance();
                advance();
                continue;
            }

            // !=
            if (c == '!' && peekNext() == '=') {
                tokens.add(new Token(TokenType.NEQ, "!=", line, column));
                advance();
                advance();
                continue;
            }

            // <=
            if (c == '<' && peekNext() == '=') {
                tokens.add(new Token(TokenType.LE, "<=", line, column));
                advance();
                advance();
                continue;
            }

            // >=
            if (c == '>' && peekNext() == '=') {
                tokens.add(new Token(TokenType.GE, ">=", line, column));
                advance();
                advance();
                continue;
            }

            // ------------------------------------
            // Lambda-Kalkül Erweiterung
            // ------------------------------------
            // ARROW: ->
            if (c == '-' && peekNext() == '>') {
                tokens.add(new Token(TokenType.ARROW, "->", line, column));
                advance();
                advance();
                continue;
            }

            // BACKSLASH: \
            if (c == '\\') {
                tokens.add(new Token(TokenType.BACKSLASH, "\\", line, column));
                advance();
                continue;
            }

            // ------------------------------------
            // Single-character tokens
            // ------------------------------------
            switch (c) {
                case '+':
                    tokens.add(makeToken(TokenType.PLUS));
                    advance();
                    break;
                case '-':
                    tokens.add(makeToken(TokenType.MINUS));
                    advance();
                    break;
                case '*':
                    tokens.add(makeToken(TokenType.STAR));
                    advance();
                    break;
                case '/':
                    tokens.add(makeToken(TokenType.SLASH));
                    advance();
                    break;

                case '=':
                    tokens.add(makeToken(TokenType.EQUAL));
                    advance();
                    break;

                case '<':
                    tokens.add(makeToken(TokenType.LT));
                    advance();
                    break;
                case '>':
                    tokens.add(makeToken(TokenType.GT));
                    advance();
                    break;

                case '(':
                    tokens.add(makeToken(TokenType.LPAREN));
                    advance();
                    break;
                case ')':
                    tokens.add(makeToken(TokenType.RPAREN));
                    advance();
                    break;

                default:
                    throw new RuntimeException("Unexpected character '" + c + "' at " + line + ":" + column);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    // ------------------------------------
    // Helper methods
    // ------------------------------------
    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private char peek() {
        return input.charAt(pos);
    }

    private char peekNext() {
        if (pos + 1 >= input.length()) {
            return '\0';
        }
        return input.charAt(pos + 1);
    }

    private void advance() {
        if (peek() == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }
        pos++;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            advance();
        }
    }

    private Token makeToken(TokenType type) {
        return new Token(type, String.valueOf(peek()), line, column);
    }

    private Token readNumber() {
        int start = pos;
        int startCol = column;

        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }

        String text = input.substring(start, pos);
        return new Token(TokenType.NUMBER, text, line, startCol);
    }

    private Token readIdentifierOrKeyword() {
        int start = pos;
        int startCol = column;

        while (!isAtEnd() && Character.isLetterOrDigit(peek())) {
            advance();
        }

        String text = input.substring(start, pos);

        // Keywords
        switch (text) {
            case "let":
                return new Token(TokenType.LET, text, line, startCol);
            case "true":
                return new Token(TokenType.TRUE, text, line, startCol);
            case "false":
                return new Token(TokenType.FALSE, text, line, startCol);
            case "if":
                return new Token(TokenType.IF, text, line, startCol);
            case "then":
                return new Token(TokenType.THEN, text, line, startCol);
            case "else":
                return new Token(TokenType.ELSE, text, line, startCol);
        }

        return new Token(TokenType.IDENT, text, line, startCol);
    }
}
