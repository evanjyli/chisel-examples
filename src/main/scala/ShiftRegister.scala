package example

import chisel3._
import _root_.circt.stage.ChiselStage
import chisel3.experimental.hierarchy._

class ProcessorBundle(width: Int) extends Bundle {
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
}

@instantiable
class Processor(width: Int) extends Module {
  @public val io = IO(new ProcessorBundle(width))

  io.out := io.in + 1.U
}

class TopModule extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out1 = Output(UInt(8.W))
    val out2 = Output(UInt(8.W))
  })

  // Define the Processor module once
  val processorDef = Definition(new Processor(8))

  // Instantiate two instances from the same definition
  val procs = Seq.fill(2)(Instance(processorDef))

  // Access the I/O ports through `.get`
  procs(0).io.in := io.in
  procs(1).io.in := io.in

  io.out1 := procs(0).io.out
  io.out2 := procs(1).io.out
}

object ShiftReg extends App {
  ChiselStage.emitSystemVerilogFile(
    new TopModule,
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing"))
}
