```
Copyright (C) 2020 by phantom
Email: admin@phvntom.tech
This file is under MIT License, see http://phvntom.tech/LICENSE.txt
Recording some scala features Here

[Referencs]
    https://docs.scala-lang.org/tour/tour-of-scala.html
    https://docs.scala-lang.org/overviews/scala-book/introduction.html
```
### Trait
Traits are used to share interfaces and fields between classes. They are similar to Java 8s interfaces. Classes and objects can extend traits, but traits cannot be instantiated and therefore have no parameters.


### Abtract Class
But because traits are so powerful, you rarely need to use an abstract class. In fact, you only need to use an abstract class when:

- You want to create a base class that requires constructor arguments
- Your Scala code will be called from Java code

### Case Class
Case classes are like regular classes with a few key differences which we will go over. 

Case classes are good for modeling immutable data. 
A minimal case class requires the keywords `case class`, an identifier, and a parameter list (which may be empty).
** When you create a case class with parameters, the parameters are public `val`s. **

```scala
case class Book(isbn: String)
val frankenstein = Book("978-0486282114")
println(frankenstein.isbn)
```
Notice how the keyword `new` was not used to instantiate the Book case class. 
This is because case classes have an `apply` method by default which takes care of object construction.

### Singleton Object
> In this section, object doesn't mean the instance of a class

A singleton object is a class that has exactly one instance. 
It is created lazily when it is referenced, like a lazy val.

The definition of an object looks like a class, but uses the keyword `object`. 
The methods in object can be imported from anywhere in the program. 
Creating utility methods like this is a common use case for singleton objects.

An object with the same name as a class is called a `companion object`. 
Conversely, the class is the object’s `companion class`. 
A companion class or object can access the private members of its companion. 
Use a companion object for methods and values which are not specific to instances of the companion class.

**Note**: If a class or object has a companion, both must be defined in the same file.

```scala
import scala.math._

case class Circle(radius: Double) {
  /* Need import before using */
  import Circle._

  def area: Double = calculateArea(radius)
}

object Circle {
  private def calculateArea(radius: Double): Double = Pi * pow(radius, 2.0)
}

val circle1 = Circle(5.0)

circle1.area
```
