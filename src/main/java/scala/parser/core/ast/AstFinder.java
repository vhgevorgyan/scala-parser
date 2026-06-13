package scala.parser.core.ast;

public class AstFinder {

    public static ASTNode findNodeAt(ASTNode root, int line, int column) {
        return findRecursive(root, line, column);
    }

    private static ASTNode findRecursive(ASTNode node, int line, int column) {
        if (node == null) return null;

        if (node.line == line && node.column <= column) {
            if (node instanceof ProgramNode program) {
                for (ASTNode stmt : program.statements) {
                    ASTNode found = findRecursive(stmt, line, column);
                    if (found != null) return found;
                }
            }

            if (node instanceof BinaryExprNode bin) {
                ASTNode left = findRecursive(bin.left, line, column);
                if (left != null) return left;
                ASTNode right = findRecursive(bin.right, line, column);
                if (right != null) return right;
            }

            if (node instanceof IfNode i) {
                ASTNode cond = findRecursive(i.condition, line, column);
                if (cond != null) return cond;
                ASTNode thenB = findRecursive(i.thenBranch, line, column);
                if (thenB != null) return thenB;
                ASTNode elseB = findRecursive(i.elseBranch, line, column);
                if (elseB != null) return elseB;
            }

            if (node instanceof ApplicationNode app) {
                ASTNode fn = findRecursive(app.function, line, column);
                if (fn != null) return fn;
                ASTNode arg = findRecursive(app.argument, line, column);
                if (arg != null) return arg;
            }

            if (node instanceof LambdaNode lam) {
                ASTNode body = findRecursive(lam.body, line, column);
                if (body != null) return body;
            }

            if (node instanceof LetStatementNode let) {
                ASTNode val = findRecursive(let.value, line, column);
                if (val != null) return val;
            }

            if (node instanceof UnaryExprNode un) {
                ASTNode operand = findRecursive(un.operand, line, column);
                if (operand != null) return operand;
            }

            if (node instanceof CaseNode c) {
                ASTNode expr = findRecursive(c.expr, line, column);
                if (expr != null) return expr;
                for (CaseNode.CaseBranch br : c.branches) {
                    ASTNode body = findRecursive(br.body(), line, column);
                    if (body != null) return body;
                }
            }

            return node;
        }

        return null;
    }
}
