package scala.parser.core.types;

import java.util.List;
import java.util.stream.Collectors;

public record TypeConstructor(String name, List<Type> args) implements Type {
    @Override
    public String toString() {
        if (args.isEmpty()) return name;
        return name + " " + args.stream().map(Object::toString).collect(Collectors.joining(" "));
    }
}
