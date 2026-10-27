package scala.parser.core.interpreter;

public final class ValueUtils {
    private ValueUtils() {}

    public static boolean valuesEqual(Value a, Value b) {
        if (a instanceof NumberValue na && b instanceof NumberValue nb) return na.value == nb.value;
        if (a instanceof BoolValue ba && b instanceof BoolValue bb) return ba.value == bb.value;
        if (a instanceof StringValue sa && b instanceof StringValue sb) return sa.value.equals(sb.value);
        return false;
    }
}
