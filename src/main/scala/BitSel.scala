package example

import chisel3._
import _root_.circt.stage.ChiselStage

class BitSel1 extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out_hi = Output(UInt(4.W))
    val out_lo = Output(UInt(4.W))
  })

  io.out_hi := io.in(7, 4)
  io.out_lo := io.in(3, 0)
}

class BitSel2 extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val bit3 = Output(Bool())
  })

  io.bit3 := io.in(3)
}
