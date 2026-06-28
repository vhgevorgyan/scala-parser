# Lambda Core — Formale Definition

## 1. Aufbau

Die Sprache wird Bottom-up definiert: beginnend mit dem reinen, ungetypten λ-Kalkül,
wird schrittweise jede Erweiterung mit Syntax, operationaler Semantik (Small-Step)
und Typregeln spezifiziert.

### Notation

```
e → e′    Small-Step-Reduktion (ein Schritt)
e →* e′   reflexiv-transitive Hülle
e ⇓ v     Big-Step-Auswertung (Evaluator)
Γ ⊢ e : τ  Typableitung
e :: τ    Typannotation im Quelltext
[x ↦ v]e  Substitution: ersetze x durch v in e
FV(e)     freie Variablen von e
```

---

## 2. Basis: Unveränderlicher λ-Kalkül

### Syntax

```
e  ::=  x                     (Variable)
      |  λx. e               (Abstraktion)
      |  e₁ e₂                (Applikation)

v  ::=  λx. e                (Wert: geschlossene Abstraktion)
```

### Operationale Semantik (Small-Step)

```
Beta-Reduktion:
(λx. e₁) e₂  →  [x ↦ e₂] e₁

App-Links:
e₁ → e₁′
───────────
e₁ e₂ → e₁′ e₂

App-Rechts:
e₂ → e₂′
──────────────
v e₂ → v e₂′
```

### Substitution

```
[x ↦ s]x       = s
[x ↦ s]y       = y                    (x ≠ y)
[x ↦ s](λy. e) = λy. [x ↦ s]e        (y ∉ FV(s), y ≠ x)
[x ↦ s](e₁ e₂) = ([x ↦ s]e₁) ([x ↦ s]e₂)
```

---

## 3. Erweiterung: Let-Bindung

### Syntax

```
e  ::=  ... aus Basis
      |  let x = e₁ in e₂
      |  let rec x = λy. e₁ in e₂
```

### Operationale Semantik

```
Let:
let x = v in e  →  [x ↦ v] e

Let-Links:
e₁ → e₁′
────────────────────
let x = e₁ in e₂  →  let x = e₁′ in e₂

Let-Rec:
let rec x = λy. e₁ in e₂  →  let x = fix (λx. λy. e₁) in e₂

Fix-Punkt-Semantik:
fix (λx. e)  →  [x ↦ fix (λx. e)] e
```

### Typregel (HM)

```
(Var)    Γ, x : σ ⊢ x : σ               (Instantiate σ)

(Abs)    Γ, x : τ₁ ⊢ e : τ₂
         ────────────────────
         Γ ⊢ λx. e : τ₁ → τ₂

(App)    Γ ⊢ e₁ : τ₁ → τ₂    Γ ⊢ e₂ : τ₁
         ────────────────────────────────
         Γ ⊢ e₁ e₂ : τ₂

(Let)    Γ ⊢ e₁ : σ    Γ, x : σ ⊢ e₂ : τ
         ────────────────────────────────
         Γ ⊢ let x = e₁ in e₂ : τ

(LetRec) Γ, x : τ₁ → τ₂ ⊢ λy. e₁ : τ₁ → τ₂    Γ, x : τ₁ → τ₂ ⊢ e₂ : τ
         ───────────────────────────────────────────────────────────────
         Γ ⊢ let rec x = λy. e₁ in e₂ : τ

(Gen)    Γ ⊢ e : τ    α ∉ FV(Γ)
         ───────────────────────
         Γ ⊢ e : ∀α. τ

(Inst)   Γ ⊢ e : ∀α. τ
         ──────────────
         Γ ⊢ e : τ[α ↦ τ′]
```

---

## 4. Erweiterung: If-Ausdruck + Unit

### Syntax

```
e  ::=  ... aus Let
      |  if e₁ then e₂ else e₃
      |  if e₁ then e₂                 (without else → Unit)
      |  ()                             (Unit literal)

v  ::=  ... aus Basis
      |  ()                             (Unit-Wert)

τ  ::=  ... aus HM
      |  Unit
```

### Operationale Semantik

```
IfTrue:
if true then e₂ else e₃  →  e₂

IfFalse:
if false then e₂ else e₃  →  e₃

If-Cond:
e₁ → e₁′
─────────────────────────────────────
if e₁ then e₂ else e₃  →  if e₁′ then e₂ else e₃

If-NoElse-True:
if true then e₂  →  e₂

If-NoElse-False:
if false then e₂  →  ()
```

### Typregeln

```
(If)   Γ ⊢ e₁ : Bool    Γ ⊢ e₂ : τ    Γ ⊢ e₃ : τ
         ──────────────────────────────────────────
         Γ ⊢ if e₁ then e₂ else e₃ : τ

(If-Unit) Γ ⊢ e₁ : Bool    Γ ⊢ e₂ : τ
           ──────────────────────────
           Γ ⊢ if e₁ then e₂ : τ

(Unit)   Γ ⊢ () : Unit
```

---

## 5. Erweiterung: ADTs (Enums) + Pattern Matching

### Syntax

