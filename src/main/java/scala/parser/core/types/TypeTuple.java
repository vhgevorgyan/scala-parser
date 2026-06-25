package scala.parser.core.types;

import java.util.List;

public record TypeTuple(List<Type> members) implements Type {
    public TypeTuple(List<Type> members) {
        this.members = List.copyOf(members);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("(");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(members.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
