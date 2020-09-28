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
   {
      argv[i++] = argv[optind++];
   }

   if (htif_argc != 2) {
      printf("ARGUMENTS WRONG with %d\n", htif_argc);
      exit(1);
   }

   dtengine_t engine(argv[1]);
   engine.emu_reset(10);
   printf("[Emu] Reset after 10 cycles \n");

   bool startTest = false;

   while (!engine.is_finish()) {

      engine.emu_step(1);

      if (!startTest && engine.emu_get_pc() == 0x80000000) {
         startTest = true;
         printf("[Emu] DiffTest Start \n");
      }

      printf("\t\t\t\t [ ROUND %ld ]\n", engine.trace_count);
      printf("zjv   pc: 0x%016lx (0x%08lx)\n",  engine.emu_get_pc(), engine.emu_get_inst());
      
      if (startTest && engine.emu_difftest_valid()) {
         engine.sim_step(1);

         if (engine.is_finish()) {
            printf("\n\t\t \x1b[32m========== [ %s PASS ] ==========\x1b[0m\n", argv[1]);
            break;
         }
         
         else if((engine.emu_get_pc() != engine.sim_get_pc()) ||
            (memcmp(engine.sim_state.regs, engine.emu_state.regs, 32*sizeof(reg_t)) != 0 ) ) {
            printf("\t\t ========== [ Trace Out ] ==========\n");
            if (engine.emu_get_pc() != engine.sim_get_pc())
               printf("emu|sim \x1b[31mpc: %016lX|%016lx\x1b[0m\n",  engine.emu_get_pc(), engine.sim_get_pc());
            else
               printf("emu|sim pc: %016lX|%016lx\n",  engine.emu_get_pc(), engine.sim_get_pc());

            for (int i = 0; i < REG_G_NUM; i++) {
               if (engine.emu_state.regs[i] != engine.sim_state.regs[i])
                  printf("\x1b[31m[%-3s] = %016lX|%016lx \x1b[0m", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
               else
                  printf("[%-3s] = %016lX|%016lx ", reg_name[i], engine.emu_state.regs[i], engine.sim_state.regs[i]);
               if (i % 3 == 2)
                  printf("\n");
            }
            if (REG_G_NUM % 3 != 0)
            printf("\n");
            exit(-1);
         }

      }

      printf("\n");      

      // sleep(1);
   }

   engine.trace_close();
   return 0;
}


