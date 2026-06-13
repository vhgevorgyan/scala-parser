package scala.parser.core.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import scala.parser.core.ParserError;
import scala.parser.core.ParserResult;
import scala.parser.core.ast.*;
import scala.parser.core.lexer.Token;
import scala.parser.core.lexer.Tokenizer;

class ParserTests {

    private ParserResult parse(String src) {
        List<Token> tokens = new Tokenizer(src).tokenize();
        return new Parser(tokens).parse();
    }

    @Test
    void numberLiteral() {
        ParserResult r = parse("42");
        assertTrue(r.errors.isEmpty());
        ProgramNode prog = r.ast;
        assertEquals(1, prog.statements.size());
        assertInstanceOf(LiteralNode.class, prog.statements.get(0));
        assertEquals("42", ((LiteralNode) prog.statements.get(0)).value);
    }

    @Test
    void booleanLiterals() {
        assertInstanceOf(BoolNode.class, parse("true").ast.statements.get(0));
        assertTrue(((BoolNode) parse("true").ast.statements.get(0)).value);

        assertInstanceOf(BoolNode.class, parse("false").ast.statements.get(0));
        assertFalse(((BoolNode) parse("false").ast.statements.get(0)).value);
    }

    @Test
    void identifier() {
        ParserResult r = parse("myVar");
        assertTrue(r.errors.isEmpty());
        IdentifierNode id = (IdentifierNode) r.ast.statements.get(0);
        assertEquals("myVar", id.name);
    }

    @Test
    void arithmeticPrecedence() {
        // 1 + 2 * 3  →  (1 + (2 * 3))
        ParserResult r = parse("1 + 2 * 3");
        assertTrue(r.errors.isEmpty());
        BinaryExprNode bin = (BinaryExprNode) r.ast.statements.get(0);
        assertEquals("+", bin.operator);
        assertInstanceOf(LiteralNode.class, bin.left);
        assertInstanceOf(BinaryExprNode.class, bin.right);
        BinaryExprNode inner = (BinaryExprNode) bin.right;
        assertEquals("*", inner.operator);
    }

    @Test
    void comparisonPrecedence() {
        ParserResult r = parse("1 + 2 == 3");
        assertTrue(r.errors.isEmpty());
        BinaryExprNode bin = (BinaryExprNode) r.ast.statements.get(0);
        assertEquals("==", bin.operator);
        assertInstanceOf(BinaryExprNode.class, bin.left);
        assertEquals("+", ((BinaryExprNode) bin.left).operator);
    }

    @Test
    void lambda() {
        ParserResult r = parse("\\x -> x");
        assertTrue(r.errors.isEmpty());
        LambdaNode lam = (LambdaNode) r.ast.statements.get(0);
        assertEquals("x", lam.param);
        assertInstanceOf(IdentifierNode.class, lam.body);
        assertEquals("x", ((IdentifierNode) lam.body).name);
    }

    @Test
    void lambdaWithBody() {
        ParserResult r = parse("\\x -> x + 1");
        assertTrue(r.errors.isEmpty());
        LambdaNode lam = (LambdaNode) r.ast.statements.get(0);
        assertEquals("x", lam.param);
        assertInstanceOf(BinaryExprNode.class, lam.body);
    }

    @Test
    void functionApplication() {
        ParserResult r = parse("f x");
        assertTrue(r.errors.isEmpty());
        ApplicationNode app = (ApplicationNode) r.ast.statements.get(0);
        assertInstanceOf(IdentifierNode.class, app.function);
        assertEquals("f", ((IdentifierNode) app.function).name);
        assertInstanceOf(IdentifierNode.class, app.argument);
        assertEquals("x", ((IdentifierNode) app.argument).name);
    }

    @Test
    void multipleApplications() {
        ParserResult r = parse("f x y");
        assertTrue(r.errors.isEmpty());
        // ((f x) y)
        ApplicationNode outer = (ApplicationNode) r.ast.statements.get(0);
        assertInstanceOf(ApplicationNode.class, outer.function);
        assertEquals("y", ((IdentifierNode) outer.argument).name);
        ApplicationNode inner = (ApplicationNode) outer.function;
        assertEquals("f", ((IdentifierNode) inner.function).name);
        assertEquals("x", ((IdentifierNode) inner.argument).name);
    }

    @Test
    void ifExpr() {
        ParserResult r = parse("if true then 1 else 2");
        assertTrue(r.errors.isEmpty());
        IfNode ifn = (IfNode) r.ast.statements.get(0);
        assertInstanceOf(BoolNode.class, ifn.condition);
        assertInstanceOf(LiteralNode.class, ifn.thenBranch);
        assertEquals("1", ((LiteralNode) ifn.thenBranch).value);
        assertInstanceOf(LiteralNode.class, ifn.elseBranch);
        assertEquals("2", ((LiteralNode) ifn.elseBranch).value);
    }

    @Test
    void letStatement() {
        ParserResult r = parse("let x = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("x", let.name);
        assertInstanceOf(LiteralNode.class, let.value);
        assertEquals("42", ((LiteralNode) let.value).value);
    }

    @Test
    void letWithExpression() {
        ParserResult r = parse("let add = \\x -> \\y -> x + y");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("add", let.name);
        assertInstanceOf(LambdaNode.class, let.value);
        LambdaNode outer = (LambdaNode) let.value;
        assertEquals("x", outer.param);
    }

    @Test
    void parentheses() {
        ParserResult r = parse("(1 + 2) * 3");
        assertTrue(r.errors.isEmpty());
        BinaryExprNode bin = (BinaryExprNode) r.ast.statements.get(0);
        assertEquals("*", bin.operator);
        assertInstanceOf(BinaryExprNode.class, bin.left);
        assertEquals("+", ((BinaryExprNode) bin.left).operator);
    }

    @Test
    void lambdaInApplication() {
        ParserResult r = parse("(\\x -> x + 1) 5");
        assertTrue(r.errors.isEmpty());
        ApplicationNode app = (ApplicationNode) r.ast.statements.get(0);
        assertInstanceOf(LambdaNode.class, app.function);
        assertEquals("5", ((LiteralNode) app.argument).value);
    }

    @Test
    void allOperators() {
        ParserResult r = parse("1 + 2 - 3 * 4 / 5");
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void allComparisons() {
        ParserResult r = parse("1 < 2 > 3 <= 4 >= 5 == 6 != 7");
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void multipleStatements() {
        ParserResult r = parse("let x = 1\nlet y = x");
        assertTrue(r.errors.isEmpty());
        assertEquals(2, r.ast.statements.size());
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(0));
        assertEquals("x", ((LetStatementNode) r.ast.statements.get(0)).name);
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(1));
        assertEquals("y", ((LetStatementNode) r.ast.statements.get(1)).name);
    }

    @Test
    void unexpectedTokenError() {
        ParserResult r = parse("let = 5");
        assertFalse(r.errors.isEmpty());
    }

    @Test
    void missingArrowInLambda() {
        ParserResult r = parse("\\x x");
        assertFalse(r.errors.isEmpty());
    }

    @Test
    void emptyParens() {
        ParserResult r = parse("()");
        assertFalse(r.errors.isEmpty());
    }

    @Test
    void letRec() {
        ParserResult r = parse("let rec fact = \\n -> if n == 0 then 1 else n * fact (n - 1)");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("fact", let.name);
        assertTrue(let.isRec);
        assertInstanceOf(LambdaNode.class, let.value);
    }

    @Test
    void typeAnnotation() {
        ParserResult r = parse("let x : Number = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("x", let.name);
        assertNotNull(let.declaredType);
    }

    @Test
    void dataDecl() {
        ParserResult r = parse("data Maybe a = Nothing | Just a");
        assertTrue(r.errors.isEmpty());
        DataDeclNode data = (DataDeclNode) r.ast.statements.get(0);
        assertEquals("Maybe", data.name);
        assertEquals(1, data.params.size());
        assertEquals("a", data.params.get(0));
        assertEquals(2, data.constructors.size());
        assertEquals("Nothing", data.constructors.get(0).name());
        assertTrue(data.constructors.get(0).argTypes().isEmpty());
        assertEquals("Just", data.constructors.get(1).name());
        assertEquals(1, data.constructors.get(1).argTypes().size());
    }

    @Test
    void dataDeclNoParams() {
        ParserResult r = parse("data Bool = True | False");
        assertTrue(r.errors.isEmpty());
        DataDeclNode data = (DataDeclNode) r.ast.statements.get(0);
        assertEquals("Bool", data.name);
        assertTrue(data.params.isEmpty());
        assertEquals(2, data.constructors.size());
    }

    @Test
    void caseExpr() {
        ParserResult r = parse("case x of Nothing -> 0 | Just y -> y");
        assertTrue(r.errors.isEmpty());
        CaseNode caseNode = (CaseNode) r.ast.statements.get(0);
        assertInstanceOf(IdentifierNode.class, caseNode.expr);
        assertEquals(2, caseNode.branches.size());
        assertEquals("Nothing", caseNode.branches.get(0).constructor());
        assertTrue(caseNode.branches.get(0).bindings().isEmpty());
        assertEquals("Just", caseNode.branches.get(1).constructor());
        assertEquals(1, caseNode.branches.get(1).bindings().size());
        assertEquals("y", caseNode.branches.get(1).bindings().get(0).name());
    }

    @Test
    void caseMultiBindings() {
        ParserResult r = parse("case p of Pair x y -> x");
        assertTrue(r.errors.isEmpty());
        CaseNode caseNode = (CaseNode) r.ast.statements.get(0);
        assertEquals(1, caseNode.branches.size());
        assertEquals("Pair", caseNode.branches.get(0).constructor());
        assertEquals(2, caseNode.branches.get(0).bindings().size());
        assertEquals("x", caseNode.branches.get(0).bindings().get(0).name());
        assertEquals("y", caseNode.branches.get(0).bindings().get(1).name());
    }

    @Test
    void semicolonsSkipBetweenStatements() {
        ParserResult r = parse("let x = 1; let y = 2");
        assertTrue(r.errors.isEmpty());
        assertEquals(3, r.ast.statements.size());
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(0));
        assertInstanceOf(LiteralNode.class, r.ast.statements.get(1));
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(2));
    }

    @Test
    void unaryNot() {
        ParserResult r = parse("!true");
        assertTrue(r.errors.isEmpty());
        UnaryExprNode unary = (UnaryExprNode) r.ast.statements.get(0);
        assertEquals("!", unary.operator);
    }

    @Test
    void logicalAndOr() {
        ParserResult r = parse("true && false || true");
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void stringLiteral() {
        ParserResult r = parse("let s = \"hello\"");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertInstanceOf(StringLiteralNode.class, let.value);
        StringLiteralNode s = (StringLiteralNode) let.value;
        assertEquals("hello", s.value);
    }

    @Test
    void recKeyword() {
        ParserResult r = parse("let rec x = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertTrue(let.isRec);
    }

    @Test
    void dataAndConstructorUsage() {
        ParserResult r = parse("data Maybe a = Nothing | Just a\nlet x = Just 42");
        assertTrue(r.errors.isEmpty());
        assertEquals(2, r.ast.statements.size());
    }
}
