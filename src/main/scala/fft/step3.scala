package example

import chisel3._
import chisel3.util._

// Extended to 8-point FFT
class FFTStep3 extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(8, new ComplexNum))
    val valid_in = Input(Bool())
    val out = Output(Vec(8, new ComplexNum))
    val valid_out = Output(Bool())
  })

  def toFixed(d: Double): SInt = {
    val scale = (1 << 14).toDouble
    (d * scale).round.toInt.S(16.W)
  }

  // Complex operations (same as before)
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

  def complexMul(a: ComplexNum, b: ComplexNum): ComplexNum = {
    val result = Wire(new ComplexNum)
    result.real := ((a.real * b.real - a.imag * b.imag) >> 14).asSInt
    result.imag := ((a.real * b.imag + a.imag * b.real) >> 14).asSInt
    result
  }

  def butterfly(a: ComplexNum, b: ComplexNum, twiddle: ComplexNum): (ComplexNum, ComplexNum) = {
    val bw = complexMul(b, twiddle)
    (complexAdd(a, bw), complexSub(a, bw))
  }

  val inputs = RegInit(VecInit(Seq.fill(8)(0.U.asTypeOf(new ComplexNum))))
  val valid = RegInit(false.B)

  when(io.valid_in) {
    inputs := io.in
    valid := true.B
  }

  // Twiddle factors for 8-point FFT
  val W0 = Wire(new ComplexNum)
  W0.real := 1.S
  W0.imag := 0.S

  val W1 = Wire(new ComplexNum)
  W1.real := toFixed(0.7071)
  W1.imag := toFixed(-0.7071)

  val W2 = Wire(new ComplexNum)
  W2.real := 0.S
  W2.imag := toFixed(-1.0)

  val W3 = Wire(new ComplexNum)
  W3.real := toFixed(-0.7071)
  W3.imag := toFixed(-0.7071)

  // First stage - 4 butterflies with distance 4
  val (stage1_0, stage1_4) = butterfly(inputs(0), inputs(4), W0)
  val (stage1_1, stage1_5) = butterfly(inputs(1), inputs(5), W1)
  val (stage1_2, stage1_6) = butterfly(inputs(2), inputs(6), W2)
  val (stage1_3, stage1_7) = butterfly(inputs(3), inputs(7), W3)

  // Second stage - 2 groups of 2 butterflies with distance 2
  val (stage2_0, stage2_2) = butterfly(stage1_0, stage1_2, W0)
  val (stage2_1, stage2_3) = butterfly(stage1_1, stage1_3, W2)
  val (stage2_4, stage2_6) = butterfly(stage1_4, stage1_6, W0)
  val (stage2_5, stage2_7) = butterfly(stage1_5, stage1_7, W2)

  // Third stage - 4 butterflies with distance 1
  val (stage3_0, stage3_1) = butterfly(stage2_0, stage2_1, W0)
  val (stage3_2, stage3_3) = butterfly(stage2_2, stage2_3, W0)
  val (stage3_4, stage3_5) = butterfly(stage2_4, stage2_5, W0)
  val (stage3_6, stage3_7) = butterfly(stage2_6, stage2_7, W0)

  // Output assignment
  io.out(0) := stage3_0
  io.out(1) := stage3_1
  io.out(2) := stage3_2
  io.out(3) := stage3_3
  io.out(4) := stage3_4
  io.out(5) := stage3_5
  io.out(6) := stage3_6
  io.out(7) := stage3_7
  io.valid_out := valid
}
