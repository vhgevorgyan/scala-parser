package scala.parser.core.lexer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class TokenizerTests {

    private List<Token> tokenize(String src) {
        return new Tokenizer(src).tokenize();
    }

    @Test
    void numbers() {
        List<Token> tokens = tokenize("42 0 123");
        assertEquals(4, tokens.size());
        assertEquals(TokenType.NUMBER, tokens.get(0).type);
        assertEquals("42", tokens.get(0).text);
        assertEquals(TokenType.NUMBER, tokens.get(1).type);
        assertEquals("0", tokens.get(1).text);
        assertEquals(TokenType.NUMBER, tokens.get(2).type);
        assertEquals("123", tokens.get(2).text);
        assertEquals(TokenType.EOF, tokens.get(3).type);
    }

    @Test
    void identifiers() {
        List<Token> tokens = tokenize("foo bar x123");
        assertEquals(4, tokens.size());
        assertEquals(TokenType.IDENT, tokens.get(0).type);
        assertEquals("foo", tokens.get(0).text);
        assertEquals("bar", tokens.get(1).text);
        assertEquals("x123", tokens.get(2).text);
    }

    @Test
    void keywords() {
        List<Token> tokens = tokenize("let true false if then else");
        assertEquals(7, tokens.size());
        assertEquals(TokenType.LET, tokens.get(0).type);
        assertEquals(TokenType.TRUE, tokens.get(1).type);
        assertEquals(TokenType.FALSE, tokens.get(2).type);
        assertEquals(TokenType.IF, tokens.get(3).type);
        assertEquals(TokenType.THEN, tokens.get(4).type);
        assertEquals(TokenType.ELSE, tokens.get(5).type);
    }

    @Test
    void operators() {
        List<Token> tokens = tokenize("+ - * / == != < > <= >= -> \\ =");
        assertEquals(14, tokens.size());
        assertEquals(TokenType.PLUS, tokens.get(0).type);
        assertEquals(TokenType.MINUS, tokens.get(1).type);
        assertEquals(TokenType.STAR, tokens.get(2).type);
        assertEquals(TokenType.SLASH, tokens.get(3).type);
        assertEquals(TokenType.EQ, tokens.get(4).type);
        assertEquals(TokenType.NEQ, tokens.get(5).type);
        assertEquals(TokenType.LT, tokens.get(6).type);
        assertEquals(TokenType.GT, tokens.get(7).type);
        assertEquals(TokenType.LE, tokens.get(8).type);
        assertEquals(TokenType.GE, tokens.get(9).type);
        assertEquals(TokenType.ARROW, tokens.get(10).type);
        assertEquals(TokenType.BACKSLASH, tokens.get(11).type);
        assertEquals(TokenType.EQUAL, tokens.get(12).type);
    }

    @Test
    void parentheses() {
        List<Token> tokens = tokenize("()");
        assertEquals(3, tokens.size());
        assertEquals(TokenType.LPAREN, tokens.get(0).type);
        assertEquals(TokenType.RPAREN, tokens.get(1).type);
    }

    @Test
    void whitespaceHandling() {
        List<Token> tokens = tokenize("  let   x\t= 42\n");
        assertEquals(5, tokens.size());
        assertEquals(TokenType.LET, tokens.get(0).type);
        assertEquals(TokenType.IDENT, tokens.get(1).type);
        assertEquals("x", tokens.get(1).text);
        assertEquals(TokenType.EQUAL, tokens.get(2).type);
        assertEquals(TokenType.NUMBER, tokens.get(3).type);
        assertEquals("42", tokens.get(3).text);
    }

    @Test
    void lineNumbers() {
        List<Token> tokens = tokenize("1\n+ 2\n* 3");
        assertEquals(6, tokens.size());
        assertEquals(0, tokens.get(0).line);
        assertEquals("1", tokens.get(0).text);
        assertEquals(1, tokens.get(1).line);
        assertEquals("+", tokens.get(1).text);
        assertEquals(1, tokens.get(2).line);
        assertEquals("2", tokens.get(2).text);
        assertEquals(2, tokens.get(3).line);
        assertEquals("*", tokens.get(3).text);
        assertEquals(2, tokens.get(4).line);
        assertEquals("3", tokens.get(4).text);
    }

    @Test
    void columnNumbers() {
        List<Token> tokens = tokenize("1 + 2");
        assertEquals(4, tokens.size());
        assertEquals(0, tokens.get(0).column);
        assertEquals(2, tokens.get(1).column);
        assertEquals(4, tokens.get(2).column);
    }

    @Test
    void emptyInput() {
        List<Token> tokens = tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type);
    }

    @Test
    void onlyWhitespace() {
        List<Token> tokens = tokenize("   \t\n  ");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.EOF, tokens.get(0).type);
    }

    @Test
    void complexExpression() {
        List<Token> tokens = tokenize("let f = \\x -> x + 1");
        assertEquals(10, tokens.size());
        assertEquals(TokenType.LET, tokens.get(0).type);
        assertEquals(TokenType.IDENT, tokens.get(1).type);
        assertEquals("f", tokens.get(1).text);
        assertEquals(TokenType.EQUAL, tokens.get(2).type);
        assertEquals(TokenType.BACKSLASH, tokens.get(3).type);
        assertEquals(TokenType.IDENT, tokens.get(4).type);
        assertEquals("x", tokens.get(4).text);
        assertEquals(TokenType.ARROW, tokens.get(5).type);
        assertEquals(TokenType.IDENT, tokens.get(6).type);
        assertEquals("x", tokens.get(6).text);
        assertEquals(TokenType.PLUS, tokens.get(7).type);
        assertEquals(TokenType.NUMBER, tokens.get(8).type);
        assertEquals("1", tokens.get(8).text);
        assertEquals(TokenType.EOF, tokens.get(9).type);
    }
}
