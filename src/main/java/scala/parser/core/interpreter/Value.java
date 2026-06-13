package scala.parser.core.interpreter;

public sealed abstract class Value
    permits NumberValue, StringValue, ClosureValue, BoolValue, BuiltinValue, ConstructorValue, ConstructorBuilder {
}
