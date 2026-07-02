package scala.parser.core.types;

public record TypeDynamic() implements Type {
    @Override
    public String toString() { return "?"; }
}