```
data C[Ā] = K₁(τ₁̄) | ... | Kₙ(τₙ̄)

e  ::=  ... aus If
      |  K(e₁, ..., eₙ)            (Constructor-Application)
      |  e₁ match { case P ⇒ e₂ }*   (Pattern Match)
      |  K                          (Constructor-Referenz)

P  ::=  K(x₁, ..., xₙ)            (Constructor-Pattern)
      |  n | b                     (Literal-Pattern)
      |  _                         (Wildcard)
      |  x                         (Variable-Binding)

v  ::=  ... aus If
      |  K(v₁, ..., vₙ)            (Constructor-Wert)

τ  ::=  ... aus HM
      |  C τ̄                        (Typkonstruktor)
```

### Operationale Semantik

```
Constructor-App:
eᵢ → eᵢ′
──────────────────────────────────
K(v₁, ..., eᵢ, ..., eₙ) → K(v₁, ..., eᵢ′, ..., eₙ)

Match-Constructor:
K(v₁, ..., vₙ) match { case K(x₁, ..., xₙ) ⇒ e; rest }
    →  [x₁ ↦ v₁, ..., xₙ ↦ vₙ] e

Match-Wildcard:
v match { case _ ⇒ e; rest }  →  e

Match-Literal:
n match { case n ⇒ e; rest }  →  e         (Literal gleich)
n match { case m ⇒ e; rest }  →  n match rest   (Literal ungleich)

Match-Scrutinee:
e₁ → e₁′
──────────────────────────────────────
e₁ match { case P ⇒ e }*  →  e₁′ match { case P ⇒ e }*
```

### Typregeln

```
(Ctor)   data C[Ā] = K(τ₁̄, ..., τₙ̄) in Γ
         ──────────────────────────────────
         Γ ⊢ K : ∀ᾱ. τ₁̄ → ... → τₙ̄ → C ᾱ

(Match)  Γ ⊢ e : C τ̄    Γ, xᵢ : [ᾱ ↦ τ̄]τᵢ ⊢ eᵢ : τ    (∀i)
         ────────────────────────────────────────────────
         Γ ⊢ e match { case Kᵢ(x̄ᵢ) ⇒ eᵢ } : τ

(Wild)   Γ ⊢ _    Γ ⊢ e : τ
         ────────────────────
         Γ ⊢ case _ ⇒ e : τ

(Scrut)  e → e′    e, e′ geschlossen
         ──────────────────────────
         match-Kontext
```

---

## 6. Erweiterung: Literale + Operatoren

### Syntax

```
e  ::=  ... aus ADTs
      |  n                     (Number-Literal)
      |  b                     (Boolean-Literal)
      |  s                     (String-Literal)
      |  e₁ ⊕ e₂               (Binäroperator: +, -, *, /, ==, ...)
      |  ⊖ e                   (Unäroperator: !, -)

v  ::=  ... aus ADTs
      |  n                     (Number)
      |  b                     (Boolean)
      |  s                     (String)

τ  ::=  ... aus ADTs
      |  Int | Boolean | String
```

### Operationale Semantik

```
BinOp-Werte:
v₁ ⊕ v₂  →  δ(⊕, v₁, v₂)          (δ = primitive Operation)

BinOp-Links:
e₁ → e₁′
───────────────────
e₁ ⊕ e₂ → e₁′ ⊕ e₂

BinOp-Rechts:
e₂ → e₂′
───────────────────
v₁ ⊕ e₂ → v₁ ⊕ e₂′

UnaryOp:
⊖ v  →  δ(⊖, v)

UnaryOp-Expr:
e → e′
─────────
⊖ e → ⊖ e′
```

### Typregeln

```
(Lit-Int)    Γ ⊢ n : Int

(Lit-Bool)   Γ ⊢ b : Bool

(Lit-Str)    Γ ⊢ s : String

(BinOp)   Γ ⊢ e₁ : τ    Γ ⊢ e₂ : τ    τ ∈ {Int, Bool, String}
          ────────────────────────────────────────────────────
          Γ ⊢ e₁ ⊕ e₂ : τ′

           τ′ = Int  für +, -, *, /
           τ′ = Bool für ==, !=, <, >, <=, >=, &&, ||

(UnaryOp)  Γ ⊢ e : τ
           ───────────
           Γ ⊢ ⊖ e : τ          τ = Bool für !, τ = Int für -
```

---

## 7. Erweiterung: Paare / Tupel

### Syntax

```
e  ::=  ... aus Operatoren
      |  (e₁, ..., eₙ)

v  ::=  ... aus Operatoren
      |  (v₁, ..., vₙ)

τ  ::=  ... aus Operatoren
      |  (τ₁, ..., τₙ)
```

### Operationale Semantik

```
Tuple:
eᵢ → eᵢ′
─────────────────────────────────────────────
(v₁, ..., eᵢ, ..., eₙ) → (v₁, ..., eᵢ′, ..., eₙ)
```

### Typregeln

```
(Tuple)  Γ ⊢ eᵢ : τᵢ    (∀i)
         ─────────────────────
         Γ ⊢ (e₁, ..., eₙ) : (τ₁, ..., τₙ)
```

