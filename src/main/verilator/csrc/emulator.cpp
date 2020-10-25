#include "engine.h"

const char *reg_name[REG_G_NUM] = {
  "x0", "ra", "sp",  "gp",  "tp", "t0", "t1", "t2",
  "s0", "s1", "a0",  "a1",  "a2", "a3", "a4", "a5",
  "a6", "a7", "s2",  "s3",  "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

sim_t *sim;
reg_t emu_regs[32];

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
      argv[i++] = argv[optind++];

   if (htif_argc != 2) {
      #ifdef ZJV_DEBUG
         printf("ARGUMENTS WRONG with %d\n", htif_argc);
      #endif
      exit(1);
   }

   dtengine_t engine(argv[1]);
   engine.emu_reset(10);
   #ifdef ZJV_DEBUG
      fprintf(stderr, "[Emu] Reset after 10 cycles \n");
   #endif

   bool startTest = false;
   int faultExitLatency = 0;
   bool faultFlag = false;
   
   int cont_count = 0;
   int bubble_cnt = 0;
   int int_total_cnt = 0;
   long sim_cnt = 0;

   //  while (!engine.is_finish()) {
   //        engine.emu_step(1);
   //  }

   // while (!engine.is_finish()) {
   //       engine.sim_solo();
   // }

   while (!engine.is_finish()) {
      engine.emu_step(1);
      engine.sim_sync_cycle();

      if (!startTest && engine.emu_get_pc() == 0x80000000) {
         startTest = true;
         #ifdef ZJV_DEBUG
            fprintf(stderr, "[Emu] DiffTest Start \n");
         #endif
      }

      #ifdef ZJV_DEBUG

//        fprintf(stderr, "\t\t\t\t [ ROUND %lx %lx ]\n", engine.trace_count, engine.emu_get_mcycle());
        fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());

      #endif

      if (engine.is_finish()) {
         if (engine.emu_difftest_poweroff() == (long)PROGRAM_PASS) {
            fprintf(stderr, "\n\t\t \x1b[32m========== [ %s PASS with IPC %f ] ==========\x1b[0m\n", argv[1], 1.0 * sim_cnt / engine.trace_count);
            //sleep(5);
         }
         else
            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);
         break;
      }


      if(engine.emu_get_int()) {
         engine.sim_checkINT();
         int_total_cnt++;
         if (int_total_cnt > 50) {
            fprintf(stderr, "\n\t\t \x1b[32m========== [ %s PASS with IPC %f ] ==========\x1b[0m\n", argv[1], 1.0 * sim_cnt / engine.trace_count);
            printf("Total Int Cnt is %d!\n", int_total_cnt);
            //sleep(5);
            exit(0);
         }

      }

      if (startTest && engine.emu_difftest_valid()) {
         bubble_cnt = 0;
      #ifdef ZJV_DEBUG
//         fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());
      #endif
         engine.sim_step(1);
         sim_cnt++;

//         printf("engine.emu_get_priv() %d, engine.sim_get_priv() %d\n", engine.emu_get_priv(), engine.sim_get_priv());


//         fprintf(stderr, "emu|sim \x1b[34mpc: %016lX|%016lx\x1b[0m\n",  engine.emu_get_pc(), engine.sim_get_pc());
//         for (int i = 0; i < REG_G_NUM; i++) {
//            if (engine.emu_state.regs[i] != engine.sim_state.regs[i])
//               fprintf(stderr, "\x1b[31m[%-3s] = %016lX|%016lx \x1b[0m", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
//            else
//               fprintf(stderr, "[%-3s] = %016lX|%016lx ", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
//            if (i % 3 == 2)
//               fprintf(stderr, "\n");
//         }
//         if (REG_G_NUM % 3 != 0)
//            fprintf(stderr, "\n");
//
//         fprintf(stderr, "zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());
//         if (REG_G_NUM % 3 != 0)
//            fprintf(stderr, "\n");
//         fprintf(stderr, "\n");

//      fprintf(stderr, "sim priv: %016lX, emu priv: %016lX\n",  engine.sim_get_priv(), engine.emu_get_priv());
//      fprintf(stderr, "sim idel: %016lX, emu idel: %016lX\n",  engine.sim_get_mideleg(), engine.emu_get_mideleg());
//      fprintf(stderr, "sim edel: %016lX, emu edel: %016lX\n",  engine.sim_get_medeleg(), engine.emu_get_medeleg());

      if(((engine.emu_get_pc() != engine.sim_get_pc()) ||
//          engine.sim_get_mideleg() != engine.emu_get_mideleg() ||
//          (engine.emu_get_priv() != engine.sim_get_priv()) ||
          (memcmp(engine.sim_state.regs, engine.emu_state.regs, 32*sizeof(reg_t)) != 0 ))) {

            faultExitLatency++;

            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);
            if (engine.emu_get_pc() != engine.sim_get_pc()) {
               fprintf(stderr, "emu [%lx]: mstate %016lx mepc  %016lx mtval %016lx mcause %016lx\n",
                                engine.emu_get_priv(),
                                engine.emu_get_mstatus(), engine.emu_get_mepc(), engine.emu_get_mtval(),
                                engine.emu_get_mcause());
               fprintf(stderr, "         sstate %016lx sepc  %016lx stval %016lx scause %016lx\n",
                                engine.emu_get_sstatus(), engine.emu_get_sepc(), engine.emu_get_stval(),
                                engine.emu_get_scause());
               fprintf(stderr, "         mtvec %016lx  stvec %016lx mideleg %16lx medeleg %16lx\n",
                                engine.emu_get_mtvec(), engine.emu_get_stvec(), engine.emu_get_mideleg(),
                                engine.emu_get_medeleg());
               fprintf(stderr, "emu|sim \x1b[31mpc: %016lX|%016lx\x1b[0m\n",  engine.emu_get_pc(), engine.sim_get_pc());
            }
            else
                fprintf(stderr, "emu|sim pc: %016lX|%016lx\n",  engine.emu_get_pc(), engine.sim_get_pc());

//            if (engine.emu_get_mstatus() != engine.sim_get_mstatus())
//                fprintf(stderr, "emu|sim \x1b[31mmstatus: %016lX|%016lx\x1b[0m\n",  engine.emu_get_mstatus(), engine.sim_get_mstatus());
//            else
//                fprintf(stderr, "emu|sim mstatus: %016lX|%016lx\n",  engine.emu_get_mstatus(), engine.sim_get_mstatus());
//
//            if (engine.emu_get_priv() != engine.sim_get_priv())
//                fprintf(stderr, "emu|sim \x1b[31mpriv: %016lX|%016lx\x1b[0m\n",  engine.emu_get_priv(), engine.sim_get_priv());
//            else
//                fprintf(stderr, "emu|sim priv: %016lX|%016lx\n",  engine.emu_get_priv(), engine.sim_get_priv());

            for (int i = 0; i < REG_G_NUM; i++) {
               if (engine.emu_state.regs[i] != engine.sim_state.regs[i])
                  fprintf(stderr, "\x1b[31m[%-3s] = %016lX|%016lx \x1b[0m", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
               else
                  fprintf(stderr, "[%-3s] = %016lX|%016lx ", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
               if (i % 3 == 2)
                  fprintf(stderr, "\n");
            }
            fprintf(stderr, "\n");
            if (faultExitLatency == 1)
                exit(-1);
         }
         else {
            faultExitLatency = 0;
         }
      }
      else {
        bubble_cnt++;
      }

      if(bubble_cnt > 256) {
        printf("Too many bubbles, end at %lx\n", engine.emu_get_pc());
        exit(-1);
      }
      #ifdef ZJV_DEBUG
         // fprintf(stderr, "\n");
      #endif

      // sleep(1);
   }

   engine.trace_close();
   return 0;
}


