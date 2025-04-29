package example

import chisel3._
import chisel3.util._

// Step 3: Added priority queue for faster minimum node finding
class HuffmanCompressorStep3 extends Module {
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

  // Frequency counting (from step2)
  val freqMem = SyncReadMem(256, UInt(32.W), SyncReadMem.WriteFirst)
  val freqUpdateQueue = Module(new Queue(UInt(8.W), 16))

  // Optimization 2: Priority Queue for tree building
  class PriorityEntry extends Bundle {
    val freq = UInt(32.W)
    val idx = UInt(9.W)
  }
  val pq = Module(new Queue(new PriorityEntry, 512))
  
  // Tree building memory
  val treeNodes = Mem(512, UInt(72.W))
  val nodeCount = RegInit(0.U(9.W))

  // States with added tree initialization
  val sIdle :: sCountInit :: sCount :: sTreeInit :: sBuildTree :: sEncode :: sDone :: Nil = Enum(7)
  val state = RegInit(sIdle)

  // Default values
  io.input_ready := freqUpdateQueue.io.enq.ready && 
                    (state === sIdle || state === sCount)
  io.output_valid := false.B
  io.output := 0.U
  io.output_last := false.B
  io.done := state === sDone

  // Frequency counting logic (from step2)
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
        state := sTreeInit
      }
    }

    is(sTreeInit) {
      // Initialize priority queue with leaf nodes
      val initAddr = RegInit(0.U(8.W))
      val freq = freqMem.read(initAddr)
      when(freq > 0.U) {
        val entry = Wire(new PriorityEntry)
        entry.freq := freq
        entry.idx := nodeCount
        pq.io.enq.valid := true.B
        pq.io.enq.bits := entry

        // Create leaf node
        val nodeData = Cat(freq, initAddr, 0.U(24.W))
        treeNodes.write(nodeCount, nodeData)
        nodeCount := nodeCount + 1.U
      }
      
      when(initAddr === 255.U) {
        state := sBuildTree
      }.otherwise {
        initAddr := initAddr + 1.U
      }
    }

    is(sBuildTree) {
      // Build tree using priority queue
      when(pq.io.count >= 2.U) {
        val node1 = pq.io.deq.bits
        val node2 = pq.io.deq.bits
        pq.io.deq.ready := true.B

        // Create new internal node
        val newNode = Cat(
          (node1.freq + node2.freq),
          nodeCount,
          node1.idx(7, 0),
          node2.idx(7, 0),
          1.U(8.W),
          0.U(8.W)
        )
        treeNodes.write(nodeCount, newNode)

        // Add new node to priority queue
        val entry = Wire(new PriorityEntry)
        entry.freq := node1.freq + node2.freq
        entry.idx := nodeCount
        pq.io.enq.valid := true.B
        pq.io.enq.bits := entry

        nodeCount := nodeCount + 1.U
      }.elsewhen(pq.io.count === 1.U) {
        state := sEncode
      }
    }

    is(sEncode) {
      // Same encoding logic as step2
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
