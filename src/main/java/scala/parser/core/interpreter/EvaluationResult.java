package scala.parser.core.interpreter;

import java.util.Map;

import scala.parser.core.ParserResult;
import scala.parser.core.ast.ASTNode;
import scala.parser.core.types.Type;

public record EvaluationResult(ParserResult parserResult, Value value, Map<ASTNode, Type> inferredTypes) {}
