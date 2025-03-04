read_verilog ShiftReg.sv
hierarchy -check -top ShiftReg
blackbox mem_8x8
blackbox tagMem_8x3
proc; memory; techmap
async2sync;
dffunmap;
flatten -wb
abc -fast -lut 3
opt -nodffe -nosdff;
write_blif ShiftReg.lut.blif
