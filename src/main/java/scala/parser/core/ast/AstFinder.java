package scala.parser.core.ast;

public class AstFinder {

    public static ASTNode findNodeAt(ASTNode root, int line, int column) {
        return findRecursive(root, line, column);
    }

    private static ASTNode findRecursive(ASTNode node, int line, int column) {
        if (node == null) return null;

        // Prüfen, ob Position passt
        if (node.line == line && node.column <= column) {
            // Wir versuchen, tiefere Knoten zu finden
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

            if (node instanceof LetStatementNode let) {
                ASTNode val = findRecursive(let.value, line, column);
                if (val != null) return val;
            }

            // Wenn kein tieferer Knoten passt → diesen zurückgeben
            return node;
        }

        return null;
    }
}
