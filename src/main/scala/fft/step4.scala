package example

import chisel3._
import chisel3.util._

// Added pipelined butterfly
class PipelinedButterfly extends Module {
  val io = IO(new Bundle {
    val in_a = Input(new ComplexNum)
    val in_b = Input(new ComplexNum)
    val twiddle = Input(new ComplexNum)
    val valid_in = Input(Bool())
    val out_a = Output(new ComplexNum)
    val out_b = Output(new ComplexNum)
    val valid_out = Output(Bool())
  })

  // Pipeline registers for multiplication
  val mul_reg = RegInit(0.U.asTypeOf(new ComplexNum))
  val a_reg = RegInit(0.U.asTypeOf(new ComplexNum))
  val valid_reg = RegInit(false.B)

  // Complex multiplication with registered outputs
  val prod_real = ((io.in_b.real * io.twiddle.real - io.in_b.imag * io.twiddle.imag) >> 14).asSInt
  val prod_imag = ((io.in_b.real * io.twiddle.imag + io.in_b.imag * io.twiddle.real) >> 14).asSInt

  when(io.valid_in) {
    mul_reg.real := prod_real
    mul_reg.imag := prod_imag
    a_reg := io.in_a
    valid_reg := true.B
  }.otherwise {
    valid_reg := false.B
  }

  // Addition and subtraction in second stage
  io.out_a.real := a_reg.real + mul_reg.real
  io.out_a.imag := a_reg.imag + mul_reg.imag
  io.out_b.real := a_reg.real - mul_reg.real
  io.out_b.imag := a_reg.imag - mul_reg.imag
  io.valid_out := valid_reg
}

// 8-point FFT with pipelined butterflies
class FFTStep4 extends Module {
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

  // Instantiate pipelined butterflies for first stage
  val stage1_butterflies = Seq.fill(4)(Module(new PipelinedButterfly))

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


  // Input registers
  val inputs = RegInit(VecInit(Seq.fill(8)(0.U.asTypeOf(new ComplexNum))))
  val valid = RegInit(false.B)

  when(io.valid_in) {
    inputs := io.in
    valid := true.B
  }.otherwise {
    valid := false.B
  }

  // First stage connections
  for (i <- 0 until 4) {
    stage1_butterflies(i).io.in_a := inputs(i)
    stage1_butterflies(i).io.in_b := inputs(i + 4)
    stage1_butterflies(i).io.valid_in := valid
  }
  stage1_butterflies(0).io.twiddle := W0
  stage1_butterflies(1).io.twiddle := W1
  stage1_butterflies(2).io.twiddle := W2
  stage1_butterflies(3).io.twiddle := W3

  // Stage 1 output registers
  val stage1_outputs = Reg(Vec(8, new ComplexNum))
  val stage1_valid = RegInit(false.B)

  when(stage1_butterflies(0).io.valid_out) {
    for (i <- 0 until 4) {
      stage1_outputs(i) := stage1_butterflies(i).io.out_a
      stage1_outputs(i + 4) := stage1_butterflies(i).io.out_b
    }
    stage1_valid := true.B
  }.otherwise {
    stage1_valid := false.B
  }

  // Second stage butterflies
  val stage2_butterflies = Seq.fill(4)(Module(new PipelinedButterfly))

  // Second stage connections
  for (i <- 0 until 2) {
    stage2_butterflies(i).io.in_a := stage1_outputs(i * 2)
    stage2_butterflies(i).io.in_b := stage1_outputs(i * 2 + 1)
    stage2_butterflies(i).io.twiddle := W0
    stage2_butterflies(i).io.valid_in := stage1_valid
  }
  for (i <- 2 until 4) {
    stage2_butterflies(i).io.in_a := stage1_outputs(i * 2)
    stage2_butterflies(i).io.in_b := stage1_outputs(i * 2 + 1)
    stage2_butterflies(i).io.twiddle := W2
    stage2_butterflies(i).io.valid_in := stage1_valid
  }

  // Final output assignment
  for (i <- 0 until 4) {
    io.out(i * 2) := stage2_butterflies(i).io.out_a
    io.out(i * 2 + 1) := stage2_butterflies(i).io.out_b
  }
  io.valid_out := stage2_butterflies(0).io.valid_out
}
