package gcd

import chisel3._
import _root_.circt.stage.ChiselStage

class ShiftReg(length: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  io.out := util.ShiftRegister(io.in, length)
}

object ShiftReg extends App {
  ChiselStage.emitSystemVerilogFile(
    new ShiftReg(64),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))
}
