package gcd

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage


class TestRegInit(length: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(length.W))
    val b = Input(UInt(length.W))
    val c = Output(UInt(length.W))
  })
  val init = (1 << length) - 1
  val reg = RegInit(init.U(length.W))
  reg := io.a + io.b
  io.c := reg
}

object TestRegInit extends App {
  ChiselStage.emitSystemVerilogFile(
    new TestRegInit(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )
}
