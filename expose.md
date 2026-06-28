# Masterarbeit – Exposé

## Funktionale Programmiersprache mit Hindley-Milner-Typinferenz:
Entwurf, Implementierung und LSP-Integration

---

## 1. Motivation

Moderne Code-Editoren wie VS Code basieren auf dem Language Server Protocol (LSP), das sprachspezifische Dienste wie Autovervollständigung, Typhinweise und Fehlerdiagnose standardisiert. Die Implementierung eines LSP-Servers erfordert die tiefe Integration von Lexer, Parser, Typinferenz und Evaluator.

Diese Arbeit implementiert eine **vollständige, kleine funktionale Programmiersprache** (Lambda-Kalkül mit let-Polymorphismus, algebraischen Datentypen und Pattern Matching) mit **Hindley-Milner-Typinferenz (Algorithmus W)** und koppelt sie an einen **LSP-Server**, der in VS Code genutzt werden kann.

Ziel ist es, den gesamten Pipeline-Weg von der Eingabezeile über lexikalische Analyse, syntaktische Analyse, Typinferenz und Auswertung bis hin zur IDE-Integration praktisch und theoretisch fundiert darzustellen.

---

## 2. Sprachdesign

Die Sprache ist eine **an Scala 3 angelehnte, funktionale Minimalsprache** mit HM-Typinferenz:

```
-- Primitive Typen: Int, Boolean, String, Unit
val x = 42
val u: Unit = ()

-- Lambda:
(x: Int) => x + 1

-- Applikation:
f(x)

-- Unäres Minus:
-x

-- Boolsche Operatoren: &&, ||, !
-- Vergleiche: ==, !=, <, >, <=, >=
-- Arithmetik: +, -, *, /

-- Fallunterscheidung (else optional):
if (x > 0) x else -x

-- Rekursion (def, mit mehreren Parametern):
def add(x: Int, y: Int): Int = x + y
def fact(n: Int): Int =
  if (n == 0) 1 else n * fact(n - 1)

-- Typannotation:
val x: Int = 42

-- Type Ascription:
(x + 1: Int)

-- Block-Ausdruck:
val r = { val a = 1; a + 1 }

-- Algebraischer Datentyp (Scala 3 enum):
enum Maybe[A] {
  case Nothing
  case Just(x: A)
}

-- Pattern Matching (match):
x match {
  case Nothing => 0
  case Just(y)  => y
}

-- Literal-Patterns und Wildcard:
x match {
  case 0 => "zero"
  case 1 => "one"
  case _ => "many"
}
```

**Besonderheiten:**
- Keine Seiteneffekte (rein funktional, außer `print`)
- Polymorphe let-Bindungen (let-Polymorphismus nach Hindley-Milner)
- Currying (alle Funktionen sind einstellig)
- Pattern Matching mit Konstruktoren, Literalen und Wildcard

---

## 3. Kernthemen

### 3.1 Lexer (Tokenisierung)

Zeichenweises Scannen mit fehlertoleranter Behandlung:
- Erkennung von Keywords (`val`, `def`, `enum`, `match`, `case`, `if`, `else`, `true`, `false`), Identifiern, Zahlen, Strings, Operatoren (`=>`, `==`, `!=`, `<=`, `>=`, `&&`, `||`)
- Kommentare `//` und `/* */`
- Unbekannte Zeichen werden übersprungen (kein Abbruch)

### 3.2 Parser (syntaktische Analyse)

Recursive-Descent-Parser mit Operator-Präzedenz (14 Stufen):
- Aufbau eines AST (Abstract Syntax Tree)
- Fehlerrecovery (Synchronisation bei Syntaxfehlern)
- `parseValDef()`: `val x = expr` / `val x: Type = expr` / `def f(x: Type, ...): RetType = body` (mehrere Parameter mit Komma)
- `parseMatchBody()`: `{ case P => body; ... }` (Konstruktor-, Literal- und Wildcard-Patterns)
- `isLambdaStart()`: Lookahead erkennt `(x: Type) =>` vor Klammerausdruck
- `parseBlock()`: `{ stmts }` als Block-Ausdruck
- Applikation via `f(x)`-Syntax (paren-arguments)

### 3.3 Typinferenz (Hindley-Milner / Algorithmus W)

Herzstück der Arbeit:
- **Unifikation** mit Occurs-Check
- **let-Polymorphismus**: `generalize` → `instantiate`
- **Type Schemes**: `forall a. a -> a`
- **ADT-Constructor-Registrierung** bei enum-Deklarationen
- **Exhaustiveness-Prüfung** für Pattern Matching (inkl. Wildcard `_`-Abdeckung)
- **Type Ascription**: `(expr: Type)` für explizite Typannotation in Ausdrücken
- **Unit-Typ**: `Unit` als vollwertiger Typ mit `()`-Literal
- Fehlermeldungen mit Quelltext-Position und beteiligten Typen

### 3.4 Evaluator (Interpretation)

Tree-Walking-Interpreter mit Environments:
- Closures (Lexical Scoping)
- ConstructorBuilder (curried Application für ADT-Konstruktoren)
- Fixpoint-Semantik für let rec
- Rekursionstiefenlimit (Sicherheit)

