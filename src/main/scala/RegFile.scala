package example

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

class RegFile extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(4.W))
    val data = Output(UInt(2.W))
  })
  val regfile = Reg(Vec(16, UInt(2.W)))
  io.data := regfile(io.addr)
}

class NestedIndex extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(4.W))
    val data = Output(UInt(3.W))
  })
  val regfile_1 = Reg(Vec(16, UInt(2.W)))
  val regfile_2 = Reg(Vec(4, UInt(3.W)))
  io.data := regfile_2(regfile_1(io.addr))
}
