# Agentkonfiguration für opencode

## Testbefehle

```bash
# Alle Tests ausführen
.\gradlew.bat test

# Bestimmte Testklasse
.\gradlew.bat test --tests "*SmallStepPBT*"
.\gradlew.bat test --tests "*CoverageGuidedPBT*"
.\gradlew.bat test --tests "*ScalaMapTest*"
.\gradlew.bat test --tests "*TypeErrorMessageQualityTest*"
.\gradlew.bat test --tests "*TypeSoundnessFormalTest*"
.\gradlew.bat test --tests "*EvaluatorTests*"
.\gradlew.bat test --tests "*EvaluatorPBT*"

# Bestimmte Methode
.\gradlew.bat test --tests "*ScalaMapTest.mutualRecursionEvenOdd*"

# Mutation Analysis
.\gradlew.bat runMutationAnalysis

# Type Error Diagnosis Benchmark
.\gradlew.bat runTypeErrorBenchmark

# Build (inkl. Tests)
.\gradlew.bat build

# Benchmarks ausführen
.\gradlew.bat run -Dmain=scala.parser.ScalingBenchmarks

# Benchmark Suite (4 Auswertungen)
.\gradlew.bat runBenchmarkSuite
```

## Wichtige Dateien

| Bereich | Pfad |
|---------|------|
| AST-Knoten | `src/main/java/scala/parser/core/ast/` |
| Parser | `src/main/java/scala/parser/core/parser/Parser.java` |
| TypeChecker | `src/main/java/scala/parser/core/types/TypeChecker.java` |
| Evaluator | `src/main/java/scala/parser/core/interpreter/Evaluator.java` |
| SmallStepReducer | `src/main/java/scala/parser/core/reduction/SmallStepReducer.java` |
| Substitution | `src/main/java/scala/parser/core/reduction/Substitution.java` |
| MutationAnalysis | `src/main/java/scala/parser/MutationAnalysis.java` |
| TypeErrorBenchmark | `src/main/java/scala/parser/TypeErrorDiagnosisBenchmark.java` |
| BenchmarkSuite | `src/main/java/scala/parser/BenchmarkSuite.java` |
| TypeDynamic | `src/main/java/scala/parser/core/types/TypeDynamic.java` |
| LSP-Server | `src/main/java/scala/parser/lsp/` |
| Tests | `src/test/java/scala/parser/core/` |
| CoverageGuidedPBT | `src/test/java/scala/parser/core/reduction/CoverageGuidedPBT.java` |
| Coq-Formalisierung | `coq/LambdaCore.v` |
| Projektdoku | `projektuebersicht.md` |
| Weitere Doku | `docs/lambda-core.md`, `docs/benchmarks.md`, `docs/architecture.md` |

## Wichtige Constraints

- **408+ Tests** müssen grün bleiben (JUnit 5 + jqwik PBT)
- Alle Änderungen müssen `gradle test` grün halten
- Keine ungefragten Commits
- Coq-Code als `.v`-Datei (Installation nicht auf dev-Maschine verfügbar)
- Mutual Recursion: `mergeMutualDefs()` gruppiert adjazente `def`-Statements
- PBT-Generatoren erzeugen geschlossene (keine freien Variablen), wohlgetypte Ausdrücke
- Preservation nach `step` kann Typ generalisieren (HM-Eigenschaft) → nur Wohlgetyptheit prüfen

## Bekannte Limitationen

- **Equivalent Mutants**: Einige Operatoren (SwapIfBranches, SwapOperator) produzieren oft äquivalente Mutanten (gleicher Output) → 0% Erkennung erwartbar.
- **Coq**: `LambdaCore.v` war vollständig bewiesen (0 Admitted) – kann nur auf Rechner mit Coq-Installation verifiziert werden.
- **Mutation Analysis Output-Vergleich**: `valueEquals` vergleicht Closure-/Constructor-Werte per `toString()` (approximativ) – struktureller Vergleich wäre genauer.
