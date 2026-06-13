package scala.parser.core.interpreter;

import java.util.ArrayList;
import java.util.List;

public final class ConstructorBuilder extends Value {
    public final String dataType;
    public final String ctorName;
    public final int totalArgs;
    public final List<Value> args;

    public ConstructorBuilder(String dataType, String ctorName, int totalArgs) {
        this.dataType = dataType;
        this.ctorName = ctorName;
        this.totalArgs = totalArgs;
        this.args = new ArrayList<>();
    }

    public ConstructorBuilder(String dataType, String ctorName, int totalArgs, List<Value> args) {
        this.dataType = dataType;
        this.ctorName = ctorName;
        this.totalArgs = totalArgs;
        this.args = new ArrayList<>(args);
    }

    public Value apply(Value arg) {
        List<Value> newArgs = new ArrayList<>(args);
        newArgs.add(arg);
        if (newArgs.size() >= totalArgs) {
            return new ConstructorValue(ctorName, dataType, newArgs);
        }
        return new ConstructorBuilder(dataType, ctorName, totalArgs, newArgs);
    }

    @Override
    public String toString() {
        return "<constructor: " + ctorName + ">";
    }
}
