package scala.parser.core.interpreter;

import java.util.List;
import java.util.stream.Collectors;

public final class TupleValue extends Value {
    public final List<Value> values;

    public TupleValue(List<Value> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return "(" + values.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }
}
