package scala.parser.core.lexer;

public enum TokenType {
    // Keywords
    LET, REC,
    DATA, CASE, OF,
    // Identifiers & literals
    IDENT,
    NUMBER,
    STRING,
    // Operators
    PLUS, MINUS, STAR, SLASH,
    EQUAL,
    COLON,
    // Logical
    AND, OR, NOT,
    // Parentheses
    LPAREN,
    RPAREN,
    PIPE,
    EOF,
    BACKSLASH,
    ARROW,
    TRUE, FALSE,
    IF, THEN, ELSE,
    EQ, NEQ, LT, GT, LE, GE,
    SEMICOLON,
}
