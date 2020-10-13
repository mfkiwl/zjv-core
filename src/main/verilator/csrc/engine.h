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

extern void init_uart(const std::string img);
extern void init_ram(const std::string img);

class dtengine_t {
public:
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
    unsigned long emu_get_int();
    unsigned long emu_get_mcycle();

    unsigned long sim_get_pc();
    void sim_sync_cycle();
    void sim_set_mip();

    bool is_finish();
    unsigned long emu_difftest_valid();
    unsigned long emu_difftest_poweroff();

    difftest_sim_state_t sim_state;
    difftest_emu_state_t emu_state;

    reg_t trace_count;

private:
    sim_t*  spike;
    emu_t*  zjv;

    #if VM_TRACE
        VerilatedVcdC* tfp;
    #endif

    std::string file_fifo_path;
};


#endif // _ENGINE_H