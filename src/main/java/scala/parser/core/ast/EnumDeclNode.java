package scala.parser.core.ast;

import java.util.List;

public class EnumDeclNode extends ASTNode {

    public final String name;
    public final List<String> params;
    public final List<ConstructorDef> constructors;

    public EnumDeclNode(String name, List<String> params, List<ConstructorDef> constructors, int line, int column) {
        super(line, column);
        this.name = name;
        this.params = params;
        this.constructors = constructors;
    }

    public static record ConstructorDef(String name, List<String> argTypes) {}
}
