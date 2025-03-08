package adder

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage


class Adder(length: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(length.W))
    val b = Input(UInt(length.W))
    val c = Output(UInt(length.W))
  })
  val reg = Reg(UInt(length.W))
  reg := io.a + io.b
  io.c := reg
}

object Adder extends App {
  ChiselStage.emitSystemVerilogFile(
    new Adder(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )
}