### 3.5 LSP-Integration

Anbindung an das Language Server Protocol über LSP4J:
| Feature | Nutzen |
|---|---|
| Hover | Typ der aktuellen Expression anzeigen |
| Completion | Keywords + Identifier vorschlagen |
| Go-to-Definition | Zur Definition springen |
| Find References | Alle Referenzen finden |
| Rename | Symbol umbenennen |
| Semantic Tokens | Syntax-Highlighting |
| Diagnostics | Fehler in der Editor-Oberfläche |
| Code Actions | Quickfixes für häufige Fehler |

### 3.6 VS Code Extension

TypeScript-Client, der den LSP-Server als Java-Subprozess startet:
- TextMate Grammar für Syntax Highlighting
- Language Configuration (Kommentare, Klammern)

---

## 4. Teststrategie

### 4.1 Unit Tests (JUnit 5)

Systematische Tests aller Komponenten:
- **Lexer**: Alle Token-Typen, Kommentare, Sonderfälle, Fehler
- **Parser**: Alle AST-Knoten, Präzedenz, Fehlerrecovery
- **Type Checker**: HM-Inferenz, Polymorphie, ADTs, Fehlererkennung
- **Evaluator**: Alle Values, Builtins, Rekursion, Pattern Matching

### 4.2 Property-Based Tests (jqwik) – 13 Properties, ~9500+ Tries

Zufallsgenerierte Programme mit Invarianten:

| Kategorie | Property | Beschreibung |
|---|---|---|
| **Soundness** | Type Soundness | Wohlgetypt → kein Laufzeitfehler |
| **Stabilität** | Determinism | Gleiches Programm → gleicher Typ + Wert |
| | Weakening | Unused val ändert Typability nicht |
| **Round-Trip** | Parse Round-Trip | AST → Source → parse → identisch |
| | Fuzz (5000×) | Wie oben, massiv mehr Iterationen |
| **Fehlererkennung** | notOnNonBool | `!` auf Number → Fehler erwähnt Bool |
| | ifCondMustBeBool | If mit Number → Fehler erwähnt Bool |
| | noDirectSelfRecursion | `def f = f` wird abgefangen |
| | undeclaredVariable | Unbekannte Variable → "Undefined" |
| | exhaustivenessCheck | Fehlende Konstruktoren → "missing constructor" |
| **Error Quality** | typeErrorMessagesAreInformative | Fehler >5 Zeichen, nicht leer |
| | **typeErrorLocalization** | Mutation: Typfehler zeigt auf die mutierte Stelle (1000×) |
| **Recovery** | **errorRecoveryQuality** | Mutation: Cascade-Messung der Fehlerrecovery (500×) |

### 4.3 Mutation Analysis

Zusätzlich zur Property-Based-Testing wurde eine systematische **Mutationsanalyse** (`MutationAnalysis.java`) mit 12 Operatoren × 200 wohlgetypten Programmen = 2000 Mutationen durchgeführt. Jede Mutation wird als Parse-, Typ- oder Laufzeitfehler kategorisiert oder als unerkannt ("escaped") markiert. Die Gesamterkennungsrate beträgt 36,5%, wobei strukturelle Mutationen (SelfReference: 100%, TypeAnnotationMismatch: 100%) zuverlässig erkannt werden, während semantische Änderungen (Operator-Swap, Branch-Swap: 0%) typkorrekt bleiben.

### 4.4 Scaling Benchmarks

Systematische Performance-Messung (`ScalingBenchmarks.java`) für Tokenize/Parse/Typecheck/Evaluate bei Eingabegrößen n = 10/50/100/500/1000 in drei Szenarien (arithmetische Kette, ADT-Kette, rekursive Aufrufe).

---

## 5. Technologien

| Komponente | Technologie |
|---|---|
| Sprache | Java 24 |
| Build | Gradle 9.x |
| LSP | LSP4J 0.21.0 |
| Testing | JUnit 5 + jqwik 1.9.2 |
| IDE Integration | VS Code (TypeScript) |
| Pattern | Sealed Interfaces, Records, Streams |

---

## 6. Wissenschafliche Beiträge

1. **Vollständige Pipeline** von der lexikalischen Analyse bis zur IDE-Integration einer kleinen funktionalen Sprache
2. **HM-Typinferenz mit ADTs**: Implementierung von Algorithmus W mit algebraischen Datentypen, Typkonstruktoren und let-Polymorphismus
3. **Exhaustiveness Checking** für Pattern Matching im Kontext von HM-Inferenz
4. **Property-Based Testing** für Typsysteme: Automatisierte Generierung von Programmen mit Invarianten-Prüfung
5. **LSP-Integration als Fallstudie**: Wie tiefe Sprachfeatures (Typinferenz, ADTs) in moderne Editoren eingebettet werden
6. **Mutation-basierte Fehlerlokalisierung**: Automatisierte Evaluation der Qualität von Typfehler-Meldungen durch zufällige Mutation und Positionsprüfung
7. **Fehlerrecovery-Evaluation**: Messung der Cascade-Effekte nach Syntaxfehlern mittels Token-Mutation
8. **Performance-Skalierungsanalyse**: Systematische Laufzeitmessung über Eingabegrößen n = 10–1000 für alle Pipeline-Phasen
9. **Mutationsanalyse der Pipeline**: 12 Mutationsoperatoren, 2000 Mutationen – quantitative Evaluation der Fehlererkennungsrate (36,5% Gesamterkennung, 100% für SelfReference und TypeAnnotationMismatch)

