package gcd

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage


class Counter(length: Int) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val out = Output(UInt(length.W))
  })
  dontTouch(io)
  val cntr = RegInit(0.U(length.W))
  cntr := cntr + 1.U
  io.out := cntr
}

object Counter extends App {
  ChiselStage.emitSystemVerilogFile(
    new Counter(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )
}
