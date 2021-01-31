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
# !!!!! First change 'fpga' option in src/main/scala/common/config.scala to 'true' !!!!!
# After making, we can find verilog in build/verilog/rv64_nstage/zjv_fpga.v
make generate_fpga
cd fpga
make generate_project
cd ZJV4FPGA
vivado
# Use GUI to synthesis, implement and generate bit stream
# Then ZJV core will give us AXI4 Memory, AXI4 MMIO and one meip I/O and we can build our SoC
```

## Address Space

**For FPGA**

 // (0x04000000L, 0x30000000L)  // MMIO Out of Tile : SPI UART BRAM

 //    (0x04000000L, 0x00001000L)  // UART 13bit (because of Vivado's UART IP Design)

 //    (0x04010000L, 0x00010000)  // BRAM 16bit

 //    (0X04020000L, 0x00010000)  // SPI 16bit

 // (0x38000000L, 0x00010000L)  // CLINT

 // (0x3c000000L, 0x04000000L)  // PLIC

 // (0x80000000L, undecidedL )  // SDRAM

For **Difftest**