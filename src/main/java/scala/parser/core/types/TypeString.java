package scala.parser.core.types;

public record TypeString() implements Type {
    @Override
    public String toString() {
        return "String";
    }
}
