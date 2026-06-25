package scala.parser.core.types;

public record TypeFun(Type from, Type to) implements Type {
    @Override
    public String toString() {
        String fromStr = from instanceof TypeFun ? "(" + from + ")" : from.toString();
        return fromStr + " => " + to;
    }
}
