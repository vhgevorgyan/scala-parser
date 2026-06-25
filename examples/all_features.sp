// Unicode operators
val x: Int = 5
val ge: Boolean = x ≥ 3
val ne: Boolean = x ≠ 0
val inc: Int = succ(x)

// Type alias
type Seconds = Int
val t: Seconds = 42

// If without else
def printAbs(n: Int): Unit = if (n < 0) print(-n)

// Type ascription
val y = (x + 1: Int)

// Unit
val u: Unit = ()

// Range
val r = 1..10

// List literal (desugars to Cons/Nil)
val list = [1, 2, 3]
list match {
  case x :: xs => x
  case Nil => 0
}

// Wildcard + literal patterns
5 match {
  case 0 => "zero"
  case 1 => "one"
  case _ => "many"
}
