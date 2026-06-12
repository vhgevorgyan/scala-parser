package scala.parser.core.types;

import java.util.List;

public record TypeScheme(List<String> params, Type body) {
    @Override
    public String toString() {
        if (params.isEmpty()) return body.toString();
        return "forall " + String.join(" ", params) + ". " + body;
    }
}
