package example

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

object Main extends App {
  def elaborate(gen: => RawModule, name: String): Unit = {
    val sv = ChiselStage.emitSystemVerilog(
      gen,
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "--lowering-options=disallowLocalVariables,noAlwaysComb,verifLabels,disallowPortDeclSharing",
      )
    )
    val svWriter = new PrintWriter(f"generated/$name.sv")
    svWriter.write(sv)
    svWriter.close()

    val chirrtl = ChiselStage.emitCHIRRTL(
      gen
    )
    val chirrtlWriter = new PrintWriter(f"generated/$name.fir")
    chirrtlWriter.write(chirrtl)
    chirrtlWriter.close()
  }

  elaborate(new Adder(2), "Adder")
  elaborate(new Const(2), "Const")
  elaborate(new Cache, "Cache")

  elaborate(new DecoupledMux, "DecoupledMux")
  elaborate(new DynamicIndexing, "DynamicIndexing")

  elaborate(new Fir(4), "Fir")
  elaborate(new GCD, "GCD")
  elaborate(new Top, "Hierarchy")
  elaborate(new NestedWhen, "NestedWhen")
// elaborate(new NestedBundleModule, "NestedBundleModule")
  elaborate(new PointerChasing, "PointerChasing")
  elaborate(new MyQueue(2), "MyQueue")
  elaborate(new RegFile, "RegFile")
  elaborate(new TestRegInit(2), "RegInit")
  elaborate(new RegVecInit(2), "RegVecInit")

  elaborate(new SinglePortSRAM(2), "SinglePortSRAM")
  elaborate(new OneReadOneWritePortSRAM(2), "OneReadOneWritePortSRAM")
  elaborate(new AggregateSRAM(2), "AggregateSRAM")
  elaborate(new DualReadSingleWritePortSRAM(2), "DualReadSingleWritePortSRAM")
}
