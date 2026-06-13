package scala.parser.core.types;

public sealed interface Type permits TypeNumber, TypeBool, TypeString, TypeFun, TypeVar, TypeConstructor {
}
