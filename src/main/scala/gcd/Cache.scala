import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class SRAM(val depth: Int, val width: Int) extends Module {
  val addrWidth = log2Ceil(depth)
  val io = IO(new Bundle {
    // Write port
    val writeEnable = Input(Bool())
    val writeAddr   = Input(UInt(addrWidth.W))
    val writeData   = Input(UInt(width.W))

    // Read port
    val readAddr    = Input(UInt(addrWidth.W))
    val readData    = Output(UInt(width.W))
  })

  // Define the memory
  val mem = SyncReadMem(depth, UInt(width.W))

  // Write operation
  when(io.writeEnable) {
    mem.write(io.writeAddr, io.writeData)
  }

  // Read operation
  io.readData := mem.read(io.readAddr, !io.writeEnable)
}

class Cache(
  val addrWidth: Int = 8,
  val dataWidth: Int = 8,
  val cacheSize: Int = 8
) extends Module {
  val indexWidth = log2Ceil(cacheSize)
  val tagWidth = addrWidth - indexWidth - 2 // assuming 4-byte words (2 bits for byte offset)

  val io = IO(new Bundle {
    // Request interface
    val reqAddr    = Input(UInt(addrWidth.W))
    val reqRead    = Input(Bool())
    val reqWrite   = Input(Bool())
    val reqData    = Input(UInt(dataWidth.W))

    // Response interface
    val respData   = Output(UInt(dataWidth.W))
    val respValid  = Output(Bool())

    // Memory interface (Main memory)
    val memAddr    = Output(UInt(addrWidth.W))
    val memRead    = Output(Bool())
    val memWrite   = Output(Bool())
    val memDataOut = Output(UInt(dataWidth.W))
    val memDataIn  = Input(UInt(dataWidth.W))
  })

  // Split the address
  val reqTag   = io.reqAddr(addrWidth-1, indexWidth+2)
  val reqIndex = io.reqAddr(indexWidth+1, 2)
  val reqOffset = io.reqAddr(1, 0) // Not used in this simple cache

  // Define cache SRAMs
  // Tag store: holds the tag for each cache line
  val tagMem = SyncReadMem(cacheSize, UInt(tagWidth.W))

  // Data store: holds the data for each cache line
  val dataMem = Module(new SRAM(depth = cacheSize, width = dataWidth))

  // State machine states
  val s_idle :: s_mem_read :: s_mem_write :: s_wait :: Nil = Enum(4)
  val state = RegInit(s_idle)

  // Registers to hold request information
  val reqTagReg    = Reg(UInt(tagWidth.W))
  val reqIndexReg  = Reg(UInt(indexWidth.W))
  val reqDataReg   = Reg(UInt(dataWidth.W))
  val writeBackTag = Reg(UInt(tagWidth.W))
  val writeBackData = Reg(UInt(dataWidth.W))

  // Read the tag
  val storedTag = tagMem.read(reqIndex, state === s_idle)
  val hit = (storedTag === reqTag) && (storedTag =/= 0.U) // Assuming tag 0.U is invalid

  // Connect dataMem
  dataMem.io.readAddr := reqIndex
  dataMem.io.writeEnable := false.B
  dataMem.io.writeAddr := 0.U
  dataMem.io.writeData := 0.U
  io.respValid := false.B
  io.respData := 0.U

  // Default memory interface signals
  io.memAddr := 0.U
  io.memRead := false.B
  io.memWrite := false.B
  io.memDataOut := 0.U

  switch(state) {
    is(s_idle) {
      reqTagReg := reqTag
      reqIndexReg := reqIndex
      reqDataReg := io.reqData

      when(hit) {
        when(io.reqRead) {
          // Cache hit on read
          io.respData := dataMem.io.readData
          io.respValid := true.B
        } .elsewhen(io.reqWrite) {
          // Cache hit on write
          dataMem.io.writeEnable := true.B
          dataMem.io.writeAddr := reqIndex
          dataMem.io.writeData := io.reqData
          // Optionally, update tag if needed
        }
      } .otherwise {
        // Cache miss
        state := s_mem_read
        io.memAddr := io.reqAddr
        io.memRead := true.B
      }
    }

    is(s_mem_read) {
      // Wait for memory read data
      state := s_wait
      io.memRead := false.B
    }

    is(s_wait) {
      // Assume memory read is done in one cycle for simplicity
      // In real scenarios, you need to handle wait states
      val memData = io.memDataIn
      // Write to cache
      tagMem.write(reqIndexReg, reqTagReg)
      dataMem.io.writeEnable := true.B
      dataMem.io.writeAddr := reqIndexReg
      dataMem.io.writeData := memData
      // Respond to read request
      io.respData := memData
      io.respValid := true.B
      state := s_idle
    }
  }
}

object Cache extends App {
  ChiselStage.emitSystemVerilogFile(
    new Cache,
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))
}
