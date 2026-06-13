package scala.parser.core.interpreter;

import java.util.List;

public final class ConstructorValue extends Value {
    public final String name;
    public final String dataType;
    public final List<Value> args;

    public ConstructorValue(String name, String dataType, List<Value> args) {
        this.name = name;
        this.dataType = dataType;
        this.args = args;
    }

    @Override
    public String toString() {
        if (args.isEmpty()) return name;
        return name + "(" + String.join(", ", args.stream().map(Object::toString).toList()) + ")";
    }
}
