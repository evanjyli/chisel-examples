package reginit

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

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

class RegVecInit(length: Int) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(2.W))
    val wen  = Input(Bool())
    val wdata = Input(UInt(length.W))
    val out  = Output(UInt(length.W))
  })

  val regfile = RegInit(VecInit(Seq.fill(4)(2.U(length.W))))

  when (io.wen) {
    regfile(io.addr) := io.wdata
  }

  io.out := regfile(io.addr)
}

object TestRegInit extends App {
  ChiselStage.emitSystemVerilogFile(
    new TestRegInit(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )

  ChiselStage.emitSystemVerilogFile(
    new RegVecInit(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )

  val chirrtl = ChiselStage.emitCHIRRTL(
    new RegVecInit(2), args
  )

  val fileWriter = new PrintWriter("RegVecInit.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}
