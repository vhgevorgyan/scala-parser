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

            // Single-character tokens
            switch (c) {
                case '+': tokens.add(makeToken(TokenType.PLUS)); advance(); break;
                case '-': tokens.add(makeToken(TokenType.MINUS)); advance(); break;
                case '*': tokens.add(makeToken(TokenType.STAR)); advance(); break;
                case '/': tokens.add(makeToken(TokenType.SLASH)); advance(); break;
                case '=': tokens.add(makeToken(TokenType.EQUAL)); advance(); break;
                case '(': tokens.add(makeToken(TokenType.LPAREN)); advance(); break;
                case ')': tokens.add(makeToken(TokenType.RPAREN)); advance(); break;

                default:
                    throw new RuntimeException("Unexpected character '" + c + "' at " + line + ":" + column);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    // -------------------------
    // Helper methods
    // -------------------------

    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private char peek() {
        return input.charAt(pos);
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

        if (text.equals("let")) {
            return new Token(TokenType.LET, text, line, startCol);
        }

        return new Token(TokenType.IDENT, text, line, startCol);
    }
}
