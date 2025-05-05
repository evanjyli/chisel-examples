package example

import chisel3._
import chisel3.util._

// Basic complex number representation
class ComplexNum extends Bundle {
  val real = SInt(16.W)
  val imag = SInt(16.W)
}

// Initial 4-point FFT implementation
class FFTStep1 extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(4, new ComplexNum))
    val valid_in = Input(Bool())
    val out = Output(Vec(4, new ComplexNum))
    val valid_out = Output(Bool())
  })

  // Helper function to convert double to fixed-point SInt
  def toFixed(d: Double): SInt = {
    val scale = (1 << 14).toDouble
    (d * scale).round.toInt.S(16.W)
  }

  // Basic complex addition
  def complexAdd(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := a.real + b.real
    result.imag := a.imag + b.imag
    result
  }

  // Basic complex subtraction
  def complexSub(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := a.real - b.real
    result.imag := a.imag - b.imag
    result
  }

  // Register for input data
  val inputs = RegInit(VecInit(Seq.fill(4)(0.U.asTypeOf(new ComplexNum))))
  val valid = RegInit(false.B)

  // Store inputs when valid
  when(io.valid_in) {
    inputs := io.in
    valid := true.B
  }

  // Simple 4-point FFT computation
  // Only implements additions and subtractions for now
  val stage1_0 = complexAdd(inputs(0), inputs(2))
  val stage1_1 = complexAdd(inputs(1), inputs(3))
  val stage1_2 = complexSub(inputs(0), inputs(2))
  val stage1_3 = complexSub(inputs(1), inputs(3))

  // Output assignment
  io.out(0) := stage1_0
  io.out(1) := stage1_1
  io.out(2) := stage1_2
  io.out(3) := stage1_3
  io.valid_out := valid
}
