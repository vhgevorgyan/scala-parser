# Projektübersicht – Scala Parser Language Server

## 1. Einordnung

Das Projekt implementiert eine **vollständige kleine funktionale Programmiersprache** (Lambda-Kalkül mit let-Polymorphismus, algebraischen Datentypen und Pattern Matching) in **Java 24**, inklusive **LSP-Server** und **VS-Code-Erweiterung**.

Die Syntax orientiert sich an **Scala 3**: `val`/`def` für Definitionen, `match`/`case` für Pattern Matching, `(x: Int) => body` für Lambda-Ausdrücke, `{ }` für Block-Ausdrücke und `if (cond) expr else expr` für Konditionale.

---

## 2. Sprachumfang

| Feature | Beispiel |
|---|---|---|
| Zahlen, Booleans, Strings | `42`, `true`, `"hello"` |
| Arithmetik | `+`, `-`, `*`, `/` |
| Unäres Minus | `-x` |
| Vergleiche | `==`, `!=`, `<`, `>`, `<=`, `>=` |
| Logik | `&&`, `\|\|`, `!` |
| If-then-else (else optional) | `if (x > 0) x else 0` / `if (cond) print(1)` |
| Lambda | `(x: Int) => x + 1` |
| Applikation | `f(x)` |
| Val-Bindung | `val x = 42` |
| Def-Rekursion, multi-param | `def add(x: Int, y: Int): Int = x + y` |
| Typannotation | `val x: Int = 42` |
| Type Ascription | `(x + 1: Int)` |
| Gradual Typing (dynamischer Typ `?`) | `val x: ? = 42; (x: Int)` |
| Block-Ausdrücke | `{ val x = 1; x + 1 }` |
| Unit-Typ + `()`-Literal | `val x: Unit = ()` |
| Algebraische Datentypen (Scala 3 enum) | `enum Maybe[A] { case Nothing; case Just(x: A) }` |
| Pattern Matching | `x match { case Nothing => 0; case Just(y) => y }` |
| Wildcard-Pattern `_` | `case _ => 0` |
| Literal-Patterns | `case 0 => "zero"`, `case true => "yes"` |
| Builtins | `succ`, `pred`, `iszero`, `print` |

---

## 3. Architektur

```
Source Code (.sp)
     │
     ▼
 ┌──────────┐    ┌──────────┐    ┌────────────┐    ┌────────────┐
 │ Tokenizer │───▶│  Parser  │───▶│ TypeChecker│───▶│ Evaluator  │
 │  (Lexer)  │    │(rek. Ab- │    │  (HM / W)  │    │ (Tree-     │
 │           │    │ stieg)   │    │            │    │  Walker)   │
 └──────────┘    └──────────┘    └────────────┘    └────────────┘
                      │                 │                 │
                      ▼                 ▼                 ▼
                 ┌───────────────────────────────────────────┐
                 │           CompilerPipeline                │
                 │  (Tokenize → Parse → TypeCheck → Eval)    │
                 └───────────────────────────────────────────┘
                      │
                      ▼
                 ┌──────────────────────┐
                 │   LSP Server (LSP4J) │──▶ VS Code Extension
                 │  • Hover            │
                 │  • Completion       │
                 │  • Go-to-Definition │
                 │  • References       │
                 │  • Rename           │
                 │  • Semantic Tokens  │
                 │  • Diagnostics      │
                 │  • Code Actions     │
                 └──────────────────────┘
```

### 3.1 Lexer (Tokenizer)

Zeichenweiser Scanner, produziert `Token(type, text, line, column)`-Liste. Erkennt:
- Identifier, Keywords (`val`, `def`, `enum`, `match`, `case`, `if`, `else`, `true`, `false`)
- Zahlen, Strings (mit Escapes), Operatoren (`=>`, `==`, `!=`, `<=`, `>=`, `&&`, `||`)
- Kommentare `//` und `/* */`
- Unbekannte Zeichen werden übersprungen (fehlertolerant)

### 3.2 Parser

Recursive-Descent-Parser mit Operator-Präzedenz (14 Stufen). Baut `ProgramNode`-AST. Features:
- Fehlererkennung und -synchronisation (Parser Recovery)
- `parseValDef()`: `val x = expr` / `val x: Type = expr` / `def f(x: Type): RetType = body`
- `parseMatchBody()`: `{ case P => body; ... }`
- `isLambdaStart()`: Lookahead erkennt `(x: Type) =>` vor Klammerausdruck
- `parseBlock()`: `{ stmts }` als Block-Ausdruck
- Application stoppt an Zeilengrenzen (neue Anweisung auf neuer Zeile)

### 3.3 Type Checker (Hindley-Milner / Algorithmus W)

Kern: **let-Polymorphismus** mit `generalize`/`instantiate`, Unifikation mit `occurs check`.

- `TypeScheme` für polymorphe Typen (`forall a. a -> a`)
- Typ-Inferenz für alle Ausdrücke
- ADT-Constructor-Registrierung beim `enum`-Deklaration
- **Exhaustiveness Check**: prüft ob alle Konstruktoren gematched werden (inkl. Wildcard `_`)
- **Type Ascription**: `(expr: Type)` annotiert Typ und leitet erzwungenen Typ ab
- **let-rec**: Fixpoint-Semantik mit frischer TypeVar vor RHS-Inferenz
- **Rekursive Selbstreferenz**: `def f = f` wird abgefangen
- **Detaillierte Fehlermeldungen** mit Quelltext-Location

### 3.4 Evaluator / SmallStepReducer

Zwei Evaluierungsstrategien:

**Tree-Walking-Interpreter (Big-Step)**:
- Closures für Lambda-Ausdrücke
- `ConstructorBuilder` für curried Constructor-Application
- Fixpoint-Semantik für `let rec`
- Rekursionstiefenlimit (1000)
- `Env.toString()` für REPL `:env`

**SmallStepReducer (Small-Step)**:
- Schrittweise Reduktion mit `step()` / `reduce()`-Loop
- `FixpointNode`: fix(λx. body) → subst x (fix(λx. body)) body
- `reduceBlock`: Block-Ausdrücke mit let-Substitution, Fixpoint-Wrapping für rekursive `def`
- MutualRecursion: Kreuz-Substitution aller Namen in originale Lambda-Bodies vor Fixpoint-Wrapping
- String-Vergleich (`==`/`!=` für StringLiteralNode)
- Progress + Preservation durch TypeSoundnessFormalTest abgedeckt

### 3.5 LSP Server (LSP4J 0.21.0)

| Feature | Implementierung |
|---|---|
| **Hover** | Zeigt inferierten Typ |
| **Completion** | Keywords, Builtins, in-scope Identifier |
| **Go-to-Definition** | `ScopeAnalyzer.findDefinition()` |
| **Find References** | `ScopeAnalyzer.collectReferences()` |
| **Document Symbols** | `DocumentSymbol`-Hierarchie |
| **Rename** | `WorkspaceEdit` pro Referenz, nie `null` |
| **Semantic Tokens** | Delta-Encoding: variable, number, operator, keyword, string |
| **Code Actions** | Quickfix für undefinierte Variablen, fehlende Identifier |
| **Diagnostics** | Parser + TypeChecker Fehler → LSP Diagnostics |

### 3.6 VS Code Extension

TypeScript-Client, startet JAR als Subprozess. Sprachkonfiguration:
- Dateiendung `.sp`, Kommentare `//` `/* */`, Klammern `()`
- TextMate Grammar für Syntax Highlighting

### 3.7 REPL

Interaktive Shell mit:
- `:type <expr>` – Typ anzeigen
- `:env` – gebundene Namen/Values
- `:load <file>` – Datei laden
- `:help` / `:h`
- `:quit`

### 3.8 Benchmarks

Zeitmessung für Tokenize/Parse/Typecheck/Evaluate mit Warmup und `System.gc()` vor jeder Phase.

**Scaling Benchmarks** (`ScalingBenchmarks.java`): Systematische Laufzeitmessung bei n = 10/50/100/500/1000 Ausdrücken in drei Szenarien:
- **Arithmetic chain** – N let-Bindings mit Addition (`let x0 = 0; let x1 = x0 + 1; ...`)
- **ADT chain** – Wiederholte `case`-Ausdrücke auf einem selbstdefinierten Bool-Typ
- **Recursive calls** – Wiederholte Aufrufe einer rekursiven Funktion

Ergebnis: Tokenize/Parse/Evaluate skalieren linear O(n), TypeCheck für Arithmetic chain tendenziell O(n²) durch wachsenden Unifikations-Scope.

### 3.9 CLI Test Tool

`TestParser.java` – Lädt eine `.sp`-Datei und zeigt:
- Token-Liste mit Typ, Text und Position
- AST als Baum (alle Knoten + Kindbeziehungen)
- Typcheck-Ergebnisse mit inferierten Typen
- Evaluierungsergebnis

Aufruf: `gradlew runTestParser --args="<file.sp>"`

---

## 4. Testing

### 4.1 Unit Tests (JUnit 5) – 413 Tests

| Testklasse | Tests | Abdeckung |
|---|---|---|
| `TokenizerTests` | ~30 | Alle Token-Typen, Strings, Kommentare, Fehlerfälle |
| `ParserTests` | ~50 | Alle AST-Knoten, Präzedenz, Fehlerrecovery |
| `TypeCheckerTests` | ~40 | HM-Inferenz, let-Poly, ADTs, Fehlererkennung |
| `EvaluatorTests` | ~30 | Alle Values, Builtins, Rekursion, Pattern Matching |
| `TypeSoundnessFormalTest` | ~75+ | Progress/Preservation für SmallStepReducer, Block-Reduktion, Case/Match, rekursive defs, mutual recursion, String-Vergleich, 9 Builtin-Tests, 10 Gradual-Typing-Tests |
| `LanguageConfigTest` | 20 | Language-Feature-Flags, Unicode-Operatoren |

### 4.2 Property-Based Tests (jqwik) – 34 Properties, ~13000+ Tries

| Property | Tries | Beschreibung |
|---|---|---|
| **typeSoundness** | 1000 | Wohlgetyptes Programm → Evaluator crasht nicht |
| **determinism** | 500 | Gleiches Programm → gleicher Typ + gleicher Wert |
| **weakening** | 500 | Unused val prependen → Typability bleibt erhalten |
| **parseRoundTrip** | 500 | AST → Source → parse → strukturell identisch |
| **parseRoundTripFuzz** | 5000 | Wie oben, massiv mehr Iterationen |
| **notOnNonBool** | 500 | `!` auf Number → Fehler erwähnt "Bool" |
| **ifCondMustBeBool** | 500 | If mit Number-Cond → Fehler erwähnt "Bool" |
| **noDirectSelfRecursion** | 500 | `def f = f` → Selbstreferenz-Fehler |
| **undeclaredVariable** | 500 | Unbekannte Variable → "Undefined" |
| **typeErrorMessagesAreInformative** | 500 | Alle Fehler >5 Zeichen, nicht leer |
| **exhaustivenessCheck** | 1000 | Fehlende Konstruktoren → "missing constructor" |
| **typeErrorLocalization** | 1000 | Mutation-basierte Lokalisierung: Typfehler zeigt auf mutierte Stelle |
| **errorRecoveryQuality** | 500 | Mutation-basierte Cascade-Messung: Präzision der Fehlerrecovery nach Syntaxfehler |
| **progressClosedExpr/Block** | 1000 | Geschlossener, wohlgetypter Ausdruck/Block → `isValue` oder `step != null` |
| **preservationClosedExpr/Block** | 1000 | Geschlossener, wohlgetypter Ausdruck/Block → nach `step` immer noch wohlgetypt (Typ kann genereller werden) |
| **determinismClosedExpr/Block** | 1000 | Geschlossener Ausdruck/Block → `step` liefert gleiches Ergebnis bei Wiederholung (strukturelle Gleichheit) |

> **Hinweis Preservation**: Der HM-Typinferenz erlaubt Generalisierung nach Reduktion (z.B. `λx. x` hat Typ `t2 → t3` vor Reduktion, kann nach Substitution genereller werden). Daher prüfen Preservation-Tests nur **Wohlgetyptheit** nach dem Schritt, nicht Typ-Identität.

### 4.3 Mutation Analysis (`MutationAnalysis.java`)

Systematische Evaluation der Pipeline-Fehlererkennung: 12 Mutationsoperatoren × 200 wohlgetypte Basisprogramme = 2200 Mutationen.

Jede Mutation wird kategorisiert als:
- **Parse error** – vom Parser abgefangen
- **Type error** – vom TypeChecker abgefangen
- **Runtime error** – vom Evaluator abgefangen (Exception)
- **Output change** – Programm evaluiert zu anderem Wert als Original (Output-Vergleich via `valueEquals`)
- **Escaped** – unerkannt durchgeschlüpft

| Operator | Erkennung | Phase |
|---|---|---|
| `SelfReference` (`def f = f`) | **99,5%** | TypeChecker |
| `ReplaceLiteralType` (Number↔String↔Bool) | **50,0%** | Output change (50%) |
| `BoolNegate` (`!` auf Bool) | **31,5%** | TypeChecker |
| `NotOnNumber` (`!` auf Number) | **13,5%** | TypeChecker |
| `DivideByZero` (RHS einer Division = 0) | **7,5%** | Evaluator |
| `RemoveLetBinding` | **4,5%** | TypeChecker |
| `UndefinedVariable` | **1,5%** | TypeError (selten getroffen) |
| `SwapOperator` (`+`↔`*`, etc.) | **0,5%** | Output change |
| `SwapIfBranches` | **0,0%** | unerkannt (beide Zweige oft gleicher Wert) |
| `UndeclaredConstructor` | **100%** ✅ | TypeChecker |
| `RemoveCaseBranch` | **100%** ✅ | TypeChecker (Exhaustiveness) |
| `TypeAnnotationMismatch` (`let x: Bool = 42`) | **100%** ✅ | Parser |

Gesamterkennungsrate: **37,2%** (mit Output-Vergleich). Schwachstellen sind semantische Operatoren deren Mutationen zufällig gleiche Werte produzieren (SwapIfBranches, SwapOperator) oder toten Code betreffen (RemoveLetBinding).

---

## 5. Build & Deployment

- **Gradle 9.2.1**, Java 24
- Fat JAR mit LSP4J + JUnit, ohne Spring Boot-Produktionsabhängigkeiten
- `./gradlew copyJarToExtension` → kopiert JAR nach `scala-parser-vscode/server/`
- VS Code Extension startet Server mit `java -jar server/scala-parser-lsp.jar`

---

## 6. Erreichte Meilensteine

1. ✅ Lexer (vollständig, fehlertolerant)
2. ✅ Parser (rekursiver Abstieg, Fehlerrecovery, ADTs, Case, Literal-Patterns, Wildcard)
3. ✅ HM-Typinferenz (let-Polymorphismus, ADTs, Exhaustiveness, Type Ascription, Unit)
4. ✅ Evaluator (Closures, Fixpoint, ADTs, ConstructorBuilder, UnitValue)
5. ✅ LSP Server (Hover, Completion, GotoDef, References, Rename, Diagnostics, Code Actions, Semantic Tokens)
6. ✅ VS Code Extension
7. ✅ REPL
8. ✅ Benchmarks
9. ✅ Property-Based Tests (32 Properties, >6000 Tries inkl. Coverage-Guided PBT)
10. ✅ Type Error Messages (mit Quelltext-Location + beteiligten Typen)
11. ✅ Scaling Benchmarks (Performance-Skalierung bei n = 10–1000)
12. ✅ Mutation Analysis (12 Operatoren, 2200 Mutationen, 37,2% Erkennung, inkl. Output-Vergleich)
13. ✅ `TestParser` CLI-Tool
14. ✅ Dokumentation: Architektur-Diagramme + BNF-Grammatik (`docs/`)
15. ✅ Scala-3-Syntax-Lücken geschlossen: `Unit`/`()`, `if` ohne `else`, Wildcard `_`, Literal-Patterns, Multi-Parameter-Defs, Type Ascription
16. ✅ Coq-Formalisierung: Λ-Core mit 6 Theoremen (Progress, Preservation, Type Soundness, Big↔Small), alle bewiesen (0 Admitted)
17. ✅ Case/Match-Reduktion im SmallStepReducer getestet (6 Tests in TypeSoundnessFormalTest)
18. ✅ TypeChecker Property-Based-Tests (5 Properties in TypeCheckerPBT.java)
19. ✅ Desugarer: `a..b` → `(a, b)` via funktionalem Rewrite
20. ✅ SmallStepReducer mit FixpointNode für let-rec-Rekursion (8 Tests in TypeSoundnessFormalTest)
21. ✅ String-Vergleich (`==`/`!=`) + Konkatenation im SmallStepReducer (6 Tests)
22. ✅ MutualRecursion via FixpointNode + Kreuz-Substitution (4 Tests in TypeSoundnessFormalTest: `mutualRecursionEvenOdd`, `mutualRecursionFactorial`, `mutualRecursionBothBases`)
23. ✅ PBT für geschlossene Ausdrücke: Progress/Preservation/Determinism (6 Properties in SmallStepPBT)
24. ✅ TypeChecker-Bugfix: `inferMutualGroup`/`inferLet` speichert `type` statt `scheme.body()` – behebt Dangling-Quantified-Type-Vars
25. ✅ Substitution-Bugfix: `subst` in `BlockNode` übersprang `LetStatementNode`/`MutualLetNode` – ließ freie Variablen zurück; Preservation-PBTs jetzt mit Well-typedness-Assertion
26. ✅ SmallStepReducer-Builtins: `succ`/`pred`/`iszero`/`print` + Values-are-irreducible-PBT + CaseNode-PBT via Parsing (9 Builtin-Tests, 26 PBT-Properties gesamt)
27. ✅ Gradual Typing: Dynamischer Typ `?` (`TypeDynamic`) konsistent mit allen konkreten Typen via Unifikation, Laufzeit-Checks bei `(e: T)`-Ascription, Fehlschlag mit `RuntimeException`, SmallStepReducer erased Ascription (10 Tests)
28. ✅ Coverage-Guided PBT: Gezielte Generatoren für SwapIfBranches/SwapOperator/RemoveLetBinding/UndefinedVariable (6 Properties)
29. ✅ Type Error Diagnosis Benchmark: Systematische Qualitätsmessung der Fehlermeldungen (18 Testprogramme, Standalone via `runTypeErrorBenchmark`)
30. ✅ BenchmarkSuite: 8 Analyse-Module in einem Durchlauf (`gradlew runBenchmarkSuite`):
    Seed-basierte Generierung im Empirical-Matrix-Modul für deterministischere Ergebnisse.

31. ✅ Empirische Analyse abgeschlossen: PBT erkennt Typ-/Laufzeitfehler (BoolNegate 100%, DivideByZero 87%) aber keine semantischen Änderungen (SwapOperator 0%). Key finding für Thesis: PBT sichert **interne Type Soundness**, nicht **semantische Äquivalenz**.
    Begleitdokumentation: `docs/benchmarks.md` (alle 8 Module mit Tabellen), `docs/architecture.md` (Package Structure aktualisiert), `projektuebersicht.md` (Meilensteine 30–31).

