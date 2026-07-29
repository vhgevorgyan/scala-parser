# Scala-to-λ-Core Map

## Motivation
Dieses Dokument definiert eine **systematische Abbildung** von Scala-Konstrukten auf die Erweiterungen des λ-Kalküls, die dieser Parser implementiert. Ziel ist:

1. **Nachweis der Ausdrucksstärke**: Jedes Scala-Konstrukt wird durch eine oder mehrere λ-Erweiterungen modelliert.
2. **Beweisbar korrekte Übersetzung**: Die Abbildung ist strukturell (jedes Scala-Konstrukt → genau ein AST-Knoten oder eine Kombination).
3. **Testbare Korrespondenz**: Für jedes Konstrukt gibt es einen Test, der die Übersetzung + Auswertung verifiziert.

## λ-Core Bottom-up

Der λ-Kern wird **bottom-up** erweitert, sodass jede Stufe auf der vorherigen aufbaut:

```
Basis (reiner λ-Kalkül)
  → Let-Bindings (val/def)
  → If/Unit (Bedingungen)
  → ADTs (enum, pattern matching)
  → Literale + Operatoren (Int, Bool, Char, arithmetic, comparison)
  → Tupel (Paare, Produkte)
  → Typ-Ascription + Type Aliases
  → Listen (syntactic sugar über ADTs)
  → Range-Literale (syntactic sugar)
  → Unicode-Operatoren (syntactic sugar)
  → Infix-Constructor :: (syntactic sugar)
  → While-Schleife
  → Multi-Param-Lambda + Multi-Param-Listen (Desugaring)
  → Multi-Arg-Application (Desugaring)
  → Package/Import/Annotation/Sealed (Syntax-Kompatibilität, ignoriert)
```

## Mapping-Tabelle

| # | Scala-Konstrukt | λ-Erweiterung | AST-Knoten | Beispiel |
|---|----------------|---------------|------------|----------|
| 1 | `val x = e` | Let-Binding | `LetStatementNode` | `val x = 42` |
| 2 | `def f(x: T): U = e` | Rekursive Let + Lambda | `LetStatementNode(isRec=true)` + `LambdaNode` | `def id(x: Int): Int = x` |
| 3 | `if (c) t else e` | If-Then-Else | `IfNode` | `if (x > 0) 1 else -1` |
| 4 | `if (c) t` | If mit implizitem Unit | `IfNode(else=UnitLiteralNode)` | `if (debug) print(x)` |
| 5 | `{ s1; s2; expr }` | Block | `BlockNode` | `{ val x = 1; x + 2 }` |
| 6 | `(x: T) => e` | Lambda-Abstraktion | `LambdaNode` | `(x: Int) => x + 1` |
| 7 | `f(e)` | Applikation | `ApplicationNode` | `fact(5)` |
| 8 | `42` | Integer-Literal | `LiteralNode` | `42` |
| 9 | `true` / `false` | Boolean-Literal | `BoolNode` | `true` |
| 10 | `()` | Unit-Literal | `UnitLiteralNode` | `()` |
| 11 | `"hello"` | String-Literal | `StringLiteralNode` | `"hello"` |
| 12 | `a + b`, `a - b`, etc. | Arithmetik | `BinaryExprNode(op)` | `x + 1` |
| 13 | `-x` | Unäre Negation | `UnaryExprNode("-")` | `-x` |
| 14 | `a < b`, `a == b`, etc. | Vergleich | `BinaryExprNode(op)` | `x > 0` |
| 15 | `a && b`, `a \|\| b` | Bool-Operatoren | `BinaryExprNode(op)` | `x > 0 && y < 5` |
| 16 | `!b` | Logisches Not | `UnaryExprNode("!")` | `!done` |
| 17 | `(x: T)` | Typ-Ascription | `AscriptionNode` | `(42: Int)` |
| 18 | `type A = B` | Type Alias | `TypeAliasNode` | `type Seconds = Int` |
| 19 | `(a, b)` | Tupel | `TupleNode` + `TypeTuple` | `(1, "hello")` |
| 20 | `enum Option[T] { ... }` | ADT-Deklaration | `EnumDeclNode` | `enum Option[T] { ... }` |
| 21 | `x match { case ... }` | Pattern Matching | `CaseNode` | `x match { case 0 => "zero" }` |
| 22 | `Nil` / `Cons(1, Nil)` | ADT-Constructor | `ApplicationNode(IdentifierNode("Cons"), arg)` | `Cons(1, Nil)` |
| 23 | `[1, 2, 3]` | List-Literal (Sugar) | Desugared → `Cons(1, Cons(2, Cons(3, Nil)))` | `[1, 2, 3]` |
| 24 | `x :: xs` | Infix-Constructor (Sugar) | Desugared → `::(x, xs)` | `1 :: 2 :: Nil` |
| 25 | `1..10` | Range-Literal (Sugar) | `BinaryExprNode("..")` → `TupleNode` bei Auswertung | `1..10` |
| 26 | `≥`, `≤`, `≠`, `→`, `←`, `·` | Unicode-Operatoren (Sugar) | Wie ihre ASCII-Äquivalente | `x ≥ 0` |
| 27 | `case x =>` (Wildcard) | Wildcard-Pattern | `CaseBranch(constructor=null, bindings=[], body)` | `x match { case _ => 0 }` |
| 28 | `case 0 =>` (Literal-Pattern) | Literal-Pattern | `CaseBranch(literalPattern=LiteralNode("0"))` | `x match { case 0 => "zero" }` |
| 29 | `case Cons(h, t) =>` | Constructor-Pattern | `CaseBranch(constructor="Cons", bindings=[h,t])` | `xs match { case Cons(h, t) => h }` |
| 30 | `case x :: xs =>` | Infix-Constructor-Pattern | Desugared → `::(x, xs)` | `xs match { case x :: xs => x }` |
| 31 | `while (c) body` | While-Loop | `WhileNode(cond, body)` | `while (i > 0) { i = i - 1 }` |
| 32 | `'a'` | Char-Literal | `CharLiteralNode` + `TypeChar` | `'A'` |
| 33 | `Char` | Char-Typ | `TypeChar` | `val c: Char = 'A'` |
| 34 | `(x: T, y: U) => e` | Multi-Param-Lambda | Desugared → `(x: T) => (y: U) => e` | `(x: Int, y: Int) => x + y` |
| 35 | `def f(x: T)(y: U): V = e` | Multi-Param-Listen | Desugared → `def f = (x: T) => (y: U) => e` | `def f(x: Int)(y: Int): Int = x * y` |
| 36 | `f(x, y)` | Multi-Arg-Application | Desugared → `f(x)(y)` | `add(3, 4)` |
| 37 | `package p` | Package-Deklaration | **Ignoriert** (Syntax-Kompatibilität) | `package com.example` |
| 38 | `import p.X` | Import | **Ignoriert** (Syntax-Kompatibilität) | `import scala.io.StdIn` |
| 39 | `sealed trait T` | Sealed-Trait | **Ignoriert** (Syntax-Kompatibilität) | `sealed trait Option[T]` |
| 40 | `@ann def f` | Annotation | **Ignoriert** (Syntax-Kompatibilität) | `@Override def toString = ...` |

## Testabdeckung

Jeder Eintrag in der Tabelle wird durch einen oder mehrere Tests in `ScalaMapTest.java` abgedeckt.
Die Tests verwenden den `CompilerPipeline`-Ansatz: tokenisieren → parsen → typchecken → evaluieren → Ergebnis prüfen.

## Metrik

Aktuell abgedeckte Konstrukte: **40 von 40** (100 %)
