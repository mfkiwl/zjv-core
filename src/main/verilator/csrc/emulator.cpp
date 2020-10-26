#include "engine.h"

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

//     while (!engine.is_finish()) {
//           engine.emu_step(1);
//     }

//    while (!engine.is_finish()) {
//          engine.sim_solo();
//    }

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
//        fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());
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


      if(engine.emu_get_interrupt()) {
         engine.sim_check_interrupt();
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

      if(((engine.emu_get_pc() != engine.sim_get_pc()) ||
          (memcmp(engine.get_sim_state()->regs, engine.get_emu_state()->regs, 32*sizeof(reg_t)) != 0 ))) {

            faultExitLatency++;
            fprintf(stderr,"zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());

            fprintf(stderr, "\n\t\t \x1b[31m========== [ %s FAIL ] ==========\x1b[0m\n", argv[1]);
            difftest_check_point(pc); 
            difftest_check_point(priv);
            difftest_check_point(mstatus);
            difftest_check_point(mepc);
            difftest_check_point(mtval);
            difftest_check_point(mcause);
            difftest_check_point(mtvec);
            difftest_check_point(mepc);
            difftest_check_point(mtvec);
            difftest_check_point(mideleg);
            difftest_check_point(medeleg);
            difftest_check_point(sstatus);
            difftest_check_point(sepc);
            difftest_check_point(stval);
            difftest_check_point(scause);
            difftest_check_point(stvec);
            difftest_check_point(sepc);
            difftest_check_point(stvec);
            difftest_check_general_register();

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

      if(bubble_cnt > 4096) {
        printf("Too many bubbles, end at %lx\n", engine.emu_get_pc());
        exit(-1);
      }
   }

   engine.trace_close();
   return 0;
}


