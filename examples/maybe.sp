enum Maybe[A] {
  case Nothing
  case Just(x: A)
}

def safeDiv(x: Int, y: Int): Maybe[Int] =
  if (y == 0) Nothing else Just(x / y)

safeDiv(10, 2) match {
  case Nothing => -1
  case Just(v) => v
}
