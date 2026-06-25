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

## 11. Type-Soundness

### Theorem (Progress)

Wenn Γ ⊢ e : τ und e ist kein Wert, dann existiert e′ mit e → e′.

*Beweis:* Induktion über die Typableitung. Jeder nicht-Value
hat eine äußerste Redex (App, If, Match, BinOp, UnaryOp, Ascribe).

### Theorem (Preservation)

Wenn Γ ⊢ e : τ und e → e′, dann Γ ⊢ e′ : τ.

*Beweis:* Induktion über die Reduktionsrelation. Jede Regel
erhält den Typ.

### Theorem (Type Soundness)

Wenn Γ ⊢ e : τ, dann terminiert die Reduktion von e entweder
in einem Wert v mit Γ ⊢ v : τ, oder sie divergiert.

*Folgt aus Progress + Preservation per Induktion.
(Randfall: Rekursion via let rec kann divergieren.)*
