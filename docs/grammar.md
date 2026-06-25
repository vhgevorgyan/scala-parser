# Scala Parser — Language Grammar

## Notation

```
Production  = LHS ::= RHS
Terminal    = "exact"
Nonterminal = <name>
Choice      = ... | ...
Group       = ( ... )
Option      = [ ... ]
Repetition  = { ... }     (zero or more)
```

---

## Program

```
<program>     ::= { <statement> }

<statement>   ::= "type" <ident> "=" <type>                  (type alias)
               | "val" <ident> [ ":" <type> ] "=" <expression>
               | "def" <ident> [ "(" <param-def> { "," <param-def> } ")" [ ":" <type> ] ] "=" <expression>
               | <enum-decl>
               | <expression>

<param-def>   ::= <ident> [ ":" <type> ]
```

## Expressions

```
<expression>         ::= <if-match-expr> [ ".." <if-match-expr> ] [ ":" <type> ]

<if-match-expr>     ::= "if" "(" <expression> ")" <expression> [ "else" <expression> ]
                       | <or-expr> [ "match" "{" <match-body> "}" ]

<match-body>         ::= { "case" <pattern> "=>" <expression> [ ";" ] }

<or-expr>            ::= <and-expr> { "||" <and-expr> }

<and-expr>           ::= <equal-expr> { "&&" <equal-expr> }

<equal-expr>         ::= <compare-expr> { ( "==" | "!=" ) <compare-expr> }

<compare-expr>       ::= <add-expr> { ( "<" | ">" | "<=" | ">=" ) <add-expr> }

<add-expr>           ::= <mul-expr> { ( "+" | "-" ) <mul-expr> }

<mul-expr>           ::= <unary-expr> { ( "*" | "/" ) <unary-expr> }

<unary-expr>         ::= ( "!" | "-" ) <unary-expr>
                       | <apply-expr>

<apply-expr>         ::= <primary> { "(" <expression> ")" | "{" <program> "}" }    (left-associative)

<primary>            ::= <number>
                       | <bool>
                       | <string>
                       | <ident>
                       | "(" ")"                                                   (unit literal)
                       | "(" <expression> "," <expression> { "," <expression> } ")" (tuple)
                       | "(" <ident> [ ":" <type> ] ")" "=>" <expression>         (lambda)
                       | "(" <expression> ")"                                      (parenthesized expr)
                       | "[" [ <expression> { "," <expression> } ] "]"            (list literal)
                       | "{" <program> "}"                                         (block)
```

## Patterns

```
<pattern>            ::= <ident> "::" <ident>                   (infix cons)
                       | <constructor-name> [ "(" <ident> { "," <ident> } ")" ]
                       | <number>                             (literal pattern)
                       | <bool>                               (literal pattern)
                       | "_"                                  (wildcard)
                       | <ident>                              (variable binding)
```

## Types

```
<type>               ::= <type-atomic> ( "=>" <type> )?

<type-atomic>        ::= "Int"
                       | "Boolean"
                       | "String"
                       | "Unit"
                       | <ident>                                (ADT with params)
                       | "(" <type> { "," <type> } ")"          (tuple type)
                       | "(" <type> ")"
```

## Enum Declaration

```
<enum-decl>          ::= "enum" <ident> [ "[" <ident> { "," <ident> } "]" ] "{" { "case" <constructor> } "}"

<constructor>        ::= <ident> [ "(" <field> { "," <field> } ")" ]

<field>              ::= <ident> ":" <type>
```

## Identifiers

```
<ident>              ::= <letter> { <letter> | <digit> | "_" }
```

## Literals

```
<number>             ::= <digit> { <digit> }
<bool>               ::= "true" | "false"
<string>             ::= "\"" { <char> | <escape> } "\""
<escape>             ::= "\\n" | "\\t" | "\\\\" | "\\\"" | "\\0"
```

## Terminals

| Terminal      | Meaning                       |
|---------------|-------------------------------|
| `"val"`       | Immutable binding             |
| `"def"`       | Recursive definition          |
| `"enum"`      | Algebraic data type (Scala 3) |
| `"type"`      | Type alias                    |
| `"match"`     | Pattern match                 |
| `"case"`      | Match branch                  |
| `"if"`        | Conditional                   |
| `"else"`      | If-else                       |
| `"true"`      | Boolean literal               |
| `"false"`     | Boolean literal               |
| `"Int"`       | Integer type                  |
| `"Boolean"`   | Boolean type                  |
| `"String"`    | String type                   |
| `"Unit"`      | Unit type                     |
| `"=>"`        | Arrow (lambda, match, type)   |
| `"("`         | Left paren                    |
| `")"`         | Right paren                   |
| `"{"`         | Left brace (block, match)     |
| `"}"`         | Right brace (block, match)    |
| `"["` / `"]"` | Bracket (type params, lists)  |
| `","`         | Comma (separator)             |
| `"_"`         | Wildcard pattern              |
| `"::"`        | Infix cons (List constructor) |
| `".."`        | Range operator                |
| `"="`         | Assignment                    |
| `";"`         | Statement separator           |
| `":"`         | Type annotation               |
| `"+"`         | Add                           |
| `"-"`         | Subtract / unary minus        |
| `"*"`         | Multiply                      |
| `"/"`         | Divide                        |
| `"=="`        | Equality                      |
| `"!="`        | Inequality                    |
| `<` / `>` / `<=` / `>=` | Comparison          |
| `"!"`         | Logical not                   |
| `"&&"`        | Logical and                   |
| `"||"`        | Logical or                    |
| ≥ / ≤ / ≠     | Unicode aliases (≥, ≤, ≠)     |
| → / ← / ·     | Unicode: →(=>), ←(=), ·(*)   |

