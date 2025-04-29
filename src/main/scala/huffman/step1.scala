package example

import chisel3._
import chisel3.util._

// Step 1: Basic single-cycle implementation of Huffman compression
// This version processes one byte at a time with a simple state machine
class HuffmanCompressorStep1 extends Module {
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

  // States for main control FSM
  val sIdle :: sCount :: sBuildTree :: sEncode :: sDone :: Nil = Enum(5)
  val state = RegInit(sIdle)

  // Frequency counting memory
  val freqMem = Mem(256, UInt(32.W))
  val freqAddr = RegInit(0.U(8.W))
  val currentFreq = RegInit(0.U(32.W))

  // Tree building
  // freq(32) + value(8) + left(8) + right(8) + valid(8) + parent(8)
  val treeNodes = Mem(512, UInt(72.W))
  val nodeCount = RegInit(0.U(9.W))
  val minNode1Idx = RegInit(0.U(9.W))
  val minNode2Idx = RegInit(0.U(9.W))
  val minFreq1 = RegInit(UInt(32.W), "hFFFFFFFF".U)
  val minFreq2 = RegInit(UInt(32.W), "hFFFFFFFF".U)

  // Encoding
  val codewordMem = Mem(256, UInt(32.W))
  val lengthMem = Mem(256, UInt(6.W))
  val currentByte = RegInit(0.U(8.W))

  // Default values
  io.input_ready := state === sIdle || state === sCount
  io.output_valid := false.B
  io.output := 0.U
  io.output_last := false.B
  io.done := state === sDone

  // Main state machine
  switch(state) {
    is(sIdle) {
      when(io.start) {
        // Initialize memories
        for (i <- 0 until 256) {
          freqMem.write(i.U, 0.U)
        }
        state := sCount
        freqAddr := 0.U
      }
    }

    is(sCount) {
      when(io.input_valid) {
        // Read current frequency
        currentFreq := freqMem.read(io.input)
        // Update frequency
        freqMem.write(io.input, currentFreq + 1.U)
      }.otherwise {
        state := sBuildTree
        nodeCount := 0.U
        // Initialize tree building
        for (i <- 0 until 256) {
          when(freqMem.read(i.U) > 0.U) {
            // Create leaf node: [freq|value|left|right|valid|parent]
            val nodeData = Cat(freqMem.read(i.U), i.U(8.W), 0.U(8.W), 0.U(8.W), 1.U(8.W), 0.U(8.W))
            treeNodes.write(nodeCount, nodeData)
            nodeCount := nodeCount + 1.U
          }
        }
      }
    }

    is(sBuildTree) {
      // Find two nodes with minimum frequencies
      minFreq1 := "hFFFFFFFF".U
      minFreq2 := "hFFFFFFFF".U
      for (i <- 0 until 512) {
        val node = treeNodes.read(i.U)
        val freq = node(71, 40)  // Extract frequency
        val valid = node(15, 8)  // Extract valid bit
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
        // Tree is complete
        state := sEncode
      }.otherwise {
        // Create new internal node
        val newNode = Cat(
          (minFreq1 + minFreq2),           // frequency
          nodeCount,                        // value (internal node)
          minNode1Idx(7, 0),               // left child
          minNode2Idx(7, 0),               // right child
          1.U(8.W),                        // valid
          0.U(8.W)                         // parent
        )
        treeNodes.write(nodeCount, newNode)
        // Mark children as invalid
        val invalidate = Cat(
          treeNodes.read(minNode1Idx)(71, 16),  // Keep all but valid/parent
          0.U(8.W),                             // invalid
          nodeCount(7, 0)                       // new parent
        )
        treeNodes.write(minNode1Idx, invalidate)
        treeNodes.write(minNode2Idx, invalidate)
        nodeCount := nodeCount + 1.U
      }
    }

    is(sEncode) {
      // Simple encoding - traverse tree for each input byte
      when(io.input_valid) {
        currentByte := io.input
        var code = RegInit(0.U(32.W))
        var length = RegInit(0.U(6.W))
        var node = treeNodes.read(nodeCount - 1.U)  // Start from root

        when (node(39, 32) =/= currentByte) {  // While not at leaf
          when(currentByte < node(39, 32)) {
            code := Cat(code, 0.U(1.W))
            node := treeNodes.read(node(31, 24))  // Go left
          }.otherwise {
            code := Cat(code, 1.U(1.W))
            node := treeNodes.read(node(23, 16))  // Go right
          }
          length := length + 1.U
        }
        codewordMem.write(currentByte, code)
        lengthMem.write(currentByte, length)
        io.output := code
        io.output_valid := true.B
      }.otherwise {
        state := sDone
        io.output_last := true.B
      }
    }
  }
}
