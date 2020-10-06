#ifndef _ENGINE_H
#define _ENGINE_H 
/*     Verilator     */ 
#include "VTop.h"
#include "VTop__Dpi.h"
#include "verilator.h"
#include "verilated.h"
// Trace
#if VM_TRACE
#include <verilated_vcd_c.h>
#endif

/*    spike    */
#include <riscv/difftest.h>
#include <riscv/sim.h>

/*     libc     */ 
#include <iostream>
#include <string>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>

#include "common.h"

#define emu_t VTop

#define REG_NUM    34
#define REG_G_NUM  32
#define REG_PC     32
#define REG_NPC    33

#define PROGRAM_PASS 0xc001babe
#define PROGRAM_FAIL 0xdeadbabe

class dtengine_t {
public:
    sim_t*  spike;
    emu_t*  zjv;

    difftest_sim_state_t sim_state;
    difftest_emu_state_t emu_state;

    reg_t trace_count;
    #if VM_TRACE
        VerilatedVcdC* tfp;
    #endif

    sim_t* sim_init(std::string elfpath);
    emu_t* emu_init(std::string elfpath);

    void emu_reset(uint cycle);
    void sim_reset(uint cycle);
    
    void sim_step(uint step);
    void emu_step(uint step);

    dtengine_t(std::string elfpath);

    void trace_close();

    void emu_get_state();
    void sim_get_state();

    unsigned long emu_get_pc();
    unsigned long emu_get_inst();
    unsigned long emu_get_poweroff();
    unsigned long sim_get_pc();

    bool is_finish();
    unsigned long emu_difftest_valid();

};


#endif // _ENGINE_H