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

            if (Character.isWhitespace(c)) {
                skipWhitespace();
                continue;
            }

            if (c == '/' && peekNext() == '/') {
                skipLineComment();
                continue;
            }

            if (c == '/' && peekNext() == '*') {
                skipBlockComment();
                continue;
            }

            if (Character.isDigit(c)) {
                tokens.add(readNumber());
                continue;
            }

            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            if (Character.isLetter(c)) {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            // Multi-character operators
            if (c == '=' && peekNext() == '=') {
                tokens.add(new Token(TokenType.EQ, "==", line, column));
                advance(); advance();
                continue;
            }

            if (c == '!' && peekNext() == '=') {
                tokens.add(new Token(TokenType.NEQ, "!=", line, column));
                advance(); advance();
                continue;
            }

            if (c == '<' && peekNext() == '=') {
                tokens.add(new Token(TokenType.LE, "<=", line, column));
                advance(); advance();
                continue;
            }

            if (c == '>' && peekNext() == '=') {
                tokens.add(new Token(TokenType.GE, ">=", line, column));
                advance(); advance();
                continue;
            }

            if (c == '&' && peekNext() == '&') {
                tokens.add(new Token(TokenType.AND, "&&", line, column));
                advance(); advance();
                continue;
            }

            if (c == '|' && peekNext() == '|') {
                tokens.add(new Token(TokenType.OR, "||", line, column));
                advance(); advance();
                continue;
            }

            if (c == '-' && peekNext() == '>') {
                tokens.add(new Token(TokenType.ARROW, "->", line, column));
                advance(); advance();
                continue;
            }

            if (c == '\\') {
                tokens.add(new Token(TokenType.BACKSLASH, "\\", line, column));
                advance();
                continue;
            }

            switch (c) {
                case '+' -> { tokens.add(makeToken(TokenType.PLUS)); advance(); }
                case '-' -> { tokens.add(makeToken(TokenType.MINUS)); advance(); }
                case '*' -> { tokens.add(makeToken(TokenType.STAR)); advance(); }
                case '/' -> { tokens.add(makeToken(TokenType.SLASH)); advance(); }
                case '=' -> { tokens.add(makeToken(TokenType.EQUAL)); advance(); }
                case '<' -> { tokens.add(makeToken(TokenType.LT)); advance(); }
                case '>' -> { tokens.add(makeToken(TokenType.GT)); advance(); }
                case '!' -> { tokens.add(makeToken(TokenType.NOT)); advance(); }
                case '(' -> { tokens.add(makeToken(TokenType.LPAREN)); advance(); }
                case ')' -> { tokens.add(makeToken(TokenType.RPAREN)); advance(); }
                case ';' -> { tokens.add(makeToken(TokenType.SEMICOLON)); advance(); }
                case ':' -> { tokens.add(makeToken(TokenType.COLON)); advance(); }
                case '|' -> { tokens.add(makeToken(TokenType.PIPE)); advance(); }
                default -> {
                    // Skip unknown character and continue
                    advance();
                }
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private char peek() {
        return input.charAt(pos);
    }

    private char peekNext() {
        if (pos + 1 >= input.length()) return '\0';
        return input.charAt(pos + 1);
    }

    private void advance() {
        if (peek() == '\n') { line++; column = 0; }
        else { column++; }
        pos++;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) advance();
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n') advance();
    }

    private void skipBlockComment() {
        advance(); advance();
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') { advance(); advance(); return; }
            advance();
        }
    }

    private Token makeToken(TokenType type) {
        return new Token(type, String.valueOf(peek()), line, column);
    }

    private Token readNumber() {
        int start = pos;
        int startCol = column;
        while (!isAtEnd() && Character.isDigit(peek())) advance();
        return new Token(TokenType.NUMBER, input.substring(start, pos), line, startCol);
    }

    private Token readString() {
        int startCol = column;
        int startLine = line;
        advance();
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (isAtEnd()) break;
                switch (peek()) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(peek());
                }
            } else {
                sb.append(peek());
            }
            advance();
        }
        if (!isAtEnd()) advance();
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    private Token readIdentifierOrKeyword() {
        int start = pos;
        int startCol = column;
        while (!isAtEnd() && Character.isLetterOrDigit(peek())) advance();

        String text = input.substring(start, pos);

        return switch (text) {
            case "let" -> new Token(TokenType.LET, text, line, startCol);
            case "rec" -> new Token(TokenType.REC, text, line, startCol);
            case "data" -> new Token(TokenType.DATA, text, line, startCol);
            case "case" -> new Token(TokenType.CASE, text, line, startCol);
            case "of" -> new Token(TokenType.OF, text, line, startCol);
            case "true" -> new Token(TokenType.TRUE, text, line, startCol);
            case "false" -> new Token(TokenType.FALSE, text, line, startCol);
            case "if" -> new Token(TokenType.IF, text, line, startCol);
            case "then" -> new Token(TokenType.THEN, text, line, startCol);
            case "else" -> new Token(TokenType.ELSE, text, line, startCol);
            default -> new Token(TokenType.IDENT, text, line, startCol);
        };
    }
}
