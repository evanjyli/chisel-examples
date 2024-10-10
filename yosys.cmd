read_verilog PointerChasing.sv
hierarchy -check -top PointerChasing
proc; memory; techmap;
async2sync;
dffunmap;
flatten -wb
abc -fast -lut 3
opt -nodffe -nosdff;
write_blif PointerChasing.nofsm.lut.blif
