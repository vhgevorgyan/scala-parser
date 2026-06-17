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

Die Sprache ist ein rein funktionaler Kern, angelehnt an Haskell/ML, jedoch mit minimaler Syntax:

```
-- Primitive Typen: Number, Bool, String
let x = 42

-- Lambda:
\x -> x + 1

-- Applikation:
f x

-- Boolsche Operatoren: &&, ||, !
-- Vergleiche: ==, !=, <, >, <=, >=
-- Arithmetik: +, -, *, /

-- Fallunterscheidung:
if x > 0 then x else -x

-- Rekursion:
let rec fact = \n ->
  if n == 0 then 1
  else n * fact (n - 1)

-- Algebraische Datentypen:
data Maybe a = Nothing | Just a

-- Pattern Matching:
case x of
  Nothing -> 0
  | Just y -> y

-- Typannotation:
let id : a -> a = \x -> x
```

**Besonderheiten:**
- Keine Seiteneffekte (rein funktional)
- Polymorphe let-Bindungen (let-Polymorphismus nach Hindley-Milner)
- Currying (alle Funktionen sind einstellig)
- Pattern Matching mit Konstruktoren

---

## 3. Kernthemen

### 3.1 Lexer (Tokenisierung)

Zeichenweises Scannen mit fehlertoleranter Behandlung:
- Erkennung von Keywords, Identifiern, Zahlen, Strings, Operatoren
- Kommentare `//` und `/* */`
- Unbekannte Zeichen werden übersprungen (kein Abbruch)

### 3.2 Parser (syntaktische Analyse)

Recursive-Descent-Parser mit Operator-Präzedenz (14 Stufen):
- Aufbau eines AST (Abstract Syntax Tree)
- Fehlerrecovery (Synchronisation bei Syntaxfehlern)
- Lookahead für `let rec` vs `let`
- Parsen von Typannotationen

### 3.3 Typinferenz (Hindley-Milner / Algorithmus W)

Herzstück der Arbeit:
- **Unifikation** mit Occurs-Check
- **let-Polymorphismus**: `generalize` → `instantiate`
- **Type Schemes**: `forall a. a -> a`
- **ADT-Constructor-Registrierung** bei data-Deklarationen
- **Exhaustiveness-Prüfung** für Pattern Matching
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
| | Weakening | Unused Let ändert Typability nicht |
| **Round-Trip** | Parse Round-Trip | AST → Source → parse → identisch |
| | Fuzz (5000×) | Wie oben, massiv mehr Iterationen |
| **Fehlererkennung** | notOnNonBool | `!` auf Number → Fehler erwähnt Bool |
| | ifCondMustBeBool | If mit Number → Fehler erwähnt Bool |
| | noDirectSelfRecursion | `let rec f = f` wird abgefangen |
| | undeclaredVariable | Unbekannte Variable → "Undefined" |
| | exhaustivenessCheck | Fehlende Konstruktoren → "missing constructor" |
| **Error Quality** | typeErrorMessagesAreInformative | Fehler >5 Zeichen, nicht leer |
| | **typeErrorLocalization** | Mutation: Typfehler zeigt auf die mutierte Stelle (1000×) |
| **Recovery** | **errorRecoveryQuality** | Mutation: Cascade-Messung der Fehlerrecovery (500×) |

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

## 8. Ausblick / Erweiterungsmöglichkeiten

- Typklassen (Typeclasses) nach Haskell-Vorbild
- Module / Namespaces
- Laufzeit-Datentypen (tags für ADTs)
- Optimierung (Tail-Call-Eliminierung)
- Web-basiertes Playground (via LSP + Monaco Editor)
