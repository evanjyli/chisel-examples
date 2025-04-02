package example

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

class AggregateBundle extends Bundle {
  val a = UInt(2.W)
  val b = SInt(3.W)
}

class AggregateSRAM(width: Int) extends Module {
  val io = IO(new Bundle {
    val raddr = Input(UInt(3.W))
    val rdata = Output(Vec(4, new AggregateBundle))

    val wen = Input(Bool())
    val waddr = Input(UInt(3.W))
    val wdata = Input(Vec(4, new AggregateBundle))
    val wmask = Input(Vec(4, Bool()))
  })

  val mem = SyncReadMem(8, Vec(4, new AggregateBundle))
  when (io.wen) {
    mem.write(io.waddr, io.wdata, io.wmask)
  }

  io.rdata := mem.read(io.raddr, !io.wen)
}

class DualReadSingleWritePortSRAM(width: Int) extends Module {
  val io = IO(new Bundle {
    val raddr_0 = Input(UInt(3.W))
    val raddr_1 = Input(UInt(3.W))
    val rdata_0 = Output(Vec(4, UInt(width.W)))
    val rdata_1 = Output(Vec(4, UInt(width.W)))

    val wen = Input(Bool())
    val waddr = Input(UInt(3.W))
    val wdata = Input(Vec(4, UInt(width.W)))
    val wmask = Input(Vec(4, Bool()))
  })

  val mem = SyncReadMem(8, Vec(4, UInt(width.W)))
  when (io.wen) {
    mem.write(io.waddr, io.wdata, io.wmask)
  }

  io.rdata_0 := mem.read(io.raddr_0, !io.wen)
  io.rdata_1 := mem.read(io.raddr_1, !io.wen)
}

class OneReadOneReadWritePortSRAM(width: Int) extends Module {
  val io = IO(new Bundle {
    val raddr_0 = Input(UInt(3.W))
    val raddr_1 = Input(UInt(3.W))
    val rdata_0 = Output(Vec(4, UInt(width.W)))
    val rdata_1 = Output(Vec(4, UInt(width.W)))
    val ren = Input(Bool())

    val wen = Input(Bool())
    val waddr = Input(UInt(3.W))
    val wdata = Input(Vec(4, UInt(width.W)))
    val wmask = Input(Vec(4, Bool()))
  })

  val mem = SyncReadMem(8, Vec(4, UInt(width.W)))
  when (io.wen) {
    mem.write(io.waddr, io.wdata, io.wmask)
  }

  io.rdata_0 := mem.read(io.raddr_0, !io.wen)
  io.rdata_1 := mem.read(io.raddr_1, !io.wen && !io.ren)
}
