package scala.parser.core.types;

public class TypeErrorException extends Exception {
    private static final long serialVersionUID = 1L;
    public final Type left;
    public final Type right;

    public TypeErrorException(String message) {
        super(message);
        this.left = null;
        this.right = null;
    }

    public TypeErrorException(String message, Type left, Type right) {
        super(message);
        this.left = left;
        this.right = right;
    }
}
