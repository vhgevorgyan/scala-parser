package scala.parser.core.types;

public record TypeBool() implements Type {
    @Override
    public String toString() {
        return "Bool";
    }
}
