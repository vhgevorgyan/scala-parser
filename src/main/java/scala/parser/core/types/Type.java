package scala.parser.core.types;

public sealed interface Type permits TypeNumber, TypeBool, TypeString, TypeUnit, TypeFun, TypeVar, TypeConstructor, TypeTuple {
}
