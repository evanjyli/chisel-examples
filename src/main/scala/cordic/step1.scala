package example

import chisel3._
import chisel3.util._

// Basic CORDIC implementation with rotation mode only
class CordicStep1 extends Module {
  val io = IO(new Bundle {
    val x_in = Input(SInt(16.W))
    val y_in = Input(SInt(16.W))
    val z_in = Input(SInt(16.W))  // angle in radians (fixed-point)
    val valid_in = Input(Bool())
    
    val x_out = Output(SInt(16.W))
    val y_out = Output(SInt(16.W))
    val z_out = Output(SInt(16.W))
    val valid_out = Output(Bool())
  })

  // CORDIC constants (angles) stored as fixed-point values
  // Format: 2.14 fixed-point (2 integer bits, 14 fractional bits)
  val angles = VecInit(Seq(
    "b00110010010000".U, // atan(2^0) ≈ 0.7853981633974483
    "b00011101101011".U, // atan(2^-1) ≈ 0.4636476090008061
    "b00001111110101".U  // atan(2^-2) ≈ 0.24497866312686414
  ).map(_.asSInt))

  val iterations = 3
  
  val x = RegInit(VecInit(Seq.fill(iterations + 1)(0.S(16.W))))
  val y = RegInit(VecInit(Seq.fill(iterations + 1)(0.S(16.W))))
  val z = RegInit(VecInit(Seq.fill(iterations + 1)(0.S(16.W))))
  val valid = RegInit(VecInit(Seq.fill(iterations + 1)(false.B)))

  // Input stage
  x(0) := io.x_in
  y(0) := io.y_in
  z(0) := io.z_in
  valid(0) := io.valid_in

  // CORDIC iterations
  for (i <- 0 until iterations) {
    val shift_val = i.U
    
    when(valid(i)) {
      when(z(i) >= 0.S) {
        x(i + 1) := x(i) - (y(i) >> shift_val)
        y(i + 1) := y(i) + (x(i) >> shift_val)
        z(i + 1) := z(i) - angles(i)
      }.otherwise {
        x(i + 1) := x(i) + (y(i) >> shift_val)
        y(i + 1) := y(i) - (x(i) >> shift_val)
        z(i + 1) := z(i) + angles(i)
      }
    }.otherwise {
      x(i + 1) := x(i)
      y(i + 1) := y(i)
      z(i + 1) := z(i)
    }
    valid(i + 1) := valid(i)
  }

  // Output stage
  io.x_out := x(iterations)
  io.y_out := y(iterations)
  io.z_out := z(iterations)
  io.valid_out := valid(iterations)
}
