# Benchmarks

## Scaling Benchmarks (`ScalingBenchmarks.java`)

Gemessen mit `ScalingBenchmarks.java` (500 Iterationen, 300 Warmup-Runden,
5 Stichproben pro Szenario: n = 10, 50, 100, 500, 1000).

**O-Wachstum** geschätzt aus `t(n₂)/t(n₁)` für die größten beiden n (500 → 1000),
um JIT-Warmup-Effekte zu vermeiden. Werte nahe 1.0 bedeuten O(n),
nahe 2.0 bedeuten O(n²), nahe 0.0 bedeuten O(1).

---

## 1. Arithmetic Chain

`val x0 = 0`, `val x1 = x0 + 1`, …, `val xn = xn-1 + 1`, `xn`

| Size | Tokenize (µs) | Parse (µs) | Typecheck (µs) | Evaluate (µs) |
|------|-------------|-----------|---------------|--------------|
| 10   | 126         | 39        | 237           | 122          |
| 50   | 272         | 56        | 448           | 41           |
| 100  | 247         | 27        | 1,060         | 54           |
| 500  | 270         | 117       | 18,073        | 69           |
| 1000 | 544         | 175       | 90,081        | 167          |

**O-Wachstum (500 → 1000):** tok=1.01, par=0.58, tc=2.32, eval=1.27

- **Tokenizer**: O(n) — jede `val`-Zeile wird unabhängig gescannt (1.01).
- **Parser**: ≈ O(1)–O(√n) — Parse-Zeit wird von JIT-Rauschen dominiert (< 200µs).
- **Typecheck**: ≈ O(n²) (2.32) — erwartet für HM-Typinferenz mit let-Polymorphismus:
  Jede neue Bindung wendet die aktuelle Substitution auf die gesamte Umgebung an
  (O(Umgebung) = O(n) × n Bindungen = O(n²)).
- **Evaluator**: ≈ O(n) (1.27) — jede `val` wird einmal ausgewertet.

---

## 2. ADT Chain

n Case-Ausdrücke gegen `enum MyBool { case MyTrue; case MyFalse }`
(keine Abhängigkeitskette zwischen den Cases).

| Size | Tokenize (µs) | Parse (µs) | Typecheck (µs) | Evaluate (µs) |
|------|-------------|-----------|---------------|--------------|
| 10   | 160         | 41        | 285           | 32           |
| 50   | 345         | 50        | 480           | 20           |
| 100  | 139         | 60        | 560           | 12           |
| 500  | 617         | 207       | 2,660         | 67           |
| 1000 | 1,446       | 475       | 5,458         | 151          |

**O-Wachstum (500 → 1000):** tok=1.23, par=1.20, tc=1.04, eval=1.18

- Alle Phasen skalieren **linear** (≈ O(n)).
- ADTs erzeugen keine Abhängigkeitskette → keine quadratische Typecheck-Zeit.
- Typecheck: Jeder Case wird unabhängig getypt (Konstruktor lookup + Branch-Unifikation).
- Absolute Zeiten niedriger als Arithmetic Chain (keine Umgebungs-Substitution).

---

## 3. Recursive Calls

Einzelnes `def f(n: Int): Int = if (iszero(n)) 0 else f(pred(n))`
mit konstantem Startwert `f(100)`.

| Size | Tokenize (µs) | Parse (µs) | Typecheck (µs) | Evaluate (µs) |
|------|-------------|-----------|---------------|--------------|
| 10   | 15          | 20        | 100           | 51           |
| 50   | 12          | 13        | 62            | 72           |
| 100  | 8           | 4         | 52            | 129          |
| 500  | 5           | 7         | 50            | 68           |
| 1000 | 4           | 5         | 44            | 65           |

**O-Wachstum (500 → 1000):** tok=-0.33, par=-0.32, tc=-0.19, eval=-0.05

- Der generierte Quelltext ist für alle n > 200 identisch (Startwert auf 100 gedeckelt,
  um StackOverflow zu vermeiden). Die Ergebnisse messen **JIT-Konsistenz**,
  nicht algorithmisches Scaling.
- Typecheck ist O(1) — eine einzelne `def`-Deklaration, unabhängig von n.
- Negative Wachstumsrate: JIT-Warmup macht größere n-Durchläufe schneller
  (die nach den kleineren kommen).

---

## 4. Zusammenfassung

| Szenario | Tokenize | Parse | Typecheck | Evaluate |
|----------|----------|-------|-----------|----------|
| Arithmetic Chain | O(n) | O(1)/Rauschen | **O(n²)** | O(n) |
| ADT Chain | O(n) | O(n) | O(n) | O(n) |
| Recursive Calls | O(1) | O(1) | O(1) | O(1) |

**Pipeline-Gesamtskalierung:** Die billigste Operation ist Parsing (< 200µs
selbst bei n=1000). Teuerste ist Typecheck mit O(n²) für tiefe let-Ketten
(~90ms bei 1000 Bindungen), was für den erwarteten Gebrauch
(Studierendencode < 100 Zeilen, < 1ms) völlig ausreichend ist.

---

## Benchmark Suite (`BenchmarkSuite.java`)

Vier Analyse-Module in einem Durchlauf via `gradlew runBenchmarkSuite`:

### 1. PBT-Property-vs-Mutation Matrix

