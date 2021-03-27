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
#include "riscv/extension.h"

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
    reg_t streqs[10];
    reg_t pc, inst;
    reg_t npc;
    reg_t priv;
    reg_t mstatus, mepc, mtval, mcause, mtvec;
    reg_t mip, mie;
    reg_t mideleg, medeleg;
    reg_t mcycle;
    reg_t sstatus, sepc, stval, scause, stvec;
    reg_t sip, sie;   
    reg_t satp;
    bool valid;
    bool interrupt;
    reg_t poweroff;
    bool uartirq;
    bool plicmeip;
    bool plicseip;
    uint32_t plicip;
    uint32_t plicie;
    uint32_t plicprio;
    uint32_t plicthrs;
    uint32_t plicclaim;
    bool mem;
    reg_t pa;
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

#define difftest_get(which, reg) reg_t which##_get_##reg() { return which##_state->reg; }
    difftest_get(emu, pc);
    difftest_get(emu, inst);
    difftest_get(emu, interrupt);
    difftest_get(emu, mcycle);
    difftest_get(emu, mstatus);
    difftest_get(emu, priv);
    difftest_get(emu, mepc);
    difftest_get(emu, mtval);
    difftest_get(emu, mcause);
    difftest_get(emu, sstatus);
    difftest_get(emu, sepc);
    difftest_get(emu, stval);
    difftest_get(emu, scause);
    difftest_get(emu, stvec);
    difftest_get(emu, mtvec);
    difftest_get(emu, mideleg);
    difftest_get(emu, medeleg);
    difftest_get(emu, mip);
    difftest_get(emu, sip);
    difftest_get(emu, mie);
    difftest_get(emu, sie);
    difftest_get(emu, mem);
    difftest_get(emu, pa);

    difftest_get(sim, npc);
    difftest_get(sim, pc);
    difftest_get(sim, inst);
    difftest_get(sim, mcycle);
    difftest_get(sim, mstatus);
    difftest_get(sim, priv);
    difftest_get(sim, mepc);
    difftest_get(sim, mtval);
    difftest_get(sim, mcause);
    difftest_get(sim, mtvec);
    difftest_get(sim, mideleg);
    difftest_get(sim, medeleg);
    difftest_get(sim, sstatus);
    difftest_get(sim, sepc);
    difftest_get(sim, stval);
    difftest_get(sim, scause);
    difftest_get(sim, stvec);
    difftest_get(sim, satp);
    difftest_get(sim, mip);
    difftest_get(sim, sip);
    difftest_get(sim, mie);
    difftest_get(sim, sie);
    // difftest_get(sim, pa);

    difftest_get(emu, uartirq);
    difftest_get(emu, plicmeip);
    difftest_get(emu, plicseip);
    difftest_get(emu, plicip);
    difftest_get(emu, plicie);
    difftest_get(emu, plicprio);
    difftest_get(emu, plicthrs);
    difftest_get(emu, plicclaim);
#undef difftest_get

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

#define difftest_check_point(reg, end...)                           \
    if (engine.emu_get_##reg() != engine.sim_get_##reg())           \
        fprintf(stderr, "\x1b[31m%-7s: %016lX|%016lx\x1b[0m " end,  \
                #reg,                                               \
                engine.emu_get_##reg(),                             \
                engine.sim_get_##reg());                            \
    else                                                            \
        fprintf(stderr, "%-7s: %016lX|%016lx " end,                 \
                #reg,                                               \
                engine.emu_get_##reg(),                             \
                engine.sim_get_##reg());

#define difftest_check_general_register()                                           \
    for (int i = 0; i < REG_G_NUM; i++) {                                           \
        if (engine.get_emu_state()->regs[i] != engine.get_sim_state()->regs[i])     \
            fprintf(stderr, "\x1b[31m[%-3s] = %016lX|%016lx \x1b[0m",               \
                    engine.reg_name[i],                                             \
                    engine.get_emu_state()->regs[i],                                \
                    engine.get_sim_state()->regs[i]);                               \
        else                                                                        \
            fprintf(stderr, "[%-3s] = %016lX|%016lx ",                              \
                    engine.reg_name[i],                                             \
                    engine.get_emu_state()->regs[i],                                \
                    engine.get_sim_state()->regs[i]);                               \
        if (i % 3 == 2 || i == REG_G_NUM-1)                                         \
            fprintf(stderr, "\n");                                                  \
    }                                                                               \
    fprintf(stderr, "\n");


#endif // _ENGINE_H
