enum List[A] {
  case Nil
  case ::(x: A, xs: List[A])
}

val myList = Cons(1, Cons(2, Cons(3, Nil)))

myList match {
  case x :: xs => x
  case Nil => 0
}
