package scala.parser.core.types;

public record TypeUnit() implements Type {
    @Override
    public String toString() {
        return "Unit";
    }
}