Klassifiziert jeden der 12 Mutationsoperatoren nach Pipeline-Detektionsstufe und
ordnet die passende PBT-Property-Kategorie zu.

| Operator | Parse | Type | RTE | Output | Esc | PBT-Property |
|---|---|---|---|---|---|---|
| ReplaceLiteralType | 0 | 0 | 0 | 35 | 165 | preservationClosed* |
| SwapOperator | 0 | 0 | 0 | 0 | 200 | bigStepMatchesSmallStep |
| SwapIfBranches | 0 | 0 | 0 | 1 | 199 | swapIfBranchesChangesOutput |
| RemoveCaseBranch | 0 | 2 | 0 | 0 | 0 | progressClosedCase |
| SelfReference | 0 | 183 | 0 | 16 | 1 | preservationClosed* |
| UndefinedVariable | 2 | 0 | 0 | 0 | 198 | undefinedVariableCausesError |
| DivideByZero | 0 | 0 | 21 | 0 | 179 | progressClosed* (RTE) |
| BoolNegate | 0 | 50 | 0 | 0 | 150 | preservationClosed* |
| NotOnNumber | 0 | 24 | 0 | 0 | 176 | preservationClosed* |
| UndeclaredConstructor | 0 | 200 | 0 | 0 | 0 | preservationClosed* |
| TypeAnnotationMismatch | 200 | 0 | 0 | 0 | 0 | preservationClosed* |
| RemoveLetBinding | 0 | 11 | 0 | 0 | 189 | removeLetBindingCausesError |

**Detektionskategorien:**
- **Parse** — Fehler vor PBT (Tokenizer/Parser)
- **Type** — preservationClosed* prüft Wohlgetyptheit nach step
- **RTE** — progressClosed* prüft step() wirft nicht für Nicht-Values
- **Output** — bigStepMatchesSmallStep / coverage-guided Output-Properties
- **Escaped** — keine existierende Property erkennt die Mutation

### 2. Scalability (Hand-Crafted Programs)

Laufzeit + Reduktionsschritte für 24 geschlossene Programme (2–32 AST-Knoten):

| Größe | parseNs | tcNs | evalNs | reduceNs | steps | Beispiel |
|---|---|---|---|---|---|---|
| 2 | 1.600 | 1.000 | 2.600 | 200 | 0 | `42` |
| 4 | 22.900 | 9.500 | 12.600 | 41.900 | 1 | `1 + 2` |
| 5 | 24.300 | 11.400 | 3.500 | 8.500 | 1 | `if (true) 1 else 2` |
| 5 | 36.600 | 36.600 | 12.700 | 2.411.000 | 1 | `((x: Int) => x)(5)` (JIT-Warmup) |
| 8 | 20.500 | 11.400 | 6.000 | 16.300 | 3 | `(1 + 2) * (3 + 4)` |
| 12 | 23.600 | 17.700 | 8.200 | 32.400 | 4 | `((x) => x+1)(((y) => y*2)(5))` |
| 16 | 18.700 | 14.100 | 5.300 | 116.600 | 7 | complex arith |
| 19 | 103.400 | 56.800 | 11.400 | 19.600 | 7 | complex if |
| 32 | 34.300 | 98.600 | 10.700 | 44.100 | 15 | deep arith chain |

**Beobachtung:** Typecheck-Zeit wächst mit Programmgröße (O(n) für unabhängige Ausdrücke,
O(n²) für let-Ketten). Reduktionsschritte korrelieren linear mit AST-Tiefe.

### 3. Type Error Recovery

Systematische Error-Injektion für 12 Operatoren × 200 Trials. Gleiche Methodik wie
MutationAnalysis, aber mit Ausgabe der durchschnittlichen Erkennungsrate.

Durchschnittliche Erkennung: **39,6%** (vergleichbar mit MutationAnalysis).

### 4. AST Node Coverage

Abdeckungsmatrix: Welche Komponente handhabt welchen AST-Knotentyp (per `instanceof`/`new`-Scan):

| Node Type | Eval | Reducer | Checker | Parser |
|---|---|---|---|---|
| LiteralNode | Y | Y | Y | Y |
| BoolNode | Y | Y | Y | Y |
| IdentifierNode | Y | Y | Y | Y |
| LambdaNode | Y | Y | Y | Y |
| ApplicationNode | Y | Y | Y | Y |
| IfNode | Y | Y | Y | Y |
| BinaryExprNode | Y | Y | Y | Y |
| FixpointNode | N | Y | Y | N |
| CaseNode | Y | Y | Y | Y |
| BlockNode | Y | Y | Y | Y |
| LetStatementNode | Y | Y | Y | Y |
| EnumDeclNode | Y | N | Y | Y |
| TypeAliasNode | Y | N | Y | Y |
| ProgramNode | N | N | Y | Y |

**Lücken:**
- `FixpointNode` nur in Reducer/Checker (in Evaluator durch Fixpoint-Semantik inline ersetzt)
- `EnumDeclNode`/`TypeAliasNode` fehlen im Reducer (werden beim TypeCheck verarbeitet, nicht zur Laufzeit)
- `ProgramNode` nur in Checker/Parser (Evaluator hat separate `evalProgram`-Methode ohne `instanceof`)

Aufruf: `gradlew runBenchmarkSuite` (alle 4) oder `gradlew runBenchmarkSuite --args="scalability"` (einzeln).
