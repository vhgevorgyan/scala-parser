package scala.parser.core.types;

public sealed interface Type permits TypeNumber, TypeBool, TypeString, TypeUnit, TypeChar, TypeFun, TypeVar, TypeConstructor, TypeTuple, TypeDynamic {
}
