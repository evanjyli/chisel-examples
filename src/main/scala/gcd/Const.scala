package gcd

import chisel3._
import chisel3.util._
import chisel3.util.Decoupled
import _root_.circt.stage.ChiselStage


class Const(length: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(UInt(8.W))
    val outSum = Output(UInt(8.W))
    val outAnd = Output(UInt(8.W))
    val outOr  = Output(UInt(8.W))
    val outXor = Output(UInt(8.W))
  })

  // Hardcoded constants
  val constant1 = 42.U(8.W)  // 8-bit unsigned constant
  val constant2 = 15.U(8.W)  // 8-bit unsigned constant
  val constant3 = "b10101010".U(8.W) // Hardcoded binary constant

  // Perform some operations using these constants
  val a = io.input + constant1 + constant2   // Sum of two constants
  val b = io.input + constant1 & constant3   // Bitwise AND
  val c = io.input + constant1 | constant3   // Bitwise OR
  val d = io.input + constant1 ^ constant3   // Bitwise XOR

  io.outSum := RegNext(a)
  io.outAnd := RegNext(b)
  io.outOr  := RegNext(c)
  io.outXor := RegNext(d)
}

object Const extends App {
  ChiselStage.emitSystemVerilogFile(
    new Const(2),
    firtoolOpts = Array("-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing")
  )
}
