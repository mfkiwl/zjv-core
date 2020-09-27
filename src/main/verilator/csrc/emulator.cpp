#include "VTop__Dpi.h"
#include "common.h"
#define VM_TRACE 0
#if VM_TRACE
#include <verilated_vcd_c.h>
#endif
//#include "disasm.h" // disabled for now... need to update to the current ISA/ABI in common/disasm.*
#include "VTop.h" // chisel-generated code...
#include "verilator.h"
#include "verilated.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <riscv/difftest.h>
#include <riscv/sim.h>
#include <iostream>
#include <unistd.h>

sim_t *sim;

uint64_t trace_count = 0;

int main(int argc, char** argv)
{
   unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
   uint64_t max_cycles = 0;

   bool log = false;
   const char* loadmem = NULL;
   FILE *vcdfile = NULL, *logfile = stderr;
   const char* failure = NULL;
   int optind;

   for (int i = 1; i < argc; i++)
   {
      std::string arg = argv[i];
      if (arg.substr(0, 2) == "-v")
         vcdfile = fopen(argv[i]+2,(const char*)"w+");
      else if (arg.substr(0, 2) == "-s")
         random_seed = atoi(argv[i]+2);
      else if (arg == "+verbose")
         log = true;
      else if (arg.substr(0, 12) == "+max-cycles=")
         max_cycles = atoll(argv[i]+12);
      else { // End of EMULATOR options
         optind = i;
         break;
      }
   }

   // Shift HTIF options to the front of argv
   int htif_argc = 1 + argc - optind;
   for (int i = 1; optind < argc;)
   {
      argv[i++] = argv[optind++];
   }

   extern void init_ram(const char *img);
   init_ram(argv[1]);

   VTop dut; // design under test, aka, your chisel code

#if VM_TRACE
   Verilated::traceEverOn(true); // Verilator must compute traced signals
   VerilatedVcdC* tfp = new VerilatedVcdC;
   // std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
   // std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
   if (true) {
      dut.trace(tfp, 99);  // Trace 99 levels of hierarchy
      tfp->open("sim.vcd");
   }
   printf("TFP successfully opened sim.vcd\n");
#endif

   const char* isa = "RV64IM";
   const char* priv = "M";
   const char* varch = "vlen:128,elen:64,slen:128";
   size_t nprocs = 1;
   bool halted = false;
   reg_t start_pc = reg_t(-1);
   bool real_time_clint = false;
   reg_t initrd_start = 0, initrd_end = 0;
   const char* bootargs = NULL;
   reg_t size = reg_t(2048) << 20;
   std::vector<std::pair<reg_t, mem_t*>> mems(1, std::make_pair(reg_t(DRAM_BASE), new mem_t(size)));
   std::vector<std::pair<reg_t, abstract_device_t*>> plugin_devices;
   std::vector<std::string> htif_args((const char*const*)argv+1, (const char*const*)argv + argc);
   std::vector<int> hartids;
   debug_module_config_t dm_config = {
      .progbufsize = 2,
      .max_bus_master_bits = 0,
      .require_authentication = false,
      .abstract_rti = 0,
      .support_hasel = true,
      .support_abstract_csr_access = true,
      .support_haltgroups = true
   };
   const char *log_path = nullptr;
   bool dtb_enabled = true;
   const char* dtb_file = NULL;
 
   sim = new sim_t(isa, priv, varch, nprocs, halted, real_time_clint, initrd_start, initrd_end, bootargs, start_pc, 
                   mems, plugin_devices, htif_args, std::move(hartids), dm_config, log_path, dtb_enabled, dtb_file);

   sim->set_log_commits(true);
   // sim->run();

   sim->set_log_commits(true);
   sim->difftest_setup();

   // reset for a few cycles to support pipelined reset
   for (int i = 0; i < 10; i++) {
     dut.reset = 1;
     dut.clock = 0;
     dut.eval();
     dut.clock = 1;
     dut.eval();
     dut.reset = 0;
   }

   printf("[Verilator] Ready to Run\n");


   difftest_regs_t record;
   do {
      sim->difftest_continue(1);
      sim->get_regs(&record);
   } while (record.pc != 0x80000000);
   printf("[Spike] Ready to Run\n");

   bool start = false;

   while (!Verilated::gotFinish()) {
      dut.clock = 0;
      dut.eval();
#if VM_TRACE
      bool dump = tfp && trace_count >= start;
      if (dump)
         tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif
      dut.clock = 1;
      dut.eval();
#if VM_TRACE
      if (dump)
         tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
      trace_count++;

      if (!start && dut.io_difftest_pc == 0x80000000) {
         start = true;
         printf("[Phvntom] Ready to Run\n");
      }

      //printf("\t\t [ ROUND %d ]\n", trace_count);
      //printf("spike   pc:%08lx\n", record.pc);
      //printf("phvntom pc:%08lx\n", dut.io_difftest_pc);
      //printf("phvntom inst:%08lx\n", dut.io_difftest_inst);
      //printf("\t\t [ GAME_OVER %d ]\n", trace_count);

      if (start) {
         // printf("exe_imm next inst %lx\n", dut.io_difftest_inst);

         if(record.pc != dut.io_difftest_pc) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   pc:%lx\n", record.pc);
            printf("phvntom pc:%lx\n", dut.io_difftest_pc);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }

         if(record.regs[0] != dut.io_difftest_regs_0) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x0:%lx\n", record.regs[0]);
            printf("phvntom x0:%lx\n", dut.io_difftest_regs_0);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[1] != dut.io_difftest_regs_1) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x1:%lx\n", record.regs[1]);
            printf("phvntom x1:%lx\n", dut.io_difftest_regs_1);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[2] != dut.io_difftest_regs_2) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x2:%lx\n", record.regs[2]);
            printf("phvntom x2:%lx\n", dut.io_difftest_regs_2);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[3] != dut.io_difftest_regs_3) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x3:%lx\n", record.regs[3]);
            printf("phvntom x3:%lx\n", dut.io_difftest_regs_3);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[4] != dut.io_difftest_regs_4) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x4:%lx\n", record.regs[4]);
            printf("phvntom x4:%lx\n", dut.io_difftest_regs_4);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[5] != dut.io_difftest_regs_5 && record.regs[5] != 0x80000000) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x5:%lx\n", record.regs[5]);
            printf("phvntom x5:%lx\n", dut.io_difftest_regs_5);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[6] != dut.io_difftest_regs_6) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x6:%lx\n", record.regs[6]);
            printf("phvntom x6:%lx\n", dut.io_difftest_regs_6);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[7] != dut.io_difftest_regs_7) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x7:%lx\n", record.regs[7]);
            printf("phvntom x7:%lx\n", dut.io_difftest_regs_7);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
        if(record.regs[8] != dut.io_difftest_regs_8) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x8:%lx\n", record.regs[8]);
            printf("phvntom x8:%lx\n", dut.io_difftest_regs_8);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[9] != dut.io_difftest_regs_9) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x9:%lx\n", record.regs[9]);
            printf("phvntom x9:%lx\n", dut.io_difftest_regs_9);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[10] != dut.io_difftest_regs_10) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x10:%lx\n", record.regs[10]);
            printf("phvntom x10:%lx\n", dut.io_difftest_regs_10);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[11] != dut.io_difftest_regs_11 && record.regs[11] != 0x1020) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x11:%lx\n", record.regs[11]);
            printf("phvntom x11:%lx\n", dut.io_difftest_regs_11);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[12] != dut.io_difftest_regs_12) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x12:%lx\n", record.regs[12]);
            printf("phvntom x12:%lx\n", dut.io_difftest_regs_12);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[13] != dut.io_difftest_regs_13) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x13:%lx\n", record.regs[13]);
            printf("phvntom x13:%lx\n", dut.io_difftest_regs_13);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[14] != dut.io_difftest_regs_14) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x14:%lx\n", record.regs[14]);
            printf("phvntom x14:%lx\n", dut.io_difftest_regs_14);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[15] != dut.io_difftest_regs_15) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x15:%lx\n", record.regs[15]);
            printf("phvntom x15:%lx\n", dut.io_difftest_regs_15);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[16] != dut.io_difftest_regs_16) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x16:%lx\n", record.regs[16]);
            printf("phvntom x16:%lx\n", dut.io_difftest_regs_16);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[17] != dut.io_difftest_regs_17) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x17:%lx\n", record.regs[17]);
            printf("phvntom x17:%lx\n", dut.io_difftest_regs_17);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[18] != dut.io_difftest_regs_18) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x18:%lx\n", record.regs[18]);
            printf("phvntom x18:%lx\n", dut.io_difftest_regs_18);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[19] != dut.io_difftest_regs_19) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x19:%lx\n", record.regs[19]);
            printf("phvntom x19:%lx\n", dut.io_difftest_regs_19);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[20] != dut.io_difftest_regs_20) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x20:%lx\n", record.regs[20]);
            printf("phvntom x20:%lx\n", dut.io_difftest_regs_20);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[21] != dut.io_difftest_regs_21) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x21:%lx\n", record.regs[21]);
            printf("phvntom x21:%lx\n", dut.io_difftest_regs_21);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[22] != dut.io_difftest_regs_22) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x22:%lx\n", record.regs[22]);
            printf("phvntom x22:%lx\n", dut.io_difftest_regs_22);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[23] != dut.io_difftest_regs_23) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x23:%lx\n", record.regs[23]);
            printf("phvntom x23:%lx\n", dut.io_difftest_regs_23);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
        if(record.regs[24] != dut.io_difftest_regs_24) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x24:%lx\n", record.regs[24]);
            printf("phvntom x24:%lx\n", dut.io_difftest_regs_24);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[25] != dut.io_difftest_regs_25) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x25:%lx\n", record.regs[25]);
            printf("phvntom x25:%lx\n", dut.io_difftest_regs_25);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[26] != dut.io_difftest_regs_26) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x26:%lx\n", record.regs[26]);
            printf("phvntom x26:%lx\n", dut.io_difftest_regs_26);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[27] != dut.io_difftest_regs_27) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x27:%lx\n", record.regs[27]);
            printf("phvntom x27:%lx\n", dut.io_difftest_regs_27);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[28] != dut.io_difftest_regs_28) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x28:%lx\n", record.regs[28]);
            printf("phvntom x28:%lx\n", dut.io_difftest_regs_28);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[29] != dut.io_difftest_regs_29) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x29:%lx\n", record.regs[29]);
            printf("phvntom x29:%lx\n", dut.io_difftest_regs_29);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[30] != dut.io_difftest_regs_30) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x30:%lx\n", record.regs[30]);
            printf("phvntom x30:%lx\n", dut.io_difftest_regs_30);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }
         if(record.regs[31] != dut.io_difftest_regs_31) {
            printf("========== [ Trace ] ==========\n");
            printf("spike   x31:%lx\n", record.regs[31]);
            printf("phvntom x31:%lx\n", dut.io_difftest_regs_31);
            printf("Total trace %d.\n", trace_count);
            exit(-1);
         }

         sim->difftest_continue(1);
         sim->get_regs(&record);
         printf("\n");

      }

      

      // sim->difftest_continue(1);
      // sleep(0);

      if (max_cycles != 0 && trace_count == max_cycles)
      {
         failure = "timeout";
         break;
      }
   }

#if VM_TRACE
  if (tfp)
    tfp->close();
  if (vcdfile)
    fclose(vcdfile);
#endif   

   if (failure)
   {
      fprintf(logfile, "*** FAILED *** (%s) after %lld cycles\n", failure, (long long)trace_count);
      return -1;
   }


#if 0

#endif

   for (auto& mem : mems)
      delete mem.second;

   for (auto& plugin_device : plugin_devices)
      delete plugin_device.second;

   return 0;
}
