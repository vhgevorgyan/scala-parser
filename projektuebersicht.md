# Projektübersicht – Scala Parser Language Server

## 1. Einordnung

Das Projekt implementiert eine **vollständige kleine funktionale Programmiersprache** (Lambda-Kalkül mit let-Polymorphismus, algebraischen Datentypen und Pattern Matching) in **Java 24**, inklusive **LSP-Server** und **VS-Code-Erweiterung**.

---

## 2. Sprachumfang

| Feature | Beispiel |
|---|---|
| Zahlen, Booleans, Strings | `42`, `true`, `"hello"` |
| Arithmetik | `+`, `-`, `*`, `/` |
| Vergleiche | `==`, `!=`, `<`, `>`, `<=`, `>=` |
| Logik | `&&`, `\|\|`, `!` |
| If-then-else | `if x > 0 then x else 0` |
| Lambda | `\x -> x + 1` |
| Applikation | `f x` |
| Let-Bindung | `let x = 42 in x` |
| Rekursion | `let rec fact = \n -> if n==0 then 1 else n * fact (n-1)` |
| Typannotation | `let x : Number = 42` |
| Algebraische Datentypen | `data Maybe a = Nothing \| Just a` |
| Pattern Matching | `case x of Nothing -> 0 \| Just y -> y` |
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
- Identifier, Keywords (`let`, `rec`, `data`, `case`, `of`, `if`, `then`, `else`, `true`, `false`)
- Zahlen, Strings (mit Escapes), Operatoren
- Kommentare `//` und `/* */`
- Unbekannte Zeichen werden übersprungen (fehlertolerant)

### 3.2 Parser

Recursive-Descent-Parser mit Operator-Präzedenz (14 Stufen). Baut `ProgramNode`-AST. Features:
- Fehlererkennung und -synchronisation (Parser Recovery)
- `parseCase()`: PIPE-getrennte Branches
- `parseType()`: Typannotationen
- Lookahead für `rec` bei `let`

### 3.3 Type Checker (Hindley-Milner / Algorithmus W)

Kern: **let-Polymorphismus** mit `generalize`/`instantiate`, Unifikation mit `occurs check`.

- `TypeScheme` für polymorphe Typen (`forall a. a -> a`)
- Typ-Inferenz für alle Ausdrücke
- ADT-Constructor-Registrierung beim `data`-Deklaration
- **Exhaustiveness Check**: prüft ob alle Konstruktoren gematched werden
- **let-rec**: Fixpoint-Semantik mit frischer TypeVar vor RHS-Inferenz
- **let-rec Selbstreferenz**: `let rec f = f` wird abgefangen
- Detaillierte Fehlermeldungen mit Quelltext-Location

### 3.4 Evaluator

Tree-Walking-Interpreter mit environments (linked-list of scopes):
- Closures für Lambda-Ausdrücke
- `ConstructorBuilder` für curried Constructor-Application
- Fixpoint-Semantik für `let rec`
- Rekursionstiefenlimit (1000)
- `Env.toString()` für REPL `:env`

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

---

## 4. Testing

### 4.1 Unit Tests (JUnit 5) – 146+ Tests

| Testklasse | Tests | Abdeckung |
|---|---|---|
| `TokenizerTests` | ~30 | Alle Token-Typen, Strings, Kommentare, Fehlerfälle |
| `ParserTests` | ~50 | Alle AST-Knoten, Präzedenz, Fehlerrecovery |
| `TypeCheckerTests` | ~40 | HM-Inferenz, let-Poly, ADTs, Fehlererkennung |
| `EvaluatorTests` | ~30 | Alle Values, Builtins, Rekursion, Pattern Matching |

### 4.2 Property-Based Tests (jqwik) – 9000+ Tries

| Property | Tries | Beschreibung |
|---|---|---|
| **typeSoundness** | 1000 | Wohlgetyptes Programm → Evaluator crasht nicht |
| **determinism** | 500 | Gleiches Programm → gleicher Typ + gleicher Wert |
| **weakening** | 500 | Unused Let prependen → Typability bleibt erhalten |
| **parseRoundTrip** | 500 | AST → Source → parse → strukturell identisch |
| **parseRoundTripFuzz** | 5000 | Wie oben, massiv mehr Iterationen |
| **notOnNonBool** | 500 | `!` auf Number → Fehler erwähnt "Bool" |
| **ifCondMustBeBool** | 500 | If mit Number-Cond → Fehler erwähnt "Bool" |
| **noDirectSelfRecursion** | 500 | `let rec f = f` → Selbstreferenz-Fehler |
| **undeclaredVariable** | 500 | Unbekannte Variable → "Undefined" |
| **typeErrorMessagesAreInformative** | 500 | Alle Fehler >5 Zeichen, nicht leer |
| **exhaustivenessCheck** | 1000 | Fehlende Konstruktoren → "missing constructor" |
| **typeErrorLocalization** | 1000 | Mutation-basierte Lokalisierung: Typfehler zeigt auf mutierte Stelle |

---

## 5. Build & Deployment

- **Gradle 9.2.1**, Java 24
- Fat JAR mit LSP4J + JUnit, ohne Spring Boot-Produktionsabhängigkeiten
- `./gradlew copyJarToExtension` → kopiert JAR nach `scala-parser-vscode/server/`
- VS Code Extension startet Server mit `java -jar server/scala-parser-lsp.jar`

---

## 6. Erreichte Meilensteine

1. ✅ Lexer (vollständig, fehlertolerant)
2. ✅ Parser (rekursiver Abstieg, Fehlerrecovery, ADTs, Case)
3. ✅ HM-Typinferenz (let-Polymorphismus, ADTs, Exhaustiveness)
4. ✅ Evaluator (Closures, Fixpoint, ADTs, ConstructorBuilder)
5. ✅ LSP Server (Hover, Completion, GotoDef, References, Rename, Diagnostics, Code Actions, Semantic Tokens)
6. ✅ VS Code Extension
7. ✅ REPL
8. ✅ Benchmarks
9. ✅ Property-Based Tests (11 Properties, >8000 Tries)
10. ✅ Type Error Messages (mit Quelltext-Location + beteiligten Typen)
