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
# First change 'fpga' option in src/main/scala/common/config.scala to 'true'
# After making, we can find verilog in build/verilog/rv64_nstage/zjv_fpga.v
make generate_fpga
# Then ZJV core will give us AXI4 Memory, AXI4 MMIO and one meip I/O and we can build our SoC
```
