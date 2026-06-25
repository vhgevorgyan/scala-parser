package scala.parser.core.types;

public record TypeNumber() implements Type {
    @Override
    public String toString() {
        return "Int";
    }
}
