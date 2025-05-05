package example

import chisel3._
import chisel3.util._

// Added butterfly operation
class FFTStep2 extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(4, new ComplexNum))
    val valid_in = Input(Bool())
    val out = Output(Vec(4, new ComplexNum))
    val valid_out = Output(Bool())
  })

  def toFixed(d: Double): SInt = {
    val scale = (1 << 14).toDouble
    (d * scale).round.toInt.S(16.W)
  }

  // Complex operations (same as step1)
  def complexAdd(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := a.real + b.real
    result.imag := a.imag + b.imag
    result
  }

  def complexSub(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := a.real - b.real
    result.imag := a.imag - b.imag
    result
  }

  // New: Basic complex multiplication
  def complexMul(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := ((a.real * b.real - a.imag * b.imag) >> 14).asSInt
    result.imag := ((a.real * b.imag + a.imag * b.real) >> 14).asSInt
    result
  }

  // New: Butterfly operation
  def butterfly(a: ComplexNum, b: ComplexNum, twiddle: ComplexNum): (ComplexNum, ComplexNum) = {
    val bw = complexMul(b, twiddle)
    (complexAdd(a, bw), complexSub(a, bw))
  }

  val inputs = RegInit(VecInit(Seq.fill(4)(0.U.asTypeOf(new ComplexNum))))
  val valid = RegInit(false.B)

  when(io.valid_in) {
    inputs := io.in
    valid := true.B
  }

  // Twiddle factor for 4-point FFT (W_4^1 = -j)
  val twiddle = Wire(new ComplexNum)
  twiddle.real := 0.S
  twiddle.imag := toFixed(-1.0)

  val one = Wire(new ComplexNum)
  one.real := 1.S
  one.imag := 0.S


  // Use butterfly operations

  val (stage1_0, stage1_2) = butterfly(inputs(0), inputs(2), one)
  val (stage1_1, stage1_3) = butterfly(inputs(1), inputs(3), twiddle)

  io.out(0) := stage1_0
  io.out(1) := stage1_1
  io.out(2) := stage1_2
  io.out(3) := stage1_3
  io.valid_out := valid
}