---

## 8. Erweiterung: Type Ascription + Type Alias

### Syntax

```
e  ::=  ... aus Tupel
      |  (e : τ)                     (Type Ascription)

d  ::=  type X = τ                   (Type Alias)
```

### Operationale Semantik

```
Ascription:
(e : τ)  →  e
```

### Typregeln

```
(Ascribe)  Γ ⊢ e : τ′    τ′ = τ (nach Unifikation)
           ─────────────────────────────
           Γ ⊢ (e : τ) : τ

(Alias)    type X = τ in Γ
           ────────────────
           Γ ⊢ X : τ            (τ instantiiert)
```

---

## 9. Erweiterung: Literal-Listen

### Syntaktischer Zucker

```
[e₁, e₂, ..., eₙ]   ≜   Cons(e₁, Cons(e₂, ... Cons(eₙ, Nil)))
[]                  ≜   Nil
```

### Typen

```
Nil   : ∀a. List[a]
Cons  : ∀a. a → List[a] → List[a]
```

---

## 10. Erweiterung: Infix-Listenkonstruktor

### Pattern-Syntax

```
P  ::=  P₁ :: P₂                    (Infix-Cons-Pattern)
      |  K(x̄) | _ | x | n | b       (wie in 5.)

Semantik:  P₁ :: P₂  ist Zucker für  ::(P₁, P₂)
```

---

## 11. Big-Step-Semantik (Evaluator)

