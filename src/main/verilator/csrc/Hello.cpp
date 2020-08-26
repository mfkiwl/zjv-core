#include "VHello.h"
#include "verilated.h"
int main(int argc, char** argv, char** env) {
  Verilated::commandArgs(argc, argv);
  VHello* top = new VHello;
  while (!Verilated::gotFinish()) { top->eval(); }
  delete top;
  exit(0);
}