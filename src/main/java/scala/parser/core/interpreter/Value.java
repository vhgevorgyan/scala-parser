package scala.parser.core.interpreter;

public sealed abstract class Value
    permits NumberValue, ClosureValue, BoolValue {
}
