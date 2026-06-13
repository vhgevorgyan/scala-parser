package scala.parser.core.interpreter;

import java.util.HashMap;
import java.util.Map;

public final class Env {
    private final Env parent;
    private final Map<String, Value> values = new HashMap<>();

    public Env() {
        this.parent = null;
    }

    public Env(Env parent) {
        this.parent = parent;
    }

    public void define(String name, Value value) {
        values.put(name, value);
    }

    public Value lookup(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        throw new RuntimeException("Unbound variable: " + name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Env e = this;
        while (e != null) {
            for (var entry : e.values.entrySet()) {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
            e = e.parent;
        }
        return sb.toString();
    }
}

