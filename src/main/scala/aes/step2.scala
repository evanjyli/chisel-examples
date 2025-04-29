package example

import chisel3._
import chisel3.util._

// Step 2: Added basic round key storage
class AESStep2 extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(16, UInt(8.W)))
    val key = Input(Vec(16, UInt(8.W)))
    val valid = Input(Bool())
    val ready = Output(Bool())
    val output = Output(Vec(16, UInt(8.W)))
    val done = Output(Bool())
  })

  // States
  val sIdle :: sSubBytes :: sShiftRows :: sMixColumns :: sAddKey :: sDone :: Nil = Enum(6)
  val state = RegInit(sIdle)

  // AES state array (4x4 matrix of bytes)
  val stateArray = Reg(Vec(16, UInt(8.W)))
  val roundCount = RegInit(0.U(4.W))

  // Added: Round key storage
  val roundKeys = Mem(11, Vec(16, UInt(8.W)))  // Store all round keys (initial + 10 rounds)
  val keyValid = RegInit(false.B)  // Indicates if key has been stored

  // Default values
  io.ready := state === sIdle
  io.done := state === sDone
  io.output := stateArray

  // SubBytes transformation (same as step1)
  def subBytes(byte: UInt): UInt = {
    val x = byte
    val x2 = x * x
    val x4 = x2 * x2
    val x8 = x4 * x4
    (x8 + x4 + x2 + x + 0x63.U) & 0xFF.U
  }

  // ShiftRows transformation (same as step1)
  def shiftRows(state: Vec[UInt]): Vec[UInt] = {
    val result = Wire(Vec(16, UInt(8.W)))
    // Row 0: no shift
    result(0) := state(0)
    result(4) := state(4)
    result(8) := state(8)
    result(12) := state(12)
    // Row 1: shift left by 1
    result(1) := state(5)
    result(5) := state(9)
    result(9) := state(13)
    result(13) := state(1)
    // Row 2: shift left by 2
    result(2) := state(10)
    result(6) := state(14)
    result(10) := state(2)
    result(14) := state(6)
    // Row 3: shift left by 3
    result(3) := state(15)
    result(7) := state(3)
    result(11) := state(7)
    result(15) := state(11)
    result
  }

  // MixColumns transformation (same as step1)
  def mixColumns(state: Vec[UInt]): Vec[UInt] = {
    val result = Wire(Vec(16, UInt(8.W)))
    for (col <- 0 until 4) {
      val c0 = state(col * 4)
      val c1 = state(col * 4 + 1)
      val c2 = state(col * 4 + 2)
      val c3 = state(col * 4 + 3)

      def times2(x: UInt): UInt = {
        val shifted = x << 1
        Mux(x(7), shifted ^ 0x1b.U, shifted)
      }

      def times3(x: UInt): UInt = times2(x) ^ x

      result(col * 4) := times2(c0) ^ times3(c1) ^ c2 ^ c3
      result(col * 4 + 1) := c0 ^ times2(c1) ^ times3(c2) ^ c3
      result(col * 4 + 2) := c0 ^ c1 ^ times2(c2) ^ times3(c3)
      result(col * 4 + 3) := times3(c0) ^ c1 ^ c2 ^ times2(c3)
    }
    result
  }

  // Main state machine
  switch(state) {
    is(sIdle) {
      when(io.valid) {
        when(!keyValid) {
          // Store initial key in all round key slots (for now)
          for(i <- 0 until 11) {
            roundKeys.write(i.U, io.key)
          }
          keyValid := true.B
        }
        // Initial round - XOR with first round key
        stateArray := VecInit.tabulate(16)(i => io.input(i) ^ roundKeys.read(0.U)(i))
        roundCount := 0.U
        state := sSubBytes
      }
    }

    is(sSubBytes) {
      for (i <- 0 until 16) {
        stateArray(i) := subBytes(stateArray(i))
      }
      state := sShiftRows
    }

    is(sShiftRows) {
      stateArray := shiftRows(stateArray)
      state := Mux(roundCount === 9.U, sAddKey, sMixColumns)
    }

    is(sMixColumns) {
      stateArray := mixColumns(stateArray)
      state := sAddKey
    }

    is(sAddKey) {
      // Use stored round key instead of direct key input
      val roundKey = roundKeys.read(roundCount + 1.U)
      for (i <- 0 until 16) {
        stateArray(i) := stateArray(i) ^ roundKey(i)
      }
      when(roundCount === 9.U) {
        state := sDone
      }.otherwise {
        roundCount := roundCount + 1.U
        state := sSubBytes
      }
    }

    is(sDone) {
      when(!io.valid) {
        state := sIdle
      }
    }
  }
}
