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
        ParserResult r = parse("(x: Int) => x");
        assertTrue(r.errors.isEmpty());
        LambdaNode lam = (LambdaNode) r.ast.statements.get(0);
        assertEquals("x", lam.param);
        assertInstanceOf(IdentifierNode.class, lam.body);
        assertEquals("x", ((IdentifierNode) lam.body).name);
    }

    @Test
    void lambdaWithBody() {
        ParserResult r = parse("(x: Int) => x + 1");
        assertTrue(r.errors.isEmpty());
        LambdaNode lam = (LambdaNode) r.ast.statements.get(0);
        assertEquals("x", lam.param);
        assertInstanceOf(BinaryExprNode.class, lam.body);
    }

    @Test
    void functionApplication() {
        ParserResult r = parse("f(x)");
        assertTrue(r.errors.isEmpty());
        ApplicationNode app = (ApplicationNode) r.ast.statements.get(0);
        assertInstanceOf(IdentifierNode.class, app.function);
        assertEquals("f", ((IdentifierNode) app.function).name);
        assertInstanceOf(IdentifierNode.class, app.argument);
        assertEquals("x", ((IdentifierNode) app.argument).name);
    }

    @Test
    void multipleApplications() {
        ParserResult r = parse("f(x)(y)");
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
        ParserResult r = parse("if (true) 1 else 2");
        assertTrue(r.errors.isEmpty());
        IfNode ifn = (IfNode) r.ast.statements.get(0);
        assertInstanceOf(BoolNode.class, ifn.condition);
        assertInstanceOf(LiteralNode.class, ifn.thenBranch);
        assertEquals("1", ((LiteralNode) ifn.thenBranch).value);
        assertInstanceOf(LiteralNode.class, ifn.elseBranch);
        assertEquals("2", ((LiteralNode) ifn.elseBranch).value);
    }

    @Test
    void valStatement() {
        ParserResult r = parse("val x = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("x", let.name);
        assertInstanceOf(LiteralNode.class, let.value);
        assertEquals("42", ((LiteralNode) let.value).value);
    }

    @Test
    void valWithLambda() {
        ParserResult r = parse("val add = (x: Int) => (y: Int) => x + y");
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
        ParserResult r = parse("((x: Int) => x + 1)(5)");
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
        ParserResult r = parse("val x = 1\nval y = x");
        assertTrue(r.errors.isEmpty());
        assertEquals(2, r.ast.statements.size());
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(0));
        assertEquals("x", ((LetStatementNode) r.ast.statements.get(0)).name);
        assertInstanceOf(LetStatementNode.class, r.ast.statements.get(1));
        assertEquals("y", ((LetStatementNode) r.ast.statements.get(1)).name);
    }

    @Test
    void unexpectedTokenError() {
        ParserResult r = parse("val = 5");
        assertFalse(r.errors.isEmpty());
    }

    @Test
    void missingArrowInLambda() {
        ParserResult r = parse("(x: Int) x");
        // Now parses as parenthesized ascription (x: Int) followed by x — syntactically valid
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void emptyParens() {
        ParserResult r = parse("()");
        // () is now a valid unit literal
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void defFunction() {
        ParserResult r = parse("def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1)");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("fact", let.name);
        assertTrue(let.isRec);
        assertInstanceOf(LambdaNode.class, let.value);
    }

    @Test
    void typeAnnotation() {
        ParserResult r = parse("val x: Int = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertEquals("x", let.name);
        assertNotNull(let.declaredType);
    }

    @Test
    void dataDecl() {
        ParserResult r = parse("enum Maybe[A] { case Nothing; case Just(x: A) }");
        assertTrue(r.errors.isEmpty());
        EnumDeclNode en = (EnumDeclNode) r.ast.statements.get(0);
        assertEquals("Maybe", en.name);
        assertEquals(1, en.params.size());
        assertEquals("A", en.params.get(0));
        assertEquals(2, en.constructors.size());
        assertEquals("Nothing", en.constructors.get(0).name());
        assertTrue(en.constructors.get(0).argTypes().isEmpty());
        assertEquals("Just", en.constructors.get(1).name());
        assertEquals(1, en.constructors.get(1).argTypes().size());
    }

    @Test
    void dataDeclNoParams() {
        ParserResult r = parse("enum Bool { case True; case False }");
        assertTrue(r.errors.isEmpty());
        EnumDeclNode en = (EnumDeclNode) r.ast.statements.get(0);
        assertEquals("Bool", en.name);
        assertTrue(en.params.isEmpty());
        assertEquals(2, en.constructors.size());
    }

    @Test
    void caseExpr() {
        ParserResult r = parse("x match { case Nothing => 0; case Just(y) => y }");
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
        ParserResult r = parse("p match { case Pair(x, y) => x }");
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
        ParserResult r = parse("val x = 1; val y = 2");
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
    void unaryMinus() {
        ParserResult r = parse("-5");
        assertTrue(r.errors.isEmpty());
        UnaryExprNode unary = (UnaryExprNode) r.ast.statements.get(0);
        assertEquals("-", unary.operator);
        assertInstanceOf(LiteralNode.class, unary.operand);
        assertEquals("5", ((LiteralNode)unary.operand).value);
    }

    @Test
    void unaryMinusExpr() {
        ParserResult r = parse("-(x + 1)");
        assertTrue(r.errors.isEmpty());
        UnaryExprNode unary = (UnaryExprNode) r.ast.statements.get(0);
        assertEquals("-", unary.operator);
    }

    @Test
    void logicalAndOr() {
        ParserResult r = parse("true && false || true");
        assertTrue(r.errors.isEmpty());
    }

    @Test
    void stringLiteral() {
        ParserResult r = parse("val s = \"hello\"");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertInstanceOf(StringLiteralNode.class, let.value);
        StringLiteralNode s = (StringLiteralNode) let.value;
        assertEquals("hello", s.value);
    }

    @Test
    void defNoParams() {
        ParserResult r = parse("def x = 42");
        assertTrue(r.errors.isEmpty());
        LetStatementNode let = (LetStatementNode) r.ast.statements.get(0);
        assertTrue(let.isRec);
    }

    @Test
    void dataAndConstructorUsage() {
        ParserResult r = parse("enum Maybe[A] { case Nothing; case Just(x: A) }\nval x = Just(42)");
        assertTrue(r.errors.isEmpty());
        assertEquals(2, r.ast.statements.size());
    }
}
