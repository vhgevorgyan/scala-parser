package scala.parser.core.lexer;

public enum TokenType {
    // Keywords
    VAL, DEF,
    ENUM, CASE, MATCH,
    TYPE,
    // Identifiers & literals
    IDENT,
    NUMBER,
    STRING,
    // Operators
    PLUS, MINUS, STAR, SLASH,
    EQUAL,
    COLON,
    QUESTION,
    // Logical
    AND, OR, NOT,
    // Parentheses, braces & brackets
    LPAREN, RPAREN,
    LBRACE, RBRACE,
    LBRACKET, RBRACKET,
    PIPE,
    ARROW,
    EOF,
    TRUE, FALSE,
    IF, ELSE,
    EQ, NEQ, LT, GT, LE, GE,
    SEMICOLON,
    COMMA,
    UNDERSCORE,
    DOTDOT,
    AT,
}
