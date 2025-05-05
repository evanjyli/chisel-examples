package example

import chisel3._
import chisel3.util._

// Optimized CORDIC with parallel processing and configurable precision
class CordicStep3(val width: Int = 16, val iterations: Int = 3) extends Module {
  val io = IO(new Bundle {
    val x_in = Input(SInt(width.W))
    val y_in = Input(SInt(width.W))
    val z_in = Input(SInt(width.W))
    val mode = Input(Bool())
    val valid_in = Input(Bool())
    
    val x_out = Output(SInt(width.W))
    val y_out = Output(SInt(width.W))
    val z_out = Output(SInt(width.W))
    val valid_out = Output(Bool())
    val busy = Output(Bool())
  })

  // Generate angle table based on width parameter
  def generateAngles(width: Int): Seq[SInt] = {
    val angles = (0 until iterations).map { i =>
      val angle = Math.atan(1.0 / (1 << i))
      val scale = (1L << (width - 2)).toDouble
      val fixed = (angle * scale).toLong
      (fixed & ((1L << width) - 1)).S(width.W)
    }
    angles
  }

  val angles = VecInit(generateAngles(width))

  // Parallel processing units
  class CordicStage extends Bundle {
    val x = SInt(width.W)
    val y = SInt(width.W)
    val z = SInt(width.W)
    val mode = Bool()
    val valid = Bool()
  }

  // Double-buffered pipeline for increased throughput
  val evenStages = RegInit(VecInit(Seq.fill((iterations + 1) / 2)(0.U.asTypeOf(new CordicStage))))
  val oddStages = RegInit(VecInit(Seq.fill(iterations / 2)(0.U.asTypeOf(new CordicStage))))
  
  // State machine for control
  val idle :: processing :: done :: Nil = Enum(3)
  val state = RegInit(idle)
  val cycleCount = RegInit(0.U(log2Ceil(iterations + 1).W))

  // Input handling
  when(state === idle && io.valid_in) {
    evenStages(0).x := io.x_in
    evenStages(0).y := io.y_in
    evenStages(0).z := io.z_in
    evenStages(0).mode := io.mode
    evenStages(0).valid := true.B
    state := processing
    cycleCount := 0.U
  }

  // Parallel CORDIC processing
  when(state === processing) {
    // Process even stages
    for (i <- 0 until (iterations + 1) / 2) {
      val stageIndex = i * 2
      when(evenStages(i).valid) {
        val shift_val = stageIndex.U
        val rotate_condition = Mux(evenStages(i).mode,
                                 evenStages(i).y >= 0.S,
                                 evenStages(i).z >= 0.S)

        when(rotate_condition) {
          if (i < oddStages.length) {
            oddStages(i).x := evenStages(i).x - (evenStages(i).y >> shift_val)
            oddStages(i).y := evenStages(i).y + (evenStages(i).x >> shift_val)
            oddStages(i).z := evenStages(i).z - angles(stageIndex)
          }
        }.otherwise {
          if (i < oddStages.length) {
            oddStages(i).x := evenStages(i).x + (evenStages(i).y >> shift_val)
            oddStages(i).y := evenStages(i).y - (evenStages(i).x >> shift_val)
            oddStages(i).z := evenStages(i).z + angles(stageIndex)
          }
        }
        if (i < oddStages.length) {
          oddStages(i).mode := evenStages(i).mode
          oddStages(i).valid := evenStages(i).valid
        }
      }
    }

    // Process odd stages
    for (i <- 0 until iterations / 2) {
      val stageIndex = i * 2 + 1
      when(oddStages(i).valid) {
        val shift_val = stageIndex.U
        val rotate_condition = Mux(oddStages(i).mode,
                                 oddStages(i).y >= 0.S,
                                 oddStages(i).z >= 0.S)

        when(rotate_condition) {
          if (i + 1 < evenStages.length) {
            evenStages(i + 1).x := oddStages(i).x - (oddStages(i).y >> shift_val)
            evenStages(i + 1).y := oddStages(i).y + (oddStages(i).x >> shift_val)
            evenStages(i + 1).z := oddStages(i).z - angles(stageIndex)
          }
        }.otherwise {
          if (i + 1 < evenStages.length) {
            evenStages(i + 1).x := oddStages(i).x + (oddStages(i).y >> shift_val)
            evenStages(i + 1).y := oddStages(i).y - (oddStages(i).x >> shift_val)
            evenStages(i + 1).z := oddStages(i).z + angles(stageIndex)
          }
        }
        if (i + 1 < evenStages.length) {
          evenStages(i + 1).mode := oddStages(i).mode
          evenStages(i + 1).valid := oddStages(i).valid
        }
      }
    }

    cycleCount := cycleCount + 1.U
    when(cycleCount === (iterations - 1).U) {
      state := done
    }
  }

  // Scale factor compensation with dynamic precision
  val scale_factor = (1.6467602538526541 * (1L << (width - 2))).toLong.S(width.W)
  
  // Output handling
  val final_stage = if (iterations % 2 == 0) oddStages.last else evenStages.last
  io.x_out := Mux(state === done,
                  (final_stage.x * scale_factor) >> (width - 2),
                  0.S)
  io.y_out := Mux(state === done,
                  (final_stage.y * scale_factor) >> (width - 2),
                  0.S)
  io.z_out := Mux(state === done, final_stage.z, 0.S)
  io.valid_out := state === done
  io.busy := state === processing

  // Reset state after completion
  when(state === done) {
    state := idle
  }
}
