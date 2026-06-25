package scala.parser.core.ast;

import scala.parser.core.types.Type;

public class TypeAliasNode extends ASTNode {

    public final String name;
    public final Type aliasType;

    public TypeAliasNode(String name, Type aliasType, int line, int column) {
        super(line, column);
        this.name = name;
        this.aliasType = aliasType;
    }
}
