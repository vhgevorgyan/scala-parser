package scala.parser.core.types;

public record TypeChar() implements Type {
    @Override
    public String toString() {
        return "Char";
    }
}