---

## 7. Zeitplan (Vorschlag)

| Phase | Dauer |
|---|---|
| Sprachdesign + AST | 2 Wochen |
| Lexer + Parser | 3 Wochen |
| HM-Typinferenz | 4 Wochen |
| Evaluator | 2 Wochen |
| LSP-Server | 3 Wochen |
| VS Code Extension | 1 Woche |
| Tests (Unit + PBT) | 2 Wochen |
| Benchmarking | 1 Woche |
| Ausarbeitung | 4 Wochen |
| **Gesamt** | **~22 Wochen** |

---

## 8. Related Work

### 8.1 MiniML (Leroy et al., 1990er–heute)

Die Referenzimplementierung der Hindley-Milner-Typinferenz in OCaml. MiniML dient als Lehrsprache im TAPL-Kurs von Pierce und ist der historische Ausgangspunkt für viele HM-basierte Sprachprototypen. Unsere Arbeit unterscheidet sich durch:
- Direkte Integration in eine **IDE (LSP/VS Code)**, nicht nur Compiler/REPL
- **Erweiterte Sprachfeatures**: ADTs, Pattern Matching, Blocksyntax
- **Nicht in OCaml**, sondern in Java — als Demonstrator für die Übertragbarkeit des Algorithmus
- **Systematische Mutation- und Property-Based-Tests**, die über die typischen Unit-Tests von MiniML hinausgehen

### 8.2 Plait (Felleisen et al., 2018)

Plait ist eine in Racket eingebettete Sprache für den Programmiergrundlagenunterricht, die ein HM-ähnliches Typsystem mit algebraischen Datentypen bietet. Vergleichspunkte:
- Plait setzt auf **gradual typing** und Fehlermeldungen für Anfänger, unser System auf **vollständige HM-Inferenz** mit let-Polymorphismus
- Plait-Programme werden in Racket ausgeführt; unser System hat einen eigenständigen **Tree-Walking-Evaluator** und **Small-Step-Reduzierer**
- Beide Systeme unterstützen Pattern Matching und rekursive Definitionen

### 8.3 LambdaJS (Guerraoui et al., 2013)

LambdaJS ist ein Kernkalkül zur Formalisierung von JavaScript mit HM-Typinferenz. Relevanz:
- Zeigt, dass HM auch auf **imperative Sprachfeatures** anwendbar ist (unsere Sprache ist rein funktional)
- LambdaJS hat **keine LSP-Integration** und keinen eigenständigen Evaluator
- Unser formales Modell in `docs/lambda-core.md` ist vergleichbar detailliert, aber auf einen **Scala-3-artigen Kern** zugeschnitten

### 8.4 DOT-Kalkül (Odersky et al., 2016–2024)

Der Dependent Object Types (DOT)-Kalkül ist die formale Grundlage von Scala 3 und wesentlich mächtiger (Pfadabhängige Typen, Intersection/Union-Typen, Type Member). Unsere Arbeit:
- Bleibt bewusst **einfach (HM + ADTs)**, um die Pipeline-Klarheit zu wahren
- Ersetzt DOTs Komplexität durch **explizite Desugarisierung** von Scala-3-Konstrukten in λ-Core (dokumentiert in `docs/scala-map.md`)
- Zeigt, dass selbst ein einfaches Typsystem **industrietaugliche IDE-Unterstützung** (LSP) ermöglicht

### 8.5 Weitere

- **Typed Racket (Tobin-Hochstadt, 2010)**: Gradually typed, optionales HM; Fokus auf Migration ungetypter → getypter Programme
- **Haskell (Peyton Jones, 2003)**: HM + Typeclasses; deutlich mächtiger, aber auch komplexere Inferenz (kein vollständiger Algorithmus W)
- **Elm (Czaplicki, 2012)**: HM + extrem benutzerfreundliche Fehlermeldungen; Inspiration für unsere Fehlerlokalisierungsanalyse

---

## 10. Ausblick / Erweiterungsmöglichkeiten

- Typklassen (Typeclasses) nach Haskell-Vorbild
- Module / Namespaces
- Optimierung (Tail-Call-Eliminierung)
- Web-basiertes Playground (via LSP + Monaco Editor)
- Laufzeit-Datentypen (tags für ADTs)

---

## 11. Dokumentation (`docs/`)

- **`docs/architecture.md`** – Drei Mermaid-Diagramme (Systemübersicht, Data Flow, Dependency Graph) + Paketstruktur
- **`docs/grammar.md`** – Vollständige BNF-Grammatik, Operator-Präzedenztabelle, HM-Typregeln (Lam, App, Let, LetRec, Case, Data)
