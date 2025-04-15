package example

import chisel3._
import chisel3.util.Decoupled
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage
import java.io.PrintWriter

class NestedWhen extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val c = Input(UInt(2.W))
    val sel = Input(UInt(2.W))
    val output = Output(UInt(2.W))
  })

  when (io.sel === 0.U) {
    io.output := io.a
  } .elsewhen (io.sel === 1.U) {
    io.output := io.b
  } .otherwise {
    io.output := io.c
  }
}


class LCS1 extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })

  val w = Wire(UInt(8.W))
  w := 1.U
  w := 2.U  // Should win
  io.out := w  // Expect 2
}

class LCS2 extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out = Output(UInt(8.W))
  })

  val reg = RegInit(0.U(8.W))
  when(io.cond) {
    reg := 42.U
  }
  reg := 99.U // Unconditional write — should override when block

  io.out := reg  // Always expect 99
}

class LCS3 extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  val result = Wire(UInt(8.W))
  result := 0.U

  when(io.in === 1.U) {
    result := 1.U
    when(io.in === 1.U) {
      result := 2.U  // This should win if condition is true
    }
  }

  io.out := result  // Expect 2 if io.in == 1, otherwise 0
}

class LCS4 extends Module {
  val io = IO(new Bundle {
    val sel = Input(UInt(2.W))
    val out = Output(UInt(8.W))
  })

  val out = Wire(UInt(8.W))
  out := 0.U

  when(io.sel === 0.U) { out := 10.U }
  when(io.sel === 1.U) { out := 20.U }
  when(io.sel === 2.U) { out := 30.U }

  io.out := out
}

class LCS5 extends Module {
  val io = IO(new Bundle {
    val a = Input(Bool())
    val b = Input(Bool())
    val c = Input(Bool())
    val out = Output(UInt(8.W))
  })

  val w = WireDefault(0.U(8.W))

  when(io.a) {
    w := 1.U
    when(io.b) {
      w := 2.U
      when(io.c) {
        w := 3.U
      }
    }
  }

  io.out := w
}

class LCS6 extends Module {
  val io = IO(new Bundle {
    val sel = Input(UInt(2.W))
    val out = Output(new Bundle {
      val x = UInt(8.W)
      val y = UInt(8.W)
    })
  })

  val temp = Wire(new Bundle {
    val x = UInt(8.W)
    val y = UInt(8.W)
  })

  temp.x := 0.U
  temp.y := 0.U

  when(io.sel === 0.U) {
    temp.x := 10.U
  }.elsewhen(io.sel === 1.U) {
    temp.y := 20.U
  }.otherwise {
    temp.x := 30.U
    temp.y := 40.U
  }

  temp.x := 55.U // Overwrites any earlier x
  // temp.y not overwritten, so last write depends on sel

  io.out := temp

  // Expect io.out.x == 55, io.out.y depends on sel
}

class LCS7 extends Module {
  class Inner extends Bundle {
    val a = UInt(8.W)
    val b = UInt(8.W)
  }

  val io = IO(new Bundle {
    val sel = Input(Bool())
    val out = Output(new Inner)
  })

  val my_wire = Wire(new Inner)
  my_wire.a := 0.U
  my_wire.b := 0.U

  when(io.sel) {
    my_wire.a := 10.U
    my_wire.b := 20.U
  }

  my_wire.b := 99.U // Overwrites `b` only

  io.out := my_wire
  // Expect io.out.a = 10 if sel else 0; io.out.b = 99 always
}

class LCS8 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val c = Input(UInt(2.W))
    val d = Input(UInt(2.W))
    val sel = Input(UInt(2.W))
    val output = Output(UInt(2.W))
    val output_2 = Output(UInt(2.W))
  })

  io.output := DontCare

  when (io.sel === 0.U) {
    io.output := io.b
    io.output := io.a
  } .elsewhen (io.sel === 1.U) {
    io.output := io.a
    io.output := io.b
  } .otherwise {
    when (io.sel === 2.U) {
      io.output := io.d
      io.output := io.c
    }
    io.output := io.c
    io.output := io.d
  }

  when (io.sel === 3.U) {
    io.output_2 := io.c
    io.output_2 := io.d
    io.output_2 := io.b
    io.output_2 := io.a
  }
  io.output_2 := io.c
  io.output_2 := io.d
}

class LastConnectSemantics2 extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val c = Input(UInt(2.W))
    val d = Input(UInt(2.W))
    val sel = Input(UInt(2.W))
    val output = Output(UInt(2.W))
    val output_2 = Output(UInt(2.W))
  })

  io.output := DontCare

  when (io.sel === 0.U) {
    io.output := io.a
  } .elsewhen (io.sel === 1.U) {
    io.output := io.b
  } .otherwise {
    when (io.sel === 2.U) {
      io.output := io.c
    }
    io.output := io.d
  }

  when (io.sel === 3.U) {
    io.output_2 := io.a
  }
  io.output_2 := io.c
}

class W extends Bundle {
  val a = UInt(2.W)
  val b = Flipped(Vec(2, UInt(3.W)))
}

class X extends Bundle {
  val c = Vec(2, new W)
  val d = Flipped(UInt(3.W))
  val e = Decoupled(UInt(2.W))
}

class Y extends Bundle {
  val e = new X
  val f = Vec(3, UInt(2.W))
}

class Z extends Bundle {
  val g = Vec(2, new Y)
  val h = Flipped(Vec(2, new Y))
}

class NestedBundleModule extends Module {
  val io = IO(new Z)

  val reg = RegNext(io.h)
  io.g <> reg
}

class WireRegInsideWhen extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val out = Output(UInt(2.W))
  })

  when (io.a =/= io.b) {
    val out = Wire(UInt(2.W))
    out := io.a + io.b
    val nxt = RegNext(out)
    io.out := nxt
  } .otherwise {
    val out = Wire(UInt(2.W))
    out := io.a - io.b
    val nxt = RegInit(0.U(2.W))
    when (io.b === 2.U) {
      nxt := out
    }
    io.out := nxt
  }
}

class MultiWhen extends Module {
  val io = IO(new Bundle {
    val update = Input(Bool())
    val a = Input(UInt(3.W))
    val b = Input(UInt(3.W))
    val out = Output(UInt(3.W))
    val out_2 = Output(UInt(3.W))
  })

  io.out   := DontCare
  io.out_2 := DontCare

  when (io.update) {
    val out = Wire(UInt(3.W))
    out := io.a + io.b
    val nxt = RegNext(out)
    io.out := nxt
  } .otherwise {
    io.out := io.a + io.b
  }

  when (io.update) {
    val out = Wire(UInt(3.W))
    out := io.a + io.b
    val nxt = RegNext(out)
    io.out_2 := nxt
  } .otherwise {
    io.out_2 := io.a - io.b
  }
}