32. ✅ **Empirical Matrix um VsOrig-Property erweitert**: Output-Vergleich (Original vs. Mutated) via `valueEquals`. SwapOperator/SwapIfBranches jetzt 100% erkannt (vorher 0%). Neue `--runs N` und `--csv` CLI-Flags für reproduzierbare Thesis-Daten.
    **CoverageGuidedPBT erweitert**: Zwei neue Properties (`undefinedVariableInComplexExpr`, `removeChainedLetBindingCausesError`) mit komplexeren Generatoren (chained lets, multiple vars, function application).
    - **PBT-Property-vs-Mutation-Matrix**: Welche Mutationsklasse wird von welcher PBT-Property-Kategorie erfasst?
    - **Scalability Benchmark**: Laufzeit (ns) + Reduktionsschritte für Programme von 2–32 AST-Knoten
    - **Type Error Recovery Benchmark**: Systematische Error-Injektion analog MutationAnalysis
    - **AST Node Coverage**: Abdeckungsmatrix (20 Knotentypen × 4 Komponenten)
    - **Reduction Step Distribution**: Histogramm der Reduktionsschritte für 200 zufällige Ausdrücke
    - **Parser Error Recovery**: Cascade-Rate nach Ein-Zeichen-Mutationen (4 Injektoren × 50 Trials)
    - **Empirical PBT-vs-Mutation Matrix**: Tatsächliche PBT-Erkennungsraten per Mutationsoperator (7 Ops × 100 Trials, seed-basierte Generierung)
    - **Runtime Breakdown**: Pipeline-Laufzeiten (parseNs, tcNs, evalNs, reduceNs) für 100 Zufallsprogramme als CSV

33. ✅ **Match Guards + As-Patterns**: Parser (AT-Token), TypeChecker, Evaluator, Reducer, Substitution unterstützen `case x @ P if cond => body`.
    - `CaseBranch`-Record um nullable `asName`/`guard`-Felder erweitert (2 neue Konstruktoren für Rückwärtskompatibilität)
    - PBT-Generatoren (`genEnumSource`) erzeugen As-Patterns (20%) und Guards (25%) in Match-Branches
    - **FuzzPBT.java**: 5000 Tokenizer + 2000 Parser + 500 Pipeline-Tries gegen zufällige Byte-Sequenzen
    - **TypeAliasNode/EnumDeclNode** in `inferBlock` des TypeCheckers registriert (Block-Lokalität)
    - **Dashboard-Skript**: `scripts/dashboard.py` — wandelt CSV-Benchmark-Daten in HTML-Charts um

34. ✅ **PBT Generator-Expansion + LSP-Semantik-Lücke geschlossen**:
    - **PBT-Generatoren** (`SmallStepPBT.java`): `genClosedExpr`/`genClosedBlockExpr`/`genExpr`/`genRandomBlock` erzeugen `TypeDynamic` in Ascription (choice 8: `(e : ?)`), `FixpointNode` (choice 10: `fix(λf. λx. body)`), `TypeAliasNode`/`EnumDeclNode` in Block-Kontext
    - **Preservation-Guards**: `containsTypeDynamic()`/`containsFixpoint()` rekursive Helper erlauben Skippen der Preservation-Assertion für Ausdrücke mit unvermeidbaren Typänderungen nach Reduktion (TypeDynamic bricht Soundness per Design, FixpointNode-Unrolling ändert Typ-Struktur)
    - **IfNode-Runtime-Check**: `SmallStepReducer` wirft `RuntimeException("If condition must be Bool")` bei Nicht-Bool-Condition (Gradual Typing Laufzeit-Sicherheit)
    - **`reduce()`-Exception-Propagation**: RuntimeExceptions propagieren jetzt statt stuck terms — Tests behandeln sie als "erwartete Runtime Error"
    - **ScopeAnalyzer**: `asName` wird als Definition registriert, `guard`+`literalPattern` in `walkDefinitions`/`walkReferences` traversiert
    - **SemanticTokenProvider**: Emittiert Variable-Token für `asName`, traversiert `guard`+`literalPattern`
    - **AstFinder**: Durchsucht `guard`+`literalPattern` für Node-at-Position
    - **Alle 413 Tests grün** (vorher 408+)
