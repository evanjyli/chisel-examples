package gcd

import chisel3._
import _root_.circt.stage.ChiselStage

class Fir(length: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val valid = Input(Bool())
    val out = Output(UInt(4.W))
    val consts = Input(Vec(length, UInt(4.W)))
  })

  // Such concision! You'll learn what all this means later.
  val taps = Seq(io.in) ++ Seq.fill(io.consts.length - 1)(RegInit(0.U(4.W)))
  taps.zip(taps.tail).foreach { case (a, b) => when (io.valid) { b := a } }

  io.out := taps.zip(io.consts).map { case (a, b) => a * b }.reduce(_ + _)
}

object Fir extends App {
  ChiselStage.emitSystemVerilogFile(
    new Fir(4),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
