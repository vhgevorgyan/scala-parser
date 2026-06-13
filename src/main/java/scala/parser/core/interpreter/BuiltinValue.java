package scala.parser.core.interpreter;

import java.util.function.Function;

public final class BuiltinValue extends Value {
    public final String name;
    private final Function<Value, Value> fn;

    public BuiltinValue(String name, Function<Value, Value> fn) {
        this.name = name;
        this.fn = fn;
    }

    public Value apply(Value arg) {
        return fn.apply(arg);
    }

    @Override
    public String toString() {
        return "<builtin: " + name + ">";
    }
}
