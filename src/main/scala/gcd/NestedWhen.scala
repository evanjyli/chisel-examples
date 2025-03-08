package nestedwhen

import chisel3._
import chisel3.util.Decoupled
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

  when (io.sel === 0.U) {
    io.output := io.a
  } .elsewhen (io.sel === 1.U) {
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




class W extends Bundle {
  val a = UInt(2.W)
  val b = Flipped(Vec(2, UInt(3.W)))
}

class X extends Bundle {
  val c = Vec(2, new W)
  val d = Flipped(UInt(3.W))
  val e = Decoupled(UInt(2.W))
}

class Y extends Bundle {
  val e = new X
  val f = Vec(3, UInt(2.W))
}

class Z extends Bundle {
  val g = Vec(2, new Y)
  val h = Flipped(Vec(2, new Y))
}

class NestedBundleModule extends Module {
  val io = IO(new Z)

  val reg = RegNext(io.h)
  io.g <> reg
  
}

object NestedBundleModuleCHIRRTL extends App {
  val chirrtl = ChiselStage.emitCHIRRTL(
    new NestedBundleModule, args
  )

  val fileWriter = new PrintWriter("NestedBundleModule.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}
