# Agentkonfiguration für opencode

## Testbefehle

```bash
# Alle Tests ausführen
.\gradlew.bat test

# Bestimmte Testklasse
.\gradlew.bat test --tests "*SmallStepPBT*"
.\gradlew.bat test --tests "*ScalaMapTest*"
.\gradlew.bat test --tests "*TypeErrorMessageQualityTest*"
.\gradlew.bat test --tests "*TypeSoundnessFormalTest*"
.\gradlew.bat test --tests "*EvaluatorTests*"
.\gradlew.bat test --tests "*EvaluatorPBT*"

# Bestimmte Methode
.\gradlew.bat test --tests "*ScalaMapTest.mutualRecursionEvenOdd*"

# Build (inkl. Tests)
.\gradlew.bat build

# Benchmarks ausführen
.\gradlew.bat run -Dmain=scala.parser.ScalingBenchmarks
```

## Wichtige Dateien

| Bereich | Pfad |
|---------|------|
| AST-Knoten | `src/main/java/scala/parser/core/ast/` |
| Parser | `src/main/java/scala/parser/core/parser/Parser.java` |
| TypeChecker | `src/main/java/scala/parser/core/types/TypeChecker.java` |
| Evaluator | `src/main/java/scala/parser/core/interpreter/Evaluator.java` |
| SmallStepReducer | `src/main/java/scala/parser/core/reduction/SmallStepReducer.java` |
| LSP-Server | `src/main/java/scala/parser/lsp/` |
| Tests | `src/test/java/scala/parser/core/` |
| Coq-Formalisierung | `coq/LambdaCore.v` |
| Dokumentation | `docs/lambda-core.md`, `docs/benchmarks.md` |

## Wichtige Constraints

- Alle Änderungen müssen `gradle test` grün halten
- Keine ungefragten Commits
- Coq-Code als `.v`-Datei (Installation nicht auf dev-Maschine verfügbar)
- Mutual Recursion: `mergeMutualDefs()` gruppiert adjazente `def`-Statements

## Bekannte Limitationen

- **SmallStepReducer + Rekursion**: Der substitution-basierte Reducer (`Substitution.subst`) kann rekursive `let`/`def`-Bindungen nicht korrekt reduzieren, weil die Substitution den gebundenen Namen in nachfolgenden Statements ersetzt, sodass rekursive Selbstreferenzen zu freien Variablen werden. Der `Evaluator` (Environment-basiert) handhabt Rekursion korrekt. Siehe `docs/lambda-core.md` §13 (Deviations).
- **Coq**: `LambdaCore.v` hat 4 `Admitted` (Theorem 3, 4, 6) – können nur auf einem Rechner mit Coq-Installation gefüllt werden.
