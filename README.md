# Scala Parser — λ-Calculus Core mit HM-Typinferenz

Ein in Java geschriebener Parser für einen Scala-3-ähnlichen Sprachkern,
der auf einem **bottom-up erweiterten λ-Kalkül** mit **Hindley-Milner-Typinferenz**
(Algorithm W) basiert.

## Eigenschaften

- **Lexer** — Zeichenbasiert mit Fehlertoleranz
- **Parser** — Recursive-Descent mit 14-stufiger Operator-Präzedenz und Error Recovery
- **Typsystem** — Vollständiges HM mit Let-Polymorphismus, ADTs, Pattern Matching, rekursiven Typen
- **Evaluator** — Tree-walkender Interpreter (Big-Step)
- **Small-Step-Reduzierer** — Capture-avoiding Substitution, ein Schritt → ein Schritt
- **LSP-Server** — LSP4J-Integration mit Hover, Completion, Go-to-Definition, Find References, Rename, Semantic Tokens
- **VS-Code-Extension** — TextMate Grammar, Syntax Highlighting, Language Configuration
- **Property-Based Testing** — jqwik (800+ Properties)
- **Feature-Flags** — 12 erweiterbare Sprachfeatures, `upTo()` für stufenweise Aktivierung

## Sprachumfang

| Feature | Beispiel |
|---------|----------|
| λ-Abstraktion | `(x: Int) => x + 1` |
| Let-Bindung | `val x = 42` |
| Rekursive Def | `def fact(n: Int): Int = if (n ≤ 1) 1 else n * fact(n - 1)` |
| If/Unit | `if (x > 0) 1 else -1` / `if (debug) print(x)` |
| ADTs + Pattern Matching | `enum Option[T] { case None; case Some(x: T) }` |
| Arithmetik | `+`, `-`, `*`, `/` |
| Vergleiche | `==`, `!=`, `<`, `>`, `≤`, `≥` |
| Boolesche Operatoren | `&&`, `\|\|`, `!` |
| Tupel | `(1, "hello", true)` |
| Typ-Ascription | `(42: Int)` |
| Type Aliase | `type Seconds = Int` |
| List-Literale | `[1, 2, 3]` (syntaktischer Zucker) |
| Range-Literale | `1..10` |
| Unicode-Operatoren | `≤`, `≥`, `≠`, `→` |

## Bauen

```bash
./gradlew build
```

## Testen

```bash
./gradlew test          # Alle Tests
./gradlew test --tests "*TokenizerTests*"
./gradlew test --tests "*ParserTests*"
./gradlew test --tests "*TypeCheckerTests*"
./gradlew test --tests "*EvaluatorTests*"
./gradlew test --tests "*TypeSoundness*"
./gradlew test --tests "*ScalaMapTest*"
```

## REPL

```bash
./gradlew runRepl
```

## LSP Server

```bash
./gradlew runLanguageServer
```

## Projektstruktur

| Verzeichnis | Inhalt |
|---|---|
| `src/main/java/scala/parser/core/` | Kerngrammatik (Lexer, Parser, Typsystem, Evaluator, Reducer) |
| `src/main/java/scala/parser/lsp/` | LSP-Server-Implementierung |
| `src/test/java/` | Tests (JUnit 5 + jqwik) |
| `docs/` | Formale Semantik, Architektur, Grammatik, Scala-Map |
| `vs-code-extension/` | VS-Code-Integration |

## Dokumentation

- [`docs/lambda-core.md`](docs/lambda-core.md) — Formale λ-Kalkül-Semantik mit Small-Step, Big-Step, Typregeln, Type-Soundness-Beweisen
- [`docs/architecture.md`](docs/architecture.md) — Systemarchitektur mit Mermaid-Diagrammen
- [`docs/grammar.md`](docs/grammar.md) — Vollständige BNF-Grammatik
- [`docs/scala-map.md`](docs/scala-map.md) — 30 Einträge Scala → λ-Core

## Verwandte Publikationen

- Benjamin C. Pierce, *Types and Programming Languages* (TAPL), MIT Press 2002
- Luca Cardelli, *Basic Polymorphic Typechecking* (Algorithm W), 1987
- Martin Odersky et al., *An Overview of the Scala Programming Language*, 2004
- Scala 3 Reference: [https://docs.scala-lang.org/scala3/reference/](https://docs.scala-lang.org/scala3/reference/)
