#include "VTop__Dpi.h"
#include "common.h"
#if VM_TRACE
#include <verilated_vcd_c.h>
#endif
//#include "disasm.h" // disabled for now... need to update to the current ISA/ABI in common/disasm.*
#include "VTop.h" // chisel-generated code...
#include "verilator.h"
#include <fesvr/dtm.h>
#include "verilated.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "riscv/sim.h"
#include <dlfcn.h>
#include <iostream>


extern dtm_t *dtm;

sim_t *sim;

uint64_t trace_count = 0;
double sc_time_stamp ()
{
  return double( trace_count );
}

void handle_sigterm(int sig)
{
   dtm->stop();
}

extern "C" int vpi_get_vlog_info(void* arg)
{
  return 0;
}

int main(int argc, char** argv)
{
   unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
   uint64_t max_cycles = 0;
   int start = 0;
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

   VTop dut; // design under test, aka, your chisel code

   //Instantiated DTM
   dtm = new dtm_t(htif_argc, argv);
   fprintf(stderr, "Instantiated DTM.\n");

#if VM_TRACE
   Verilated::traceEverOn(true); // Verilator must compute traced signals
   std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
   std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
   if (vcdfile) {
      dut.trace(tfp.get(), 99);  // Trace 99 levels of hierarchy
      tfp->open("");
   }
#endif

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
 
   sim = new sim_t("RV32IM", "M", "", 1, false, reg_t(-1), mems, plugin_devices, htif_args, hartids, dm_config);

   sim->set_log_commits(true);
   sim->difftest_setup();

   signal(SIGTERM, handle_sigterm);

   // reset for a few cycles to support pipelined reset
   for (int i = 0; i < 10; i++) {
    dut.reset = 1;
    dut.clock = 0;
    dut.eval();
    dut.clock = 1;
    dut.eval();
    dut.reset = 0;
  }

   while (!dtm->done() && !dut.io_success && !Verilated::gotFinish()) {
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

      sim->difftest_continue(1);

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
   else if (dtm->exit_code() <= 1)
   {
      fprintf(logfile, "*** PASSED ***\n");
   }
   else 
   {
     return dtm->exit_code();
   }

#if 0

#endif

   delete dtm;

   return 0;
}
