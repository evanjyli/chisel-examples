package gcd

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage


class MyBundle extends Bundle {
  val a = UInt(3.W)
  val b = UInt(2.W)
}

class MyQueue(length: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new MyBundle))
    val out = Decoupled(new MyBundle)
  })
  val q = Module(new Queue(new MyBundle, length))
  q.io.enq <> io.in
  io.out <> q.io.deq
}

object MyQueue extends App {
  ChiselStage.emitSystemVerilogFile(
    new MyQueue(4),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )
}
