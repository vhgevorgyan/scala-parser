package scala.parser.core.lexer;

public enum TokenType {
    // Keywords
    LET,
    // Identifiers & literals
    IDENT,
    NUMBER,
    STRING,
    // Operators
    PLUS, MINUS, STAR, SLASH,
    EQUAL,
    // Logical
    AND, OR, NOT,
    // Parentheses
    LPAREN,
    RPAREN,
    EOF,
    BACKSLASH,
    ARROW,
    TRUE, FALSE,
    IF, THEN, ELSE,
    EQ, NEQ, LT, GT, LE, GE,
    SEMICOLON,
}
