package scala.parser.core.interpreter;

public final class BoolValue extends Value {
    public final boolean value;

    public BoolValue(boolean value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value ? "true" : "false";
    }
}

