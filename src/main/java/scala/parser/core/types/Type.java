package scala.parser.core.types;

public sealed interface Type permits TypeNumber, TypeBool, TypeFun, TypeVar {
}
