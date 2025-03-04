package gcd

import chisel3._
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

class NestedWhen extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val c = Input(UInt(2.W))
    val sel = Input(UInt(2.W))
    val output = Output(UInt(2.W))
  })

  when (io.output === 0.U) {
    io.output := io.a
  } .elsewhen (io.output === 1.U) {
    io.output := io.b
  } .otherwise {
    io.output := io.c
  }
}

object NestedWhen extends App {
  val chirrtl = ChiselStage.emitCHIRRTL(
    new NestedWhen, args
  )

  val fileWriter = new PrintWriter("NestedWhen.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}