## Comments

```
<line-comment>    ::= "//" { <any except newline> }
<block-comment>   ::= "/*" { <any> } "*/"
```

## Built-in Functions & Values

| Name      | Signature                           | Description            |
|-----------|-------------------------------------|------------------------|
| `succ`    | `Int => Int`                        | Successor              |
| `pred`    | `Int => Int`                        | Predecessor            |
| `iszero`  | `Int => Boolean`                    | Zero test              |
| `print`   | `Int => Int`                        | Print (side effect)    |
| `Nil`     | `List[a]`                           | Empty list constructor |
| `Cons`    | `a => List[a] => List[a]`           | List cons constructor  |

## Syntactic Sugar

| Sugar | Desugars to |
|-------|------------|
| `[1, 2, 3]` | `Cons(1, Cons(2, Cons(3, Nil)))` |
| `x :: xs` (pattern) | `::(x, xs)` (infix cons pattern) |
| `1..10` | range value as `(1, 10)` tuple |
| `(a, b)` (expr) | Tuple of two elements |
| `(Int, String)` (type) | Tuple type (product) |

## Precedence (lowest to highest)

| Level | Assoc | Operators        |
|-------|-------|------------------|
| 1     | left  | `match`          |
| 2     | left  | `..`             |
| 3     | left  | `||`             |
| 4     | left  | `&&`             |
| 5     | right | `!`              |
| 6     | left  | `==` `!=`        |
| 7     | left  | `<` `>` `<=` `>=`|
| 8     | left  | `+` `-`          |
| 9     | left  | `*` `/`          |
| 10    | left  | Application      |

## Example Programs

```scala
// Factorial with multi-param syntax
def fact(n: Int): Int = if (n == 0) 1 else n * fact(n - 1)
fact(5)
```

```scala
// If without else (optional else)
def abs(n: Int): Int = if (n < 0) -n else n

// If without else returns Unit
val x: Unit = if (n < 0) print(-1)
```

```scala
// ADT: Maybe (Scala 3 enum)
enum Maybe[A] {
  case Nothing
  case Just(x: A)
}

// Using Maybe with match, wildcard, literal patterns
x match {
  case Nothing => 0
  case Just(y) => y
  case _ => -1
}
```

```scala
// Literal patterns
def classify(x: Int): String = x match {
  case 0 => "zero"
  case 1 => "one"
  case _ => "many"
}
```

```scala
// Unit type + ()
val greet: Unit = print(42)
val empty: Unit = ()
```

```scala
// Type ascription
val x = (42: Int)
val y = (x + 1: Int)
```

```scala
// Higher-order function
val twice = (f: Int => Int) => (x: Int) => f(f(x))
twice((y: Int) => y + 1)(0)
```

```scala
// Tuple expressions + types
val pair = (1, "hello")
val tupleType: (Int, String) = pair
```

```scala
// List literal (desugars to Cons/Nil)
val list = [1, 2, 3]
list match {
  case x :: xs => x
  case Nil => 0
}
```

```scala
// Range
val range = 1..10
```

```scala
// Type alias
type Seconds = Int
val t: Seconds = 42
```

```scala
// Unicode operators
val x = 5
val check = x ≥ 3 && x ≠ 0  // uses ≥ and ≠
```

## Type System (Hindley-Milner)

```
τ  ::= Int | Boolean | String | Unit | (τ₁, ..., τₙ) | α | τ => τ | C τ*     (monomorph)
σ  ::= ∀α*. τ                                           (type scheme)
Γ  ::= · | Γ, x : σ                                     (environment)
```

**Key rules:**

```
(Lam)     Γ, x : τ₁ ⊢ e : τ₂
          ------------------
          Γ ⊢ (x: τ₁) => e : τ₁ => τ₂

(App)     Γ ⊢ f : τ₁ => τ₂    Γ ⊢ a : τ₁
          --------------------------------
          Γ ⊢ f a : τ₂

(Let)     Γ ⊢ e₁ : σ    Γ, x : σ ⊢ e₂ : τ
          ----------------------------------
          Γ ⊢ val x = e₁ ; e₂ : τ

(LetRec)  Γ, f : τ₁ => τ₂ ⊢ λx.e : τ₁ => τ₂    Γ, f : τ₁ => τ₂ ⊢ b : τ
          -----------------------------------------------------------------
          Γ ⊢ def f(x: τ₁): τ₂ = e ; b : τ

(Case)    Γ ⊢ s : C ᾱ*    Γ, x : τᵢ ⊢ bᵢ : τ    (∀i ∈ branches)
         -------------------------------------------------------
         Γ ⊢ s match { case Cᵢ(x) => bᵢ } : τ

(Wildcard) Γ, _ ⊢ b : τ
           --------------
           Γ ⊢ s match { case _ => b } : τ

(Data)    enum C[A*] { case K₁(x: τ₁); ...; case Kₙ(x: τₙ) }
          ⇒ Γ ⊢ Kᵢ : ∀ᾱ. τᵢ* => C ᾱ*
```

## Blocks

Block expressions allow sequential composition within a single expression:

```
{ val x = 1; x + 1 }   // evaluates to 2
{ val x = 1; val y = x + 1; y }  // evaluates to 2
```

Block scoping is lexical: bindings introduced inside a block are not visible outside it. The value of a block is the value of its last statement.
