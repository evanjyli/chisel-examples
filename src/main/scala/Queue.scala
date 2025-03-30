package example

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter


class BundleQueue extends Bundle {
  val a = UInt(3.W)
  val b = UInt(2.W)
}

class MyQueue(length: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new BundleQueue))
    val out = Decoupled(new BundleQueue)
  })
  val q = Module(new Queue(new BundleQueue, length))
  q.io.enq <> io.in
  io.out <> q.io.deq
}
