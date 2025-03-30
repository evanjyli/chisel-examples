package example

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter


class Aggregate extends Bundle {
  val x = UInt(2.W)
  val y = SInt(2.W)
}

class DecoupledMux extends Module {
  val io = IO(new Bundle {
    val a = Flipped(Decoupled(new Aggregate))
    val b = Flipped(Decoupled(new Aggregate))
    val c = Decoupled(new Aggregate)
  })

  io.c.bits := Mux(io.a.valid && io.c.ready, io.a.bits,
                 Mux(io.b.valid && io.c.ready, io.b.bits,
                   DontCare))
  io.c.valid := io.a.valid || io.b.valid
  io.a.ready := io.c.ready
  io.b.ready := io.c.ready && !io.a.valid
}
