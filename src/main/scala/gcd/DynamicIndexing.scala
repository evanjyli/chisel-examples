// See README.md for license details.

package dynamic_indexing

import chisel3._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter


class OutputBundle extends Bundle {
  val x = UInt(2.W)
  val y = SInt(3.W)
}

class DynamicIndexing extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(2.W))
    val out  = Output(new OutputBundle)
  })

  val arr_a = Reg(Vec(4, UInt(3.W)))
  val arr_b = Reg(Vec(8, new OutputBundle))
  io.out := arr_b(arr_a(io.addr))
}

/**
 * Generate Verilog sources and save it in file GCD.v
 */
object DynamicIndexing extends App {
  ChiselStage.emitSystemVerilogFile(
    new DynamicIndexing,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )

  val chirrtl = ChiselStage.emitCHIRRTL(
    new DynamicIndexing 
  )
  val fileWriter = new PrintWriter("DynamicIndexing.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}
