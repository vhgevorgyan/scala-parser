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

Acht Analyse-Module in einem Durchlauf via `gradlew runBenchmarkSuite`:

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

Laufzeit + Reduktionsschritte für 29 geschlossene Programme (2–32 AST-Knoten):

| Größe | parseNs | tcNs | evalNs | reduceNs | steps | Beispiel |
|---|---|---|---|---|---|---|
| 2 | 8.800 | 4.000 | 2.500 | 1.100 | 0 | `42` |
| 4 | 19.700 | 17.500 | 92.700 | 51.100 | 1 | `succ(5)` |
| 5 | 20.600 | 11.300 | 16.900 | 4.900 | 1 | `if (false) 1 else 2` |
| 8 | 38.900 | 31.400 | 15.100 | 41.200 | 3 | `(1 + 2) * (3 + 4)` |
| 12 | 31.600 | 26.900 | 14.000 | 25.800 | 4 | nested if |
| 16 | 62.700 | 54.300 | 26.000 | 339.600 | 7 | complex arith |
| 17 | 581.000 | 115.200 | 45.100 | 79.200 | 6 | nested beta deep |
| 19 | 56.300 | 175.800 | 33.900 | 54.100 | 7 | complex if |
| 32 | 248.700 | 314.600 | 43.100 | 143.200 | 15 | deep arith chain |
| *block* | ~20–45k | ~56–107k | ~10–20k | ~10–63k | 1–3 | block with val/if/beta |

**Beobachtung:** Typecheck-Zeit wächst mit Programmgröße (O(n) für unabhängige Ausdrücke,
O(n²) für let-Ketten). Reduktionsschritte korrelieren linear mit AST-Tiefe.
5 Block-Programme (Größe 3–8) zeigen ähnliches Verhalten mit Schritten 1–3.

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

Aufruf: `gradlew runBenchmarkSuite` (alle 8) oder `gradlew runBenchmarkSuite --args="scalability steps"` (einzeln).

### 5. Reduction Step Distribution

Counts reduction steps via SmallStepReducer for 200 random closed well-typed expressions.
Rejection-sampled: must be well-typed and evaluate without error.

| Steps | Count |
|-------|-------|
| 0     | 175   |
| 1     | 18    |
| 2     | 3     |
| 3     | 2     |
| 4     | 2     |

**Average steps: 0.19** — 87.5% of random expressions are already values (literals, bools, lambdas).
Only 12.5% require ≥1 reduction step. This confirms that random expression generation heavily skews toward trivial (atomic) values.

### 6. Parser Error Recovery Quality

Injects single-character mutations into well-typed programs and measures **cascade rate**:
how many bogus follow-up errors result from one injection. 4 injectors × 50 trials.

| Injector | Total | Cascade | MaxCasc | ParseErr | CascadeRate |
|---|---|---|---|---|---|
| delete-char | 50 | 12 | 2 | 23 | 24.0% |
| insert-! | 50 | 38 | 5 | 31 | 76.0% |
| replace→# | 50 | 10 | 4 | 19 | 20.0% |
| delete-digit | 42 | 14 | 4 | 27 | 33.3% |

**Cascade rates 20–76%.** `insert-!` causes the most cascade (76%) because `!` in prefix position
introduces unexpected unary-not expressions, confusing the recursive-descent parser.
`replace→#` shows best recovery (20%), since `#` is a syntax error that localizes well.

### 7. Empirical PBT-vs-Mutation Matrix

For each expression-level mutation operator, runs actual PBT property checks against 100 mutated
closed well-typed expressions. Properties tested: progress, preservation, determinism,
bigStepMatchesSmallStep, valueIsIrreducible.

| Operator | Progress | Preserv | Determ | BigStep | ValueIrred | VsOrig | AnyPBT |
|---|---|---|---|---|---|---|---|---|
| ReplaceLiteralType | 33 | 33 | 33 | 33 | 0 | 67 | 100 |
| SwapOperator | 0 | 0 | 0 | 0 | 0 | 100 | 100 |
| SwapIfBranches | 0 | 0 | 0 | 0 | 0 | 100 | 100 |
| SelfReference | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| BoolNegate | 100 | 100 | 100 | 100 | 0 | 0 | 100 |
| NotOnNumber | 100 | 100 | 100 | 100 | 0 | 0 | 100 |
| DivideByZero | 87 | 87 | 87 | 87 | 0 | 0 | 87 |

**Key findings:**
- **VsOrig (Original-vs-Mutated output comparison) ist die mächtigste Property** — schließt die Lücken für SwapOperator (0% → 100%) und SwapIfBranches (0% → 100%).
- **PBT catches type errors** (BoolNegate: `-true` type error, NotOnNumber: `!42` type error) und **runtime errors** (DivideByZero: RTE on `x/0`).
- **VsOrig allein reicht nicht** für BoolNegate/NotOnNumber/DivideByZero — diese erzeugen Parse/Type/RTE-Fehler, keinen Output. Soundness-Properties fangen sie.
- **SelfReference** bleibt bei 0% (single expression): `let f = f` ändert Output nicht, da kein freies `f` existiert.

### CLI Options

`gradlew runBenchmarkSuite` unterstützt:
- `--runs N` — Multipliziert Trial-Anzahl (z.B. `empirical --runs 5` → 500 Trials)
- `--csv` — Maschinenlesbare CSV-Ausgabe (prefix `#` für Kommentare)
- Kombination: `scalability breakdown --runs 3 --csv` für reproduzierbare Rohdaten

### 8. Runtime Breakdown

Measures pipeline stage times (ns) for 97 random well-typed programs (rejection-sampled from 2000 attempts).

| Metric | Tokenize | Parse | TypeCheck | Evaluate | Reduce |
|---|---|---|---|---|---|
| Min (ns) | 8,400 | 2,700 | 1,500 | 500 | 500 |
| Max (ns) | 285,000 | 245,600 | 46,600 | 46,600 | 11,000 |
| Typical | 15–30k | 10–20k | 2–5k | 2–5k | 500–2k |

**Pipeline bottleneck:** `TypeCheck` dominates for complex programs (up to 245µs at size 11–17).
**Reduction** is consistently fast (< 2µs typical) because most expressions are already values (0 steps).
Output: CSV with columns `size,parseNs,tcNs,evalNs,reduceNs` for external analysis.

---

### 9. Guard & As-Pattern Generation (PBT)

`genEnumSource` in `SmallStepPBT.java` now generates match expressions with:
- **As-patterns** (`20%`): `case x @ Ctor(y) => ...` — binds the matched value to `x`
- **Guards** (`25%` on arg-bearing branches): `case Ctor(x) if x > N => ...` — condition must hold
- Generated via `Parser` (string → AST), then type-checked; used by `closedCase`/`caseSource` providers (200 tries each)
- All CaseBranch records now carry `asName` and `guard` fields (nullable)

### 10. Fuzz Testing

`FuzzPBT.java` (5000 tokenizer + 2000 parser + 500 pipeline tries):
- Random byte sequences (1–200 bytes) exercise tokenizer and parser error recovery
- Printable ASCII strings (1–100 chars) exercise full pipeline (tokenize → parse → type-check)
- Verifies no crashes: all parsing/type-checking failures must be reported as errors, not exceptions
