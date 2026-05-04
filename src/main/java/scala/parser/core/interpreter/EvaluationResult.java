package scala.parser.core.interpreter;

import scala.parser.core.ParserResult;
import scala.parser.core.interpreter.Value;

public record EvaluationResult(ParserResult parserResult, Value value) {}
