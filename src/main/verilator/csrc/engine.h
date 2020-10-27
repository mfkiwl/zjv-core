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
#include "riscv/sim.h"
#include "riscv/disasm.h"

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

struct difftest_state_t {
    reg_t regs[32];
    reg_t pc;
    reg_t inst;
    reg_t npc;
    reg_t priv;
    reg_t mstatus;
    reg_t mepc;
    reg_t mtval;
    reg_t mcause;
    reg_t mtvec;
    reg_t mideleg;
    reg_t medeleg;
    reg_t mcycle;
    reg_t sstatus;
    reg_t sepc;
    reg_t stval;
    reg_t scause;
    reg_t stvec;
    reg_t satp;
    bool valid;
    bool interrupt;
    reg_t poweroff;
    bool meip_as;
    bool seip_as;
};

class dtengine_t {
public:
    dtengine_t(size_t xlen, std::string elfpath);
    bool is_finish();
    void trace_close();

    void sim_check_interrupt();
    void sim_solo();
    void sim_sync_cycle();

    void sim_init(std::string elfpath);
    void emu_init(std::string elfpath);

    void emu_reset(uint cycle);
    void sim_reset(uint cycle);
    
    void sim_step(uint step);
    void emu_step(uint step);

    difftest_state_t* get_sim_state(){ return sim_state; };
    difftest_state_t* get_emu_state(){ return emu_state; };

    void emu_update_state();
    void sim_update_state();      

 
    unsigned long emu_difftest_valid();
    unsigned long emu_difftest_poweroff();

#define get(which, reg) reg_t which##_get_##reg() { return which##_state->reg; }
    get(emu, pc);
    get(emu, inst);
    get(emu, interrupt);
    get(emu, mcycle);
    get(emu, mstatus);
    get(emu, priv);
    get(emu, mepc);
    get(emu, mtval);
    get(emu, mcause);
    get(emu, sstatus);
    get(emu, sepc);
    get(emu, stval);
    get(emu, scause);
    get(emu, stvec);
    get(emu, mtvec);
    get(emu, mideleg);
    get(emu, medeleg);
    get(emu, meip_as);
    get(emu, seip_as);

    get(sim, pc);
    get(sim, inst);
    get(sim, mstatus);
    get(sim, priv);
    get(sim, mepc);
    get(sim, mtval);
    get(sim, mcause);
    get(sim, mtvec);
    get(sim, mideleg);
    get(sim, medeleg);
    get(sim, sstatus);
    get(sim, sepc);
    get(sim, stval);
    get(sim, scause);
    get(sim, stvec);
    get(sim, satp);
    get(sim, meip_as);
    get(sim, seip_as);
#undef get

    reg_t trace_count;

    const char *reg_name[REG_G_NUM] = {
        "x0", "ra", "sp",  "gp",  "tp", "t0", "t1", "t2",
        "s0", "s1", "a0",  "a1",  "a2", "a3", "a4", "a5",
        "a6", "a7", "s2",  "s3",  "s4", "s5", "s6", "s7",
        "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    std::string disasm(reg_t bits) {
        if (disassembler) 
            return disassembler->disassemble(bits);
        else
            return "";
    }

private:
    size_t xlen;
    sim_t*  spike;
    emu_t*  zjv;

    difftest_state_t* sim_state;
    difftest_state_t* emu_state;

    #if VM_TRACE
        VerilatedVcdC* tfp;
    #endif

    std::string file_fifo_path;
    disassembler_t* disassembler;
};

#define difftest_check_point(reg) \
    if (engine.emu_get_##reg() != engine.sim_get_##reg())   \
        fprintf(stderr, "emu|sim \x1b[31m" #reg ": %016lX|%016lx\x1b[0m\n",  engine.emu_get_##reg(), engine.sim_get_##reg());   \
    else    \
        fprintf(stderr, "emu|sim " #reg ": %016lX|%016lx\n",  engine.emu_get_##reg(), engine.sim_get_##reg());

#define difftest_check_general_register() \
    for (int i = 0; i < REG_G_NUM; i++) {   \
        if (engine.get_emu_state()->regs[i] != engine.get_sim_state()->regs[i]) \
            fprintf(stderr, "\x1b[31m[%-3s] = %016lX|%016lx \x1b[0m", engine.reg_name[i], engine.get_emu_state()->regs[i], engine.get_sim_state()->regs[i]);  \
        else    \
            fprintf(stderr, "[%-3s] = %016lX|%016lx ", engine.reg_name[i], engine.get_emu_state()->regs[i], engine.get_sim_state()->regs[i]);   \
        if (i % 3 == 2) \
            fprintf(stderr, "\n");  \
    }


#endif // _ENGINE_H