package example

import chisel3._
import chisel3.util._

// Step 2: Optimized frequency counting with dual-port memory and buffering
class HuffmanCompressorStep2 extends Module {
  val io = IO(new Bundle {
    val input = Input(UInt(8.W))
    val input_valid = Input(Bool())
    val input_ready = Output(Bool())
    
    val output = Output(UInt(32.W))
    val output_valid = Output(Bool())
    val output_ready = Input(Bool())
    val output_last = Output(Bool())
    
    val start = Input(Bool())
    val done = Output(Bool())
  })

  // States
  val sIdle :: sCountInit :: sCount :: sBuildTree :: sEncode :: sDone :: Nil = Enum(6)
  val state = RegInit(sIdle)

  // Optimization 1: Dual-port frequency memory and buffering
  val freqMem = SyncReadMem(256, UInt(32.W), SyncReadMem.WriteFirst)
  val freqUpdateQueue = Module(new Queue(UInt(8.W), 16))  // Buffer frequency updates

  // Tree building (same as step1)
  val treeNodes = Mem(512, UInt(72.W))  // freq(32) + value(8) + left(8) + right(8) + valid(8) + parent(8)
  val nodeCount = RegInit(0.U(9.W))
  val minNode1Idx = RegInit(0.U(9.W))
  val minNode2Idx = RegInit(0.U(9.W))
  val minFreq1 = RegInit(UInt(32.W), "hFFFFFFFF".U)
  val minFreq2 = RegInit(UInt(32.W), "hFFFFFFFF".U)


  // Default values
  io.input_ready := freqUpdateQueue.io.enq.ready && 
                    (state === sIdle || state === sCount)
  io.output_valid := false.B
  io.output := 0.U
  io.output_last := false.B
  io.done := state === sDone

  // Frequency counting logic with buffering
  freqUpdateQueue.io.enq.valid := io.input_valid && io.input_ready
  freqUpdateQueue.io.enq.bits := io.input

  when(freqUpdateQueue.io.deq.valid) {
    val addr = freqUpdateQueue.io.deq.bits
    val currentFreq = freqMem.read(addr)
    freqMem.write(addr, currentFreq + 1.U)
    freqUpdateQueue.io.deq.ready := true.B
  }.otherwise {
    freqUpdateQueue.io.deq.ready := false.B
  }

  // Main state machine
  switch(state) {
    is(sIdle) {
      when(io.start) {
        state := sCountInit
      }
    }

    is(sCountInit) {
      // Initialize frequency memory
      val initAddr = RegInit(0.U(8.W))
      freqMem.write(initAddr, 0.U)
      when(initAddr === 255.U) {
        state := sCount
      }.otherwise {
        initAddr := initAddr + 1.U
      }
    }

    is(sCount) {
      when(!io.input_valid && freqUpdateQueue.io.count === 0.U) {
        state := sBuildTree
        nodeCount := 0.U
        // Initialize tree building
        for (i <- 0 until 256) {
          when(freqMem.read(i.U) > 0.U) {
            val nodeData = Cat(freqMem.read(i.U), i.U(8.W), 0.U(8.W), 0.U(8.W), 1.U(8.W), 0.U(8.W))
            treeNodes.write(nodeCount, nodeData)
            nodeCount := nodeCount + 1.U
          }
        }
      }
    }

    is(sBuildTree) {
      // Same tree building logic as step1
      minFreq1 := "hFFFFFFFF".U
      minFreq2 := "hFFFFFFFF".U
      for (i <- 0 until 512) {
        val node = treeNodes.read(i.U)
        val freq = node(71, 40)
        val valid = node(15, 8)
        when(valid === 1.U) {
          when(freq < minFreq1) {
            minFreq2 := minFreq1
            minNode2Idx := minNode1Idx
            minFreq1 := freq
            minNode1Idx := i.U
          }.elsewhen(freq < minFreq2) {
            minFreq2 := freq
            minNode2Idx := i.U
          }
        }
      }

      when(minFreq2 === "hFFFFFFFF".U) {
        state := sEncode
      }.otherwise {
        val newNode = Cat(
          (minFreq1 + minFreq2),
          nodeCount,
          minNode1Idx(7, 0),
          minNode2Idx(7, 0),
          1.U(8.W),
          0.U(8.W)
        )
        treeNodes.write(nodeCount, newNode)

        val invalidate = Cat(
          treeNodes.read(minNode1Idx)(71, 16),
          0.U(8.W),
          nodeCount(7, 0)
        )
        treeNodes.write(minNode1Idx, invalidate)
        treeNodes.write(minNode2Idx, invalidate)

        nodeCount := nodeCount + 1.U
      }
    }

    is(sEncode) {
      // Same encoding logic as step1
      when(io.input_valid) {
        var code = RegInit(0.U(32.W))
        var length = RegInit(0.U(6.W))
        var node = treeNodes.read(nodeCount - 1.U)

        when (node(39, 32) =/= io.input) {
          when(io.input < node(39, 32)) {
            code := Cat(code, 0.U(1.W))
            node := treeNodes.read(node(31, 24))
          }.otherwise {
            code := Cat(code, 1.U(1.W))
            node := treeNodes.read(node(23, 16))
          }
          length := length + 1.U
        }
        io.output := code
        io.output_valid := true.B
      }.otherwise {
        state := sDone
        io.output_last := true.B
      }
    }
  }
}
