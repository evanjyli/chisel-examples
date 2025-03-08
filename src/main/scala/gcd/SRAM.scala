package sram

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter


class OneReadOneWritePortSRAM(width: Int) extends Module {
  val io = IO(new Bundle {
    val ren = Input(Bool())
    val raddr = Input(UInt(3.W))
    val rdata = Output(Vec(4, UInt(width.W)))
    val wen = Input(Bool())
    val waddr = Input(UInt(3.W))
    val wdata = Input(Vec(4, UInt(width.W)))
    val wmask = Input(Vec(4, Bool()))
  })

  // Create a 32-bit wide memory that is byte-masked
  val mem = SyncReadMem(8, Vec(4, UInt(width.W)))
  when (io.wen) {
    mem.write(io.waddr, io.wdata, io.wmask)
  }
  io.rdata := mem.read(io.raddr, io.ren)
}


class SinglePortSRAM(width: Int) extends Module {
  val io = IO(new Bundle {
    val raddr = Input(UInt(3.W))
    val rdata = Output(Vec(4, UInt(width.W)))

    val wen = Input(Bool())
    val waddr = Input(UInt(3.W))
    val wdata = Input(Vec(4, UInt(width.W)))
    val wmask = Input(Vec(4, Bool()))
  })

  val mem = SyncReadMem(8, Vec(4, UInt(width.W)))
  when (io.wen) {
    mem.write(io.waddr, io.wdata, io.wmask)
  }

  io.rdata := mem.read(io.raddr, !io.wen)
}


object SRAM extends App {
  ChiselStage.emitSystemVerilogFile(
    new SinglePortSRAM(2),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))

  val chirrtl = ChiselStage.emitCHIRRTL(
    new SinglePortSRAM(2)
  )
  val fileWriter = new PrintWriter("SinglePortSRAM.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()

  ChiselStage.emitSystemVerilogFile(
    new OneReadOneWritePortSRAM(2),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))

  val chirrtl2 = ChiselStage.emitCHIRRTL(
    new OneReadOneWritePortSRAM(2)
  )
  val fileWriter2 = new PrintWriter("OneReadOneWritePortSRAM.fir")
  fileWriter2.write(chirrtl2)
  fileWriter2.close()
}
