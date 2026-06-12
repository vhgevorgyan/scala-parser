package scala.parser.core.types;

public record TypeVar(String name) implements Type {
    @Override
    public String toString() {
        return name;
    }
}