Die Big-Step-Semantik formalisiert den baumwandelnden Evaluator als
Ableitungsrelation \( e \Downarrow v \) („e wertet zu v aus").

### Basis (reiner λ-Kalkül)

```
(Lam)    λx. e  ⇓  λx. e

(App)    e₁ ⇓ λx. e₀    e₂ ⇓ v₂    [x ↦ v₂]e₀ ⇓ v
         ──────────────────────────────────────────
         e₁ e₂ ⇓ v
```

### Let-Bindung

### Block-Notation (Blocks)

Block-Ausdrücke fassen mehrere Statements zu einem Ausdruck zusammen:

```
e  ::=  ... aus Basis
      |  { s₁; ...; sₙ }          (Block)

s  ::=  let x = e                  (Let-Statement)
      |  e                         (Expression-Statement)
```

### Operationale Semantik (Small-Step)

```
Block-Reduktion – Let-Value:
{x = v; s₂; ...; sₙ}  →  {[x ↦ v]s₂; ...; [x ↦ v]sₙ}

Block-Reduktion – Let-Step:
e₁ → e₁′
────────────────────────────────────────────
{let x = e₁; s₂; ...; sₙ}  →  {let x = e₁′; s₂; ...; sₙ}

Block-Reduktion – Expr:
e → e′
────────────────────────────────
{e; s₂; ...; sₙ}  →  {e′; s₂; ...; sₙ}

Block-Extrakt:
{v}  →  v                          (letztes Statement ist Wert → unwrap)

Block-Skip:
{let x = v; e; s₃; ...; sₙ}  →  {e; s₃; ...; sₙ}
(Wenn ein Let-Statement einen Wert hat, wird der Name substituiert
und das Statement entfernt. Die Rekursionssemantik für isRec=true
wird im Evaluator mit fixpunktähnlicher Closure-Umgebung realisiert.)
```

### Mutual Recursion

Mehrere adjazente `def`-Deklarationen im selben Block werden zu einer
Mutual-Recursion-Gruppe zusammengefasst. Die Names sind gegenseitig
im jeweils anderen Rumpf sichtbar.

```
MutualRecap:
{def x₁ = λy. e₁; def x₂ = λy. e₂; ...; e}  →
  {[x₁ ↦ λy. e₁, x₂ ↦ λy. e₂, ...]e}
```

(Die Typprüfung und Auswertung setzen alle Namen gleichzeitig in den
jeweiligen Environments der Closure-Werte.)

---

```
(Let)    e₁ ⇓ v₁    [x ↦ v₁]e₂ ⇓ v₂
         ────────────────────────────
         let x = e₁ in e₂ ⇓ v₂

(LetRec) λx. e₁ ⇓ λx. e₁    [x ↦ fix (λx. λx. e₁)]e₂ ⇓ v₂
         ────────────────────────────────────────────────────
         let rec x = λy. e₁ in e₂ ⇓ v₂
```

### If + Unit

```
(IfTrue)     e₁ ⇓ true    e₂ ⇓ v
            ──────────────────────
            if e₁ then e₂ else e₃ ⇓ v

(IfFalse)    e₁ ⇓ false    e₃ ⇓ v
            ────────────────────────
            if e₁ then e₂ else e₃ ⇓ v

(IfUnitTrue) e₁ ⇓ true    e₂ ⇓ v
            ────────────────────
            if e₁ then e₂ ⇓ v

(IfUnitFalse) e₁ ⇓ false
            ─────────────
            if e₁ then e₂ ⇓ ()
```

### ADTs + Pattern Matching

```
(Ctor)   eᵢ ⇓ vᵢ    (∀i)
         ──────────────────────────
         K(e₁, ..., eₙ) ⇓ K(v₁, ..., vₙ)

(MatchCtor)  e ⇓ K(v₁, ..., vₙ)    [x̄ ↦ v̄]e₀ ⇓ v
            ────────────────────────────────────────
            e match { case K(x̄) ⇒ e₀; rest } ⇓ v

(MatchWild)  e ⇓ v₀    e₀ ⇓ v
            ────────────────────────
            e match { case _ ⇒ e₀; rest } ⇓ v

(MatchLit)   e ⇓ n    e₀ ⇓ v
            ──────────────────────
            e match { case n ⇒ e₀; rest } ⇓ v

(MatchSkip)  e ⇓ n    n ≠ m
            ───────────────────────────────
            e match { case m ⇒ e₀; rest } ⇓ v
            wobei e match rest ⇓ v
```

### Literale + Operatoren

```
(LitNum)    n ⇓ n

(LitBool)   b ⇓ b

(LitStr)    s ⇓ s

(BinOp)     e₁ ⇓ v₁    e₂ ⇓ v₂    v = δ(⊕, v₁, v₂)
            ────────────────────────────────────────
            e₁ ⊕ e₂ ⇓ v

(UnaryOp)   e ⇓ v₀    v = δ(⊖, v₀)
            ────────────────────────
            ⊖ e ⇓ v
```

### Tupel

```
(Tuple)     eᵢ ⇓ vᵢ    (∀i)
            ─────────────────────
            (e₁, ..., eₙ) ⇓ (v₁, ..., vₙ)
```

### Ascription

```
(Ascribe)   e ⇓ v
            ───────────
            (e : τ) ⇓ v
```

### Block + Mutual Recursion

```
(BlockLet)  e₁ ⇓ v₁    [x ↦ v₁]{s₂; ...; sₙ} ⇓ v
            ────────────────────────────────────────
            {val x = e₁; s₂; ...; sₙ} ⇓ v

(BlockExpr) e ⇓ v
            ───────
            {e} ⇓ v

(MutualLet) e₁ ⇓ λy. e₀₁    e₂ ⇓ λz. e₀₂
            {[x₁ ↦ e₁, x₂ ↦ e₂, ...]e} ⇓ v
            ─────────────────────────────────
            {def x₁ = λy. e₀₁; def x₂ = λz. e₀₂; ...; e} ⇓ v
            (alle Namen in den Closure-Environments gesetzt)
```

---

## 12. Type Soundness — Formale Beweise

### 12.1 Substitutionslemma

**Lemma 1 (Substitution).**
Wenn \( \Gamma, x : \tau' \vdash e : \tau \) und \( \Gamma \vdash v : \tau' \),
dann \( \Gamma \vdash [x \mapsto v]e : \tau \).

*Beweis.* Strukturelle Induktion über \( e \).

- **Fall \( e = x \):**
  Dann \( [x \mapsto v]x = v \).
  Aus \( \Gamma, x : \tau' \vdash x : \tau \) folgt \( \tau = \tau' \) (Regel Var).
  Also \( \Gamma \vdash v : \tau' = \tau \). ✓

- **Fall \( e = y \) mit \( y \neq x \):**
  Dann \( [x \mapsto v]y = y \).
  Aus \( \Gamma, x : \tau' \vdash y : \tau \) folgt \( \Gamma \vdash y : \tau \) (da \( y \neq x \)). ✓

- **Fall \( e = \lambda y. e_0 \):**
  Nach Regel Abs: \( \tau = \tau_1 \to \tau_2 \) mit
  \( \Gamma, x : \tau', y : \tau_1 \vdash e_0 : \tau_2 \).
  Per BFN: \( y \neq x \), \( y \notin \text{FV}(v) \).
  Substitution: \( [x \mapsto v](\lambda y. e_0) = \lambda y. [x \mapsto v]e_0 \).
  Nach IV: \( \Gamma, y : \tau_1 \vdash [x \mapsto v]e_0 : \tau_2 \).
  Mit Regel Abs: \( \Gamma \vdash \lambda y. [x \mapsto v]e_0 : \tau_1 \to \tau_2 \). ✓

- **Fall \( e = e_1 e_2 \):**
  Nach Regel App: \( \Gamma, x : \tau' \vdash e_1 : \tau_2 \to \tau \),
  \( \Gamma, x : \tau' \vdash e_2 : \tau_2 \).
  Nach IV: \( \Gamma \vdash [x \mapsto v]e_1 : \tau_2 \to \tau \),
  \( \Gamma \vdash [x \mapsto v]e_2 : \tau_2 \).
  Mit App: \( \Gamma \vdash ([x \mapsto v]e_1)([x \mapsto v]e_2) : \tau \). ✓

- **Fall \( e = \text{if } e_1 \text{ then } e_2 \text{ else } e_3 \):**
  Nach Regel If: \( \Gamma, x : \tau' \vdash e_1 : \text{Bool} \),
  \( \Gamma, x : \tau' \vdash e_2 : \tau \), \( \Gamma, x : \tau' \vdash e_3 : \tau \).
  Nach IV: \( \Gamma \vdash [x \mapsto v]e_1 : \text{Bool} \),
  \( \Gamma \vdash [x \mapsto v]e_2 : \tau \), \( \Gamma \vdash [x \mapsto v]e_3 : \tau \).
  Mit If: \( \Gamma \vdash \text{if } [x \mapsto v]e_1 \text{ then } [x \mapsto v]e_2 \text{ else } [x \mapsto v]e_3 : \tau \). ✓

- **Fall \( e = K(e_1, ..., e_n) \):**
  Nach Regel Ctor: \( \Gamma, x : \tau' \vdash e_i : \tau_i \) mit
  \( K : \forall\bar\alpha. \bar\tau \to C\bar\alpha \).
  Nach IV: \( \Gamma \vdash [x \mapsto v]e_i : \tau_i \).
  Mit Ctor: \( \Gamma \vdash K([x \mapsto v]e_1, ..., [x \mapsto v]e_n) : C\bar\tau \). ✓

- **Fall \( e = e_0 \text{ match } \{\text{case } K(\bar y) \Rightarrow e_K; ...\} \):**
  Nach Regel Match: \( \Gamma, x : \tau' \vdash e_0 : C\bar\tau \),
  und für jeden Zweig \( \Gamma, x : \tau', \bar y : \bar\tau_i \vdash e_K : \tau \).
  Nach IV: \( \Gamma \vdash [x \mapsto v]e_0 : C\bar\tau \),
  \( \Gamma, \bar y : \bar\tau_i \vdash [x \mapsto v]e_K : \tau \).
  Mit Match: \( \Gamma \vdash [x \mapsto v]e_0 \text{ match } \{\text{case } K(\bar y) \Rightarrow [x \mapsto v]e_K\} : \tau \). ✓

- **Fälle \( e = n, b, s, () \):** Keine Variable, Substitution trivial. ✓

- **Fall \( e = e_1 \oplus e_2 \):**
  Nach Regel BinOp: \( \Gamma, x : \tau' \vdash e_i : \tau_i \).
  IV analog App. ✓

- **Fall \( e = \ominus e_0 \):**
  Nach Regel UnaryOp analog. ✓

- **Fall \( e = (e_1, ..., e_n) \):**
  Nach Regel Tuple analog. ✓

- **Fall \( e = (e_0 : \tau) \):**
  Nach Regel Ascribe analog. ✓

### 12.2 Kanonische-Formen-Lemma

**Lemma 2 (Kanonische Formen).**
Sei \( v \) ein Wert mit \( \Gamma \vdash v : \tau \). Dann:

1. Ist \( \tau = \tau_1 \to \tau_2 \), so ist \( v = \lambda x. e \) für ein \( e \).
2. Ist \( \tau = \text{Int} \), so ist \( v = n \) für ein \( n \in \mathbb Z \).
3. Ist \( \tau = \text{Bool} \), so ist \( v = b \) für \( b \in \{\text{true}, \text{false}\} \).
4. Ist \( \tau = \text{String} \), so ist \( v = s \).
5. Ist \( \tau = \text{Unit} \), so ist \( v = () \).
6. Ist \( \tau = C\bar\tau \) (Typkonstruktor), so ist \( v = K(v_1, ..., v_n) \)
   für einen Konstruktor \( K \) von \( C \).
7. Ist \( \tau = (\tau_1, ..., \tau_n) \), so ist \( v = (v_1, ..., v_n) \).

*Beweis.* Durch Inspektion der Werte-Definition und der Typregeln. Jeder Wert hat genau eine syntaktische Form:
- Lambda: einziger Wert mit Pfeiltyp
- Zahlenliteral: einziger Wert mit Int
- Boolesches Literal: einziger Wert mit Bool
- Stringliteral: einziger Wert mit String
- Unit-Literal: einziger Wert mit Unit
- Constructor-Wert: einziger Wert mit Typkonstruktor C
- Tupel: einziger Wert mit Tupeltyp
Die Typregeln ordnen jeder syntaktischen Form eindeutig einen
Typ zu. Somit gilt die umgekehrte Richtung: aus dem Typ folgt
die syntaktische Form des Werts. ✓

### 12.3 Progress-Theorem

**Theorem 3 (Progress).**
Wenn \( \Gamma \vdash e : \tau \) und \( e \) ist kein Wert,
dann existiert \( e' \) mit \( e \to e' \).

*Beweis.* Strukturelle Induktion über die Typableitung \( \Gamma \vdash e : \tau \).
Wir betrachten den letzten Schritt der Ableitung (die äußerste Regel).

- **Fall (Var):** \( e = x \). Variablen können keine geschlossenen Terme sein;
  in einem geschlossenen Kontext (\( \Gamma = \emptyset \)) ist dieser Fall unmöglich.
  In offenen Kontexten ist \( x \) per Definition kein Wert und auch keine Redex —
  der Term ist „stuck". Für geschlossene Terme gilt: \( \Gamma = \emptyset \)
  liefert keine Var-Ableitung für freie Variablen. ✓ (trivial, da nicht erreichbar)

- **Fall (Abs):** \( e = \lambda x. e_0 \). Ein Lambda ist immer ein Wert.
  Kein Schritt nötig. ✓

- **Fall (App):** \( e = e_1 e_2 \).
  - Ist \( e_1 \) kein Wert: Nach IV existiert \( e_1' \) mit \( e_1 \to e_1' \),
    also \( e_1 e_2 \to e_1' e_2 \) (App-Links). ✓
  - Ist \( e_1 \) ein Wert, \( e_2 \) kein Wert: Nach IV \( e_2 \to e_2' \),
    also \( e_1 e_2 \to e_1 e_2' \) (App-Rechts). ✓
  - Sind \( e_1, e_2 \) Werte: Nach Lemma 2 ist \( e_1 = \lambda x. e_0 \).
    Also Beta-Reduktion: \( (\lambda x. e_0) e_2 \to [x \mapsto e_2] e_0 \). ✓

- **Fall (If):** \( e = \text{if } e_1 \text{ then } e_2 \text{ else } e_3 \).
  - \( e_1 \) kein Wert: Nach IV \( e_1 \to e_1' \), also If-Cond. ✓
  - \( e_1 \) Wert: Nach Lemma 2 ist \( e_1 \) ein Bool \( b \).
    IfTrue/IfFalse/If-NoElse liefern den Schritt. ✓

- **Fall (Ctor):** \( e = K(e_1, ..., e_n) \).
  Sei \( i \) minimal mit \( e_i \) kein Wert. Nach IV \( e_i \to e_i' \),
  also Constructor-App. Sind alle \( e_i \) Werte, ist \( K(\bar v) \) ein
  Constructor-Wert (per Definition). ✓

- **Fall (Match):** \( e = e_0 \text{ match } \{\text{case } P \Rightarrow e'\}^* \).
  - \( e_0 \) kein Wert: Nach IV \( e_0 \to e_0' \), also Match-Scrutinee. ✓
  - \( e_0 \) Wert: Nach Lemma 2 ist \( e_0 \) ein Constructor-Wert,
    Literal, oder Wildcard. Der passende Zweig wird gewählt
    (Match-Constructor, Match-Literal, Match-Wildcard). ✓

- **Fall (BinOp):** \( e = e_1 \oplus e_2 \).
  - \( e_1 \) kein Wert: BinOp-Links. ✓
  - \( e_1 \) Wert, \( e_2 \) kein Wert: BinOp-Rechts. ✓
  - Beide Werte: \( \delta(\oplus, v_1, v_2) \) ist definiert (BinOp-Werte). ✓

- **Fall (UnaryOp):** Analog. ✓

- **Fall (Tuple):** Wie Ctor: linkester Nicht-Wert wird reduziert. ✓

- **Fall (Ascribe):** \( e = (e_0 : \tau) \). Ascription-Regel: \( e \to e_0 \). ✓

- **Fall (Let):** \( e = \text{let } x = e_1 \text{ in } e_2 \).
  - \( e_1 \) kein Wert: Let-Links. ✓
  - \( e_1 \) Wert: Let-Regel: \( \text{let } x = v \text{ in } e_2 \to [x \mapsto v] e_2 \). ✓

- **Fall (Lit-Int, Lit-Bool, Lit-Str, Unit):** Werte. Kein Schritt nötig. ✓

In allen Fällen existiert entweder ein Reduktionsschritt oder der
Term ist bereits ein Wert. Somit gilt Progress für alle
wohldefinierten geschlossenen Terme. ∎

### 12.4 Preservation-Theorem

**Theorem 4 (Preservation / Subject Reduction).**
Wenn \( \Gamma \vdash e : \tau \) und \( e \to e' \), dann \( \Gamma \vdash e' : \tau \).

*Beweis.* Induktion über die Ableitung \( e \to e' \) (Reduktionsrelation).
Wir betrachten jeden Fall der Reduktionsrelation.

- **Fall (Beta):** \( e = (\lambda x. e_0) v \to [x \mapsto v] e_0 = e' \).
  - Aus \( \Gamma \vdash (\lambda x. e_0) v : \tau \) folgt mit (App):
    \( \Gamma \vdash \lambda x. e_0 : \tau_2 \to \tau \) und \( \Gamma \vdash v : \tau_2 \).
  - Aus \( \Gamma \vdash \lambda x. e_0 : \tau_2 \to \tau \) folgt mit (Abs):
    \( \Gamma, x : \tau_2 \vdash e_0 : \tau \).
  - Mit Lemma 1 (Substitution): \( \Gamma \vdash [x \mapsto v] e_0 : \tau \). ✓

- **Fall (App-Links):** \( e = e_1 e_2 \to e_1' e_2 = e' \) mit \( e_1 \to e_1' \).
  - Aus \( \Gamma \vdash e_1 e_2 : \tau \) folgt:
    \( \Gamma \vdash e_1 : \tau_2 \to \tau \), \( \Gamma \vdash e_2 : \tau_2 \).
  - Nach IV: \( \Gamma \vdash e_1' : \tau_2 \to \tau \).
  - Mit (App): \( \Gamma \vdash e_1' e_2 : \tau \). ✓

- **Fall (App-Rechts):** \( e = v e_2 \to v e_2' = e' \) mit \( e_2 \to e_2' \).
  Analog App-Links. ✓

- **Fall (IfTrue):** \( e = \text{if true then } e_2 \text{ else } e_3 \to e_2 = e' \).
  Aus \( \Gamma \vdash \text{if true then } e_2 \text{ else } e_3 : \tau \)
  folgt mit Regel If: \( \Gamma \vdash e_2 : \tau \). ✓

- **Fall (IfFalse):** Analog mit \( e_3 \). ✓

- **Fall (If-Cond):** \( e = \text{if } e_1 \text{ then } e_2 \text{ else } e_3 \to \text{if } e_1' \text{ then } e_2 \text{ else } e_3 = e' \).
  - Aus If-Regel: \( \Gamma \vdash e_1 : \text{Bool} \), \( \Gamma \vdash e_2 : \tau \), \( \Gamma \vdash e_3 : \tau \).
  - Nach IV: \( \Gamma \vdash e_1' : \text{Bool} \).
  - Mit If: \( \Gamma \vdash \text{if } e_1' \text{ then } e_2 \text{ else } e_3 : \tau \). ✓

- **Fall (If-NoElse-True/False):** Analog mit If-Unit-Regel. ✓

- **Fall (Match-Constructor):**
  \( e = K(\bar v) \text{ match } \{ \text{case } K(\bar x) \Rightarrow e_K; \text{rest} \} \to [\bar x \mapsto \bar v] e_K = e' \).
  - Aus Match-Regel: \( \Gamma \vdash K(\bar v) : C\bar\tau \),
    \( \Gamma, \bar x : \bar\tau_i \vdash e_K : \tau \).
  - Aus Ctor-Regel: \( \Gamma \vdash v_j : \tau_j \).
  - Mit Lemma 1 (Substitution, mehrfach): \( \Gamma \vdash [\bar x \mapsto \bar v] e_K : \tau \). ✓

- **Fall (Match-Wildcard):** \( e = v \text{ match } \{ \text{case } \_ \Rightarrow e_0; \text{rest} \} \to e_0 = e' \).
  Aus Match-Regel: \( \Gamma \vdash v : C\bar\tau \), \( \Gamma \vdash e_0 : \tau \). ✓

- **Fall (Match-Literal):** Analog (Literal-Typ aus Lit-Regel).
  \( \Gamma \vdash e_0 : \tau \) direkt aus Match-Regel. ✓

- **Fall (Match-Scrutinee):** \( e = e_0 \text{ match } \{ \text{case } P \Rightarrow e'\}^* \to e_0' \text{ match } \{ \text{case } P \Rightarrow e'\}^* = e'' \).
  - Aus Match: \( \Gamma \vdash e_0 : C\bar\tau \), \( \Gamma, \bar x : \bar\tau_i \vdash e_i : \tau \).
  - Nach IV: \( \Gamma \vdash e_0' : C\bar\tau \).
  - Mit Match: \( \Gamma \vdash e_0' \text{ match } \{ \text{case } P \Rightarrow e'\}^* : \tau \). ✓

- **Fall (BinOp-Links):** \( e = e_1 \oplus e_2 \to e_1' \oplus e_2 = e' \).
  - Aus BinOp: \( \Gamma \vdash e_1 : \tau_0 \), \( \Gamma \vdash e_2 : \tau_0 \), Ergebnis \( \tau \).
  - Nach IV: \( \Gamma \vdash e_1' : \tau_0 \).
  - Mit BinOp: \( \Gamma \vdash e_1' \oplus e_2 : \tau \). ✓

- **Fall (BinOp-Rechts):** Analog. ✓

- **Fall (BinOp-Werte):** \( e = v_1 \oplus v_2 \to \delta(\oplus, v_1, v_2) = e' \).
  - \( \delta \) ist typkorrekt per Definition: \( \Gamma \vdash \delta(\oplus, v_1, v_2) : \tau \). ✓

- **Fall (UnaryOp):** Analog BinOp. ✓

- **Fall (Tuple):** \( e = (v_1, ..., e_i, ..., v_n) \to (v_1, ..., e_i', ..., v_n) = e' \).
  - Aus Tuple: \( \Gamma \vdash e_i : \tau_i \), und \( \Gamma \vdash v_j : \tau_j \).
  - Nach IV: \( \Gamma \vdash e_i' : \tau_i \).
  - Mit Tuple: \( \Gamma \vdash (v_1, ..., e_i', ..., v_n) : (\tau_1, ..., \tau_n) \). ✓

- **Fall (Let):** \( e = \text{let } x = v \text{ in } e_0 \to [x \mapsto v] e_0 = e' \).
  - Aus Let-Regel: \( \Gamma \vdash v : \tau' \), \( \Gamma, x : \tau' \vdash e_0 : \tau \).
  - Mit Lemma 1: \( \Gamma \vdash [x \mapsto v] e_0 : \tau \). ✓

- **Fall (Let-Links):** \( e = \text{let } x = e_1 \text{ in } e_0 \to \text{let } x = e_1' \text{ in } e_0 = e' \).
  - Aus Let: \( \Gamma \vdash e_1 : \tau' \), \( \Gamma, x : \tau' \vdash e_0 : \tau \).
  - Nach IV: \( \Gamma \vdash e_1' : \tau' \).
  - Mit Let: \( \Gamma \vdash \text{let } x = e_1' \text{ in } e_0 : \tau \). ✓

- **Fall (Ascription):** \( e = (e_0 : \tau) \to e_0 = e' \).
  Aus Ascribe-Regel: \( \Gamma \vdash e_0 : \tau \). ✓

- **Fall (Constructor-App):** Analog Tuple. ✓

In jedem Fall gilt \( \Gamma \vdash e' : \tau \). Somit ist Preservation
für alle Reduktionsschritte erfüllt. ∎

### 12.5 Type-Soundness-Theorem

**Theorem 5 (Type Soundness).**
Wenn \( \Gamma \vdash e : \tau \), dann gilt für die Auswertung von \( e \):

1. **Termination oder Divergenz:** Die Reduktion \( e \to^* e' \) endet
   entweder in einem Wert \( v \) oder divergiert (endlose Reduktion).
2. **Type Preservation:** Falls \( e \to^* v \), dann \( \Gamma \vdash v : \tau \).
3. **No Stuck Terms:** Für geschlossene Terme \( \emptyset \vdash e : \tau \)
   gilt: \( e \) ist entweder ein Wert oder es existiert \( e' \) mit \( e \to e' \).

*Beweis.*
- (1) folgt aus der Definition der reflexiv-transitiven Hülle \( \to^* \).
- (2) folgt durch wiederholte Anwendung von Theorem 4 (Preservation)
  auf die Reduktionskette \( e \to e_1 \to ... \to v \).
- (3) folgt aus Theorem 3 (Progress): für geschlossene, wohlgetypte
  Terme gibt es immer einen Schritt oder der Term ist bereits ein Wert.

∎

### 12.6 Äquivalenz: Small-Step vs. Big-Step

**Theorem 6 (Äquivalenz).**
Für geschlossene Terme \( e \) gilt:
\[
e \Downarrow v \quad \text{genau dann, wenn} \quad e \to^* v \text{ und } v \text{ ist ein Wert}.
\]

*Beweis.*
- **(\(\Rightarrow\))** Jeder Big-Step-Ableitung entspricht eine Folge von
  Small-Step-Reduktionen. Dies wird durch Induktion über die
  Big-Step-Ableitung gezeigt: Jede Big-Step-Regel verwendet Substitution
  oder primitive Operationen, die direkt den Small-Step-Regeln entsprechen.
  Die App-Regel (\( e_1 e_2 \Downarrow v \)) wird zu App-Links \(\to\) App-Rechts
  \(\to\) Beta in der Small-Step-Semantik.
- **(\(\Leftarrow\))** Jede Small-Step-Reduktionskette \( e \to^* v \) mit
  Wert \( v \) entspricht einer Big-Step-Ableitung. Dies folgt aus der
  Determinismus- und Konfluenz-Eigenschaft der Reduktion (für den
  einfach getypten λ-Kalkül mit strikter Auswertung).

∎

---

## 13. Implementierte Abweichungen vom Formalen Modell

Der Parser/Compiler weicht in folgenden Punkten vom formalen Kalkül ab:

| Aspekt | Formales Modell | Implementierung |
|--------|----------------|-----------------|
| Let-in-Ausdrücken | `let x = e₁ in e₂` | `val x = e₁` als Statement im Block |
| Blocksyntax | `{ let x₁ = e₁; ...; eₙ }` | `{ val x₁ = e₁; ...; eₙ }` |
| Block-Reduktion | `{ let x = v; ... } → { [x↦v]... }` (siehe §3a) | `reduceBlock` in `SmallStepReducer.java:128` |
| Mutual Recursion | separate `let rec`-Bindungen mit gegenseitiger Sichtbarkeit | Adjazente `def`-Deklarationen → `MutualLetNode` (gegruppt im Parser) |
| Rekursive Let-Bindung | `let rec x = λy. e₁ in e₂` | `def x(y: τ): τ' = e₁` |
| Currying | `K(e₁, ..., eₙ)` als \( n \)-stellig | `K(e₁)(...(eₙ))` curried |
| Pattern Matching | `e₁ match { case P ⇒ e₂ }*` | `e₁ match { case P => e₂ }` |
| Unicode | — | `≥`, `≤`, `≠`, `→` als Sugar |
| List-Literal | — | `[1,2,3]` desugared zu `Cons(1, Cons(2, Cons(3, Nil)))` |
| Range | — | `a..b` als Sugar für Tupel |

Trotz dieser syntaktischen Unterschiede ist die **Kernsemantik identisch**:
Die Desugarisierung übersetzt alle Scala-Konstrukte in das λ-Core-Modell,
bevor Typcheck und Reduktion angewendet werden.*
