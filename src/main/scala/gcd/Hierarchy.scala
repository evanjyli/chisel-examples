package hierarchy

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

class MyBundle extends Bundle {
  val in = Input(UInt(2.W))
  val out = Output(UInt(2.W))
}

class A extends Module {
  val io = IO(new MyBundle)
  io.out := RegNext(io.in)
}

class C extends Module {
  val io = IO(new MyBundle)
  io.out := RegNext(io.in)
}

class B extends Module {
  val io = IO(new MyBundle)

  val c = Module(new C)
  c.io.in := io.in

  io.out := RegNext(c.io.out)
}

class Top extends Module {
  val io = IO(new MyBundle)

  val a = Module(new A)
  a.io.in := io.in

  val b = Module(new B)
  b.io.in := a.io.out
  io.out := b.io.out
}


object Hierarchy extends App {
  ChiselStage.emitSystemVerilogFile(
    new Top,
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )

  val chirrtl = ChiselStage.emitCHIRRTL(
    new Top
  )
  val fileWriter = new PrintWriter("Hierarchy.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}


