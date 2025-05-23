`timescale 1ns / 1ps

module Fir_tb;

  reg clock;
  reg reset;
  reg [3:0] io_in;
  reg io_valid;
  reg [3:0] io_consts_0;
  reg [3:0] io_consts_1;
  reg [3:0] io_consts_2;
  reg [3:0] io_consts_3;

  wire [3:0] io_out;

  Fir dut (
    .clock(clock),
    .reset(reset),
    .io_in(io_in),
    .io_valid(io_valid),
    .io_out(io_out),
    .io_consts_0(io_consts_0),
    .io_consts_1(io_consts_1),
    .io_consts_2(io_consts_2),
    .io_consts_3(io_consts_3)
  );

  always #5 clock = ~clock;

  initial begin
    $dumpfile("dump.vcd");
    $dumpvars(0, Fir_tb);

    clock = 0;
    reset = 1;
    io_in = 0;
    io_valid = 0;
    io_consts_0 = 4'd1;
    io_consts_1 = 4'd2;
    io_consts_2 = 4'd3;
    io_consts_3 = 4'd4;

    #10;
    reset = 0;

    repeat (10) begin
      @(posedge clock);
      io_valid = 1;
      io_in = 4'b0001;
    end

    @(posedge clock);
    io_valid = 0;
    io_in = 0;

    repeat (5) @(posedge clock);

    $finish;
  end

endmodule


/*
sbt run
iverilog -g2012 -o fir_sim Fir.sv Fir_tb.v
vvp fir_sim
gtkwave dump.vcd
*/