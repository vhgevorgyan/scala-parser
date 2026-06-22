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
<program>    ::= { <statement> ";" }

<statement>  ::= <expression>
               | "let" [ "rec" ] <ident> ( ":" <type> )? "=" <expression>
               | "data" <ident> <ident>* "=" <constructor-list>
```

## Expressions

```
<expression>         ::= <if-expr>
                        | <case-expr>
                        | <lambda-expr>
                        | <or-expr>

<if-expr>            ::= "if" <expression> "then" <expression> "else" <expression>

<case-expr>          ::= "case" <expression> "of" <branch> { "|" <branch> }

<branch>             ::= ( "|" )? <pattern> "->" <expression>

<lambda-expr>        ::= "\" <ident>+ "->" <expression>

<or-expr>            ::= <and-expr> { "||" <and-expr> }

<and-expr>           ::= <not-expr> { "&&" <not-expr> }

<not-expr>           ::= "!" <not-expr>
                       | <equal-expr>

<equal-expr>         ::= <compare-expr> ( "==" <compare-expr> )?

<compare-expr>       ::= <add-expr> ( ("<" | ">" | "<=" | ">=") <add-expr> )?

<add-expr>           ::= <mul-expr> { ("+" | "-") <mul-expr> }

<mul-expr>           ::= <unary-expr> { ("*" | "/") <unary-expr> }

<unary-expr>         ::= "-" <unary-expr>
                       | <apply-expr>

<apply-expr>         ::= <atomic> { <atomic> }    (left-associative application)

<atomic>             ::= <number>
                        | <bool>
                        | <string>
                        | <ident>
                        | "(" <expression> ")"
                        | <constructor-expr>

<constructor-expr>   ::= <constructor-name> [ <atomic> ]  (partial application)
```

## Patterns

```
<pattern>            ::= <constructor-name> [ <pattern> ]
                        | <ident>            (variable binding)
                        | "_"               (wildcard, currently parsed as identifier)
```

## Types

```
<type>               ::= <type-atomic> ( "->" <type> )?

<type-atomic>        ::= "Number"
                        | "Bool"
                        | "String"
                        | <ident> [ <type-atomic> ]   (parameterized ADT)
                        | "(" <type> ")"
                        | <type-var>

<type-var>           ::= <ident>                      (lowercase, e.g. "a")
```

## Constructor List

```
<constructor-list>   ::= <constructor-def> { "|" <constructor-def> }

<constructor-def>    ::= <ident> <ident>*            (constructor name + arg types)
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
| `"let"`       | Let binding                   |
| `"rec"`       | Recursive let                 |
| `"data"`      | Algebraic data type           |
| `"case"`      | Pattern match                 |
| `"of"`        | Case separator                |
| `"if"`        | Conditional                   |
| `"then"`      | If-then                       |
| `"else"`      | If-else                       |
| `"true"`      | Boolean literal               |
| `"false"`     | Boolean literal               |
| `"Number"`    | Number type                   |
| `"Bool"`      | Boolean type                  |
| `"String"`    | String type                   |
| `"\\"`        | Lambda abstraction            |
| `"."`         | Lambda body separator         |
| `"("`         | Left paren                    |
| `")"`         | Right paren                   |
| `"|"`         | Pipe (branch/case separator)  |
| `"->"`        | Arrow (branch, function type) |
| `"="`         | Assignment                    |
| `";"`         | Statement separator           |
| `":"`         | Type annotation               |
| `"+"`         | Add                           |
| `"-"`         | Subtract / Negate             |
| `"*"`         | Multiply                      |
| `"/"`         | Divide                        |
| `"=="`        | Equality                      |
| `<` / `>` / `<=` / `>=` | Comparison          |
| `"!"`         | Logical not                   |
| `"&&"`        | Logical and                   |
| `"\|\|"`      | Logical or                    |

## Comments

```
<line-comment>    ::= "//" { <any except newline> }
<block-comment>   ::= "/*" { <any> } "*/"
```

## Built-in Functions

| Function  | Signature                     | Description            |
|-----------|-------------------------------|------------------------|
| `succ`    | `Number -> Number`            | Successor              |
| `pred`    | `Number -> Number`            | Predecessor            |
| `iszero`  | `Number -> Bool`              | Zero test              |
| `print`   | `String -> String`            | Print (side effect)    |

## Precedence (lowest to highest)

| Level | Assoc | Operators        |
|-------|-------|------------------|
| 1     | left  | `\|` (pipe in program, not expression) |
| 2     | right | `\`  (lambda)    |
| 3     | left  | `\|\|`           |
| 4     | left  | `&&`             |
| 5     | right | `!`              |
| 6     | left  | `==`             |
| 7     | left  | `<` `>` `<=` `>=`|
| 8     | left  | `+` `-`          |
| 9     | left  | `*` `/`          |
| 10    | right | `-` (unary)      |
| 11    | left  | Application      |

## Example Programs

```scala
// Factorial
let rec fac = \n -> if iszero n then 1 else n * fac (pred n)
```

```scala
// ADT: Maybe
data Maybe a = MyNothing | MyJust a

// Using Maybe
let mapMaybe = \f -> \x -> case x of MyNothing -> MyNothing | MyJust a -> MyJust (f a)
mapMaybe succ (MyJust 5)
```

```scala
// Boolean ADT
data MyBool = MyTrue | MyFalse

let not = \b -> case b of MyTrue -> MyFalse | MyFalse -> MyTrue
not MyTrue
```

## Type System (Hindley-Milner)

```
τ  ::= Number | Bool | String | α | τ → τ | C τ*     (monomorph)
σ  ::= ∀α*. τ                                           (type scheme)
Γ  ::= · | Γ, x : σ                                     (environment)
```

**Key rules:**

```
(Lam)     Γ, x : τ₁ ⊢ e : τ₂
          ------------------
          Γ ⊢ λx.e : τ₁ → τ₂

(App)     Γ ⊢ f : τ₁ → τ₂    Γ ⊢ a : τ₁
          --------------------------------
          Γ ⊢ f a : τ₂

(Let)     Γ ⊢ e₁ : σ    Γ, x : σ ⊢ e₂ : τ
          ----------------------------------
          Γ ⊢ let x = e₁ in e₂ : τ

(LetRec)  Γ, f : τ₁ → τ₂ ⊢ λx.e : τ₁ → τ₂    Γ, f : τ₁ → τ₂ ⊢ b : τ
          -----------------------------------------------------------------
          Γ ⊢ let rec f = λx.e in b : τ

(Case)    Γ ⊢ s : C ᾱ*    Γ, x : τᵢ ⊢ bᵢ : τ    (∀i ∈ branches)
          -------------------------------------------------------
          Γ ⊢ case s of { Cᵢ x -> bᵢ } : τ

(Data)    data C ᾱ* = K₁ τ₁* | ... | Kₙ τₙ*
          ⇒ Γ ⊢ Kᵢ : ∀ᾱ. τᵢ* → C ᾱ*
```
