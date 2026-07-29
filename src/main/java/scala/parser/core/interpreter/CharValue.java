package scala.parser.core.interpreter;

public final class CharValue extends Value {
    public final char value;

    public CharValue(char value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "'" + escape(value) + "'";
    }

    private static String escape(char c) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            default -> String.valueOf(c);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharValue c)) return false;
        return value == c.value;
    }

    @Override
    public int hashCode() {
        return Character.hashCode(value);
    }
}
