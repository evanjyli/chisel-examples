package example

import chisel3._
import chisel3.util._

// Bundle for arithmetic operations
class ArithBundle extends Bundle {
  val a = Input(UInt(8.W))
  val b = Input(UInt(8.W))
  val c = Input(UInt(8.W))
  val sum = Output(UInt(8.W))      // combinational output
  val product = Output(UInt(16.W))  // combinational output
}

// Performs basic arithmetic operations
class ArithUnit extends Module {
  val io = IO(new ArithBundle)

  // Direct combinational paths
  io.sum := io.a +& io.b  // +& is add with carry
  io.product := io.b * io.c
}

// Bundle for comparison operations
class CompareBundle extends Bundle {
  val x = Input(UInt(8.W))
  val y = Input(UInt(8.W))
  val greater = Output(Bool())    // combinational output
  val equal = Output(Bool())      // combinational output
}

// Performs comparison operations
class CompareUnit extends Module {
  val io = IO(new CompareBundle)

  // Direct combinational paths
  io.greater := io.x > io.y
  io.equal := io.x === io.y
}

// Top-level module that combines arithmetic and comparison
class CombHierarchy extends Module {
  val io = IO(new Bundle {
    val in1 = Input(UInt(8.W))
    val in2 = Input(UInt(8.W))
    val in3 = Input(UInt(8.W))
    val result = Output(UInt(16.W))  // combinational output
    val valid = Output(Bool())       // combinational output
  })

  val arith = Module(new ArithUnit)
  val comp = Module(new CompareUnit)

  // Complex combinational paths through multiple modules
  arith.io.a := io.in1
  arith.io.b := io.in2
  arith.io.c := io.in3

  comp.io.x := RegNext(arith.io.sum)     // Compare sum with in3
  comp.io.y := io.in3

  // Final result depends on comparison
  io.result := Mux(comp.io.greater,
                   arith.io.product,    // if sum > in3, output product
                   arith.io.sum         // else output sum
              )

  // Valid when either greater or equal is true
  io.valid := comp.io.greater || comp.io.equal
}