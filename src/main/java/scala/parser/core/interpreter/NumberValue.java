package scala.parser.core.interpreter;

public final class NumberValue extends Value {
    public final int value;

    public NumberValue(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
