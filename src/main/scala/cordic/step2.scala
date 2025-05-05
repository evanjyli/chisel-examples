package example

import chisel3._
import chisel3.util._

// Enhanced CORDIC with both rotation and vectoring modes, and pipelining
class CordicStep2 extends Module {
  val io = IO(new Bundle {
    val x_in = Input(SInt(16.W))
    val y_in = Input(SInt(16.W))
    val z_in = Input(SInt(16.W))
    val mode = Input(Bool())  // false: rotation, true: vectoring
    val valid_in = Input(Bool())
    
    val x_out = Output(SInt(16.W))
    val y_out = Output(SInt(16.W))
    val z_out = Output(SInt(16.W))
    val valid_out = Output(Bool())
  })

  val iterations = 3
  
  // CORDIC constants with improved precision
  val angles = VecInit(Seq(
    "b00110010010000".U, // atan(2^0)
    "b00011101101011".U, // atan(2^-1)
    "b00001111110101".U  // atan(2^-2)
  ).map(_.asSInt))

  // Pipeline stages
  class CordicStage extends Bundle {
    val x = SInt(16.W)
    val y = SInt(16.W)
    val z = SInt(16.W)
    val mode = Bool()
    val valid = Bool()
  }

  val stages = RegInit(VecInit(Seq.fill(iterations + 1)(0.U.asTypeOf(new CordicStage))))

  // Input stage
  stages(0).x := io.x_in
  stages(0).y := io.y_in
  stages(0).z := io.z_in
  stages(0).mode := io.mode
  stages(0).valid := io.valid_in

  // CORDIC iterations with pipelining
  for (i <- 0 until iterations) {
    val shift_val = i.U
    
    when(stages(i).valid) {
      val rotate_condition = Mux(stages(i).mode,
                               stages(i).y >= 0.S,    // vectoring mode
                               stages(i).z >= 0.S)    // rotation mode
      
      when(rotate_condition) {
        stages(i + 1).x := stages(i).x - (stages(i).y >> shift_val)
        stages(i + 1).y := stages(i).y + (stages(i).x >> shift_val)
        stages(i + 1).z := stages(i).z - angles(i)
      }.otherwise {
        stages(i + 1).x := stages(i).x + (stages(i).y >> shift_val)
        stages(i + 1).y := stages(i).y - (stages(i).x >> shift_val)
        stages(i + 1).z := stages(i).z + angles(i)
      }
    }.otherwise {
      stages(i + 1).x := stages(i).x
      stages(i + 1).y := stages(i).y
      stages(i + 1).z := stages(i).z
    }
    
    stages(i + 1).mode := stages(i).mode
    stages(i + 1).valid := stages(i).valid
  }

  // Output stage
  io.x_out := stages(iterations).x
  io.y_out := stages(iterations).y
  io.z_out := stages(iterations).z
  io.valid_out := stages(iterations).valid

  // Scale factor compensation (K ≈ 0.6072529350088812)
  // Fixed-point representation of 1/K
  val scale_factor = "b01101001100110".U.asSInt  // ≈ 1.6467602538526541
  io.x_out := (stages(iterations).x * scale_factor) >> 14
  io.y_out := (stages(iterations).y * scale_factor) >> 14
}
