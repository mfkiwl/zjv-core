package rv64_3stage

import common._

object option {
  def usage() = println(
    """ Help
      | Usage: sbt runMain rv64_3se.main [option]
      | Option:
      |   --isa <value>
      |     Set ISA set, the default value is RV64IM
      |   --priv <value>
      |     Set privilage mode, the default value is M
      |   --vm <value>
      |     Set virtual memory, the default value is None
      |   --board <value>
      |     Set FPGA board
      |""".stripMargin
  )

  def argParser (args: Array[String]) :Unit = {
    val arglist = args.toList

    def parse (remain: List[String]): Unit = {
      remain match {
        case "--isa" :: isa :: tail => {
          if (isa.substring(0,2) != "RV") { println(s"Bad ISA set $isa"); usage() }
          else { phvntomConfig.isa = isa; parse(tail) }
        }
        case "--priv" :: priv :: tail => {
          if (!priv.contains("M")) { println(s"Bad privilage mode $priv"); usage() }
          else { phvntomConfig.priv = priv; parse(tail) }
        }
        case "--vm" :: vm :: tail => phvntomConfig.vm = vm; parse(tail)
        case "--board" :: board :: tail => phvntomConfig.fpga = true; phvntomConfig.board = board; parse(tail)
        case op :: tail => usage()
        case Nil =>
      }
    }

    parse(arglist)
  }

}


object main extends App {
  option.argParser(args)

  println(phvntomConfig.isa)
  println(phvntomConfig.priv)
  println(phvntomConfig.vm)
  println(phvntomConfig.board)
}
