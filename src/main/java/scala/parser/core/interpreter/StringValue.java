package scala.parser.core.interpreter;

public final class StringValue extends Value {
    public final String value;

    public StringValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringValue s)) return false;
        return value.equals(s.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
