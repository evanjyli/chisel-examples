package pointerchasing

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

class SRAM(val depth: Int, val width: Int) extends Module {
  val addrWidth = log2Ceil(depth)
  
  val io = IO(new Bundle {
    // Write Port
    val wr_en = Input(Bool())
    val wr_addr = Input(UInt(addrWidth.W))
    val wr_data = Input(UInt(width.W))

    // Read Port
    val rd_addr = Input(UInt(addrWidth.W))
    val rd_data = Output(UInt(width.W))
  })

  // Initialize the SRAM memory
  val mem = SyncReadMem(depth, UInt(width.W))

  // Write logic
  when(io.wr_en) {
    mem.write(io.wr_addr, io.wr_data)
  }

  // Read logic
  io.rd_data := mem.read(io.rd_addr, true.B)
}

class Chaser(val addrWidth: Int, val steps: Int) extends Module {
  val io = IO(new Bundle {
    // Control Signals
    val start = Input(Bool())
    val done = Output(Bool())

    val sram_rd_addr = Output(UInt(log2Ceil(steps).W))
    val sram_rd_data = Input(UInt(addrWidth.W))

    // Output of the final address after chasing
    val final_addr = Output(UInt(addrWidth.W))
  })

  // State Machine States
  val sIdle :: sChasing :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Registers to hold current address and step count
  val currentAddr = Reg(UInt(addrWidth.W))
  val step = RegInit(0.U(log2Ceil(steps + 1).W))

  io.done := (state === sDone)

  // SRAM read interface
  io.sram_rd_addr := 0.U
  io.final_addr := currentAddr

  switch(state) {
    is(sIdle) {
      when(io.start) {
        // Initialize current address (could be parameterized)
        currentAddr := "h00000000".U(addrWidth.W) // Starting address
        step := 0.U
        state := sChasing
      }
    }

    is(sChasing) {
      when(step < steps.U) {
        io.sram_rd_addr := currentAddr

        // Assume read data is available in the next cycle
        // Update current address with the data read from SRAM
        currentAddr := io.sram_rd_data
        step := step + 1.U
      } .otherwise {
        state := sDone
      }
    }

    is(sDone) {
      // Final address is available
      // Stay in done state until reset or new start
    }
  }
}

class PointerChasing extends Module {
  val addrWidth = 4
  val sramDepth = 8
  val chasingSteps = 4

  val io = IO(new Bundle {
    // Interface can be extended as needed
    val start = Input(Bool())
    val done = Output(Bool())
    val final_addr = Output(UInt(addrWidth.W))
  })

  // Instantiate SRAM
  val sram = Module(new SRAM(depth = sramDepth, width = addrWidth))

  // Initialize SRAM with a pointer chain
  // This example initializes SRAM with a simple linear chain: addr[i] = i + 1
  // You might want to replace this with your specific pointer chain
  val initDone = RegInit(false.B)
  val initCntr = RegInit(0.U(log2Ceil(sramDepth + 1).W))

  sram.io.wr_addr := DontCare
  sram.io.wr_data := DontCare
  sram.io.wr_en   := DontCare

  when (!initDone) {
    sram.io.wr_en := true.B
    sram.io.wr_addr := initCntr
    sram.io.wr_data := initCntr + 1.U

    initCntr := initCntr + 1.U
    when (initCntr === (sramDepth - 1).U) {
      initDone := true.B
    }
  } .otherwise {
    sram.io.wr_en := false.B
  }

  // Instantiate Pointer Chaser
  val chaser = Module(new Chaser(addrWidth, chasingSteps))

  // Connect Control Signals
  chaser.io.start := io.start && initDone
  io.done := chaser.io.done
  io.final_addr := chaser.io.final_addr

  // SRAM Read Port connected to Chaser's read port
  sram.io.rd_addr := chaser.io.sram_rd_addr

  // Connect SRAM read data to chaser
  chaser.io.sram_rd_data := sram.io.rd_data
}

object PointerChasing extends App {
  ChiselStage.emitSystemVerilogFile(
    new PointerChasing,
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))

  val chirrtl = ChiselStage.emitCHIRRTL(
    new PointerChasing, args
  )

  val fileWriter = new PrintWriter("PointerChasing.fir")
  fileWriter.write(chirrtl)
  fileWriter.close()
}
