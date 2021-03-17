ZJV SoC
=======

## Build

```bash
# Install packages
sudo apt install default-jdk verilator device-tree-compiler curl gnupg make gcc g++

# Install sbt
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt

# Clone repository
git clone https://github.com/phantom-v/phvntom.git
cd phvntom
git submodule update --init --recursive --progress

# Differential test with Spike
make generate_testcase
make generate_emulator
make generate_emulator

# FPGA verilog
LOOK AT THIS
!!!!! First change 'fpga' option in src/main/scala/common/config.scala to 'true' !!!!!
# 
make generate_fpga
cd zjv-fpga-acc
make generate_project
cd zjv-fpga
vivado
# Use GUI to synthesis, implement and generate bit stream
# Then ZJV core will give us AXI4 Memory, AXI4 MMIO and one meip I/O and we can build our SoC
```

## Address Space

现在zjv-fpga-acc里面有一个Top.v，这个暂时不要改，因为后面加指令集，都要与RV64 IMA这一版本进行时序对比，而且Nexys A7本身资源也不多，没有必要添加Rinux操作系统不需要的指令集。此外，IMA可以认为是基础指令集，之后添加FDC，最好是真的可以配置（不只是illegal inst，要能够保证支持FDC指令的硬件也相应可以增减）。

**For FPGA**

 // (0x10000000L, 0x30000000L)  // MMIO Out of Tile : SPI UART BRAM

 //    (0x10000000L, 0x00001000L)  // UART 13bit (because of Vivado's UART IP Design)

 //    (0x10010000L, 0x00010000)  // BRAM 16bit (First stage bootloader)

 //    (0x10020000L, 0x00010000)  // BRAM 16bit

 //    (0X10030000L, 0x00010000)  // SPI 16bit

 // (0x02000000L, 0x00010000L)  // CLINT

 // (0x0c000000L, 0x04000000L)  // PLIC

 // (0x80000000L, 128M )  // SDRAM

For **Difftest**


### TODO

- CSR Flush dealt in EXE2 stage
- The af signal after MEM1 is only useful for difftest