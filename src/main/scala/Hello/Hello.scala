package how_sbt_work

/* Main method must have exact signature (Array[String])Unit and defined in object with name "main" */ 


object Helloscala extends App {
	println("Hello Scala")
}

object Hellosbt {
  def main(args: Array[String]): Unit = {
    println("Hello sbt")
  }
}
