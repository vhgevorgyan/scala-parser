# Scaling Benchmarks

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
