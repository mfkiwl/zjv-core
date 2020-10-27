#include "engine.h"

void dtengine_t::sim_init(std::string elfpath) {
    sim_state = new difftest_state_t;
    const char* isa = "RV64IMA";
    const char* priv = "MSU";
    const char* varch = "vlen:128,elen:64,slen:128";
    size_t nprocs = 1;
    bool halted = false;
    reg_t start_pc = reg_t(0x80000000);
    bool real_time_clint = false;
    reg_t initrd_start = 0, initrd_end = 0;
    const char* bootargs = NULL;
    reg_t size = reg_t(2048) << 20;
    std::vector<std::pair<reg_t, mem_t*>> mems(1, std::make_pair(reg_t(DRAM_BASE), new mem_t(size)));
    std::vector<std::pair<reg_t, abstract_device_t*>> plugin_devices;
    std::vector<std::string> htif_args; htif_args.push_back(elfpath);
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
    bool diffTest = true;

    spike = new sim_t(isa, priv, varch, nprocs, halted, real_time_clint, initrd_start, initrd_end, bootargs, start_pc, 
                    mems, plugin_devices, htif_args, std::move(hartids), dm_config, log_path, dtb_enabled, dtb_file,
                    diffTest, file_fifo_path);

    #ifdef ZJV_DEBUG
//         spike->set_procs_debug(true);
    #endif
    
    // spike->run();
    spike->difftest_setup();
}

void dtengine_t::emu_init(std::string elfpath) {
    emu_state = new difftest_state_t;
    zjv = new emu_t;
    init_ram(elfpath.c_str());
    init_uart(file_fifo_path);
}

void dtengine_t::emu_reset(uint cycle) {
    for (int i = 0; i < cycle; i++) {
        zjv->reset = 1;
        zjv->clock = 0;
        zjv->eval();
        zjv->clock = 1;
        zjv->eval();
        zjv->reset = 0;
    }
    emu_update_state();
}

void dtengine_t::sim_reset(uint cycle) {
    // TODO
}

void dtengine_t::sim_solo() {
    sim_sync_cycle();
    spike->difftest_checkINT();
}

void dtengine_t::sim_step(uint step) {
    spike->difftest_continue(1);
    sim_update_state();
}

void dtengine_t::sim_check_interrupt() {
    spike->difftest_checkINT();
    sim_update_state();
}

void dtengine_t::emu_step(uint step) {
    zjv->clock = 0;
    zjv->eval();
    #if VM_TRACE
        bool dump = tfp && trace_count >= start;
        if (dump)
            tfp->dump(static_cast<vluint64_t>(trace_count * 2));
    #endif
    zjv->clock = 1;
    zjv-> eval();
    #if VM_TRACE
        if (dump)
            tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
    #endif
    trace_count++;
    emu_update_state();
}

dtengine_t::dtengine_t(size_t xlen, std::string elfpath): xlen(xlen) {

    file_fifo_path = "/tmp/zjv";
    disassembler = new disassembler_t(xlen);

    emu_init(elfpath);
    sim_init(elfpath);
    

    #if VM_TRACE
        Verilated::traceEverOn(true); // Verilator must compute traced signals
        tfp = new VerilatedVcdC;
        // std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
        // std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
        if (true) {
            zjv->trace(tfp, 99);  // Trace 99 levels of hierarchy
            tfp->open("sim.vcd");
        }
        printf("TFP successfully opened sim.vcd\n");
    #endif
    trace_count = 0;
}

void dtengine_t::trace_close () {
    #if VM_TRACE
    if (tfp)
        tfp->close();
    if (vcdfile)
        fclose(vcdfile);
    #endif  
}

void dtengine_t::emu_update_state() {
    #define emu_get_reg(n) emu_state->regs[n] = zjv->io_difftest_regs_##n
            emu_get_reg( 0); emu_get_reg(10); emu_get_reg(20); emu_get_reg(30); 
            emu_get_reg( 1); emu_get_reg(11); emu_get_reg(21); emu_get_reg(31); 
            emu_get_reg( 2); emu_get_reg(12); emu_get_reg(22); 
            emu_get_reg( 3); emu_get_reg(13); emu_get_reg(23);
            emu_get_reg( 4); emu_get_reg(14); emu_get_reg(24);
            emu_get_reg( 5); emu_get_reg(15); emu_get_reg(25);
            emu_get_reg( 6); emu_get_reg(16); emu_get_reg(26);
            emu_get_reg( 7); emu_get_reg(17); emu_get_reg(27);
            emu_get_reg( 8); emu_get_reg(18); emu_get_reg(28);
            emu_get_reg( 9); emu_get_reg(19); emu_get_reg(29);
    #undef emu_get_reg
    emu_state->pc        = zjv->io_difftest_pc;
    emu_state->inst      = zjv->io_difftest_inst;
    emu_state->valid     = zjv->io_difftest_valid;
    emu_state->interrupt = zjv->io_difftest_int;
    emu_state->poweroff  = zjv->io_poweroff;
    emu_state->mstatus   = zjv->io_difftest_mstatus;
    emu_state->mideleg   = zjv->io_difftest_mideleg;
    emu_state->medeleg   = zjv->io_difftest_medeleg;
    emu_state->priv      = zjv->io_difftest_priv;
    emu_state->mepc      = zjv->io_difftest_mepc;
    emu_state->mtval     = zjv->io_difftest_mtval;
    emu_state->mcause    = zjv->io_difftest_mcause;
    emu_state->mtvec     = zjv->io_difftest_mtvec;
    emu_state->stvec     = zjv->io_difftest_stvec;
    emu_state->sstatus   = zjv->io_difftest_sstatus;
    emu_state->sepc      = zjv->io_difftest_sepc;
    emu_state->stval     = zjv->io_difftest_stval;
    emu_state->scause    = zjv->io_difftest_scause;
    emu_state->mcycle    = zjv->io_difftest_mcycle;
    emu_state->mip       = zjv->io_difftest_mip;
    emu_state->sip       = zjv->io_difftest_sip;
    emu_state->mie       = zjv->io_difftest_mie;
    emu_state->sie       = zjv->io_difftest_sie;

    emu_state->uartirq       = zjv->io_difftest_uartirq;
    emu_state->plicmeip       = zjv->io_difftest_plicmeip;
    emu_state->plicseip       = zjv->io_difftest_plicseip;
}

void dtengine_t::sim_update_state() {
    state_t* s = spike->get_state();

    for (int i = 0; i < 32; i++) {
      sim_state->regs[i] = s->XPR[i];
    }
    sim_state->npc = s->pc;
    sim_state->pc = s->last_pc;
    sim_state->inst = s->last_inst;
    sim_state->priv = s->prv;
    sim_state->mstatus  = s->mstatus;
    sim_state->mepc    = s->mepc;
    sim_state->mtval   = s->mtval;
    sim_state->mcause  = s->mcause;
    sim_state->mtvec   = s->mtvec;
    sim_state->mideleg = s->mideleg;
    sim_state->medeleg = s->medeleg;
    sim_state->sstatus = s->mstatus & ~(MSTATUS_MIE | MSTATUS_MPIE | MSTATUS_MPP | MSTATUS_MPRV | 
                                       MSTATUS_TVM | MSTATUS_TW | MSTATUS_TSR | MSTATUS_SXL | 
                                       MSTATUS64_MBE | MSTATUS64_SBE );
    sim_state->sepc    = s->sepc;
    sim_state->stval   = s->stval;
    sim_state->scause  = s->scause;
    sim_state->stvec   = s->stvec;
    sim_state->satp    = s->satp;
    sim_state->mip     = s->mip;
    sim_state->mie     = s->mie;
    sim_state->sip     = s->mip & ~(MIP_MSIP | MIP_MTIP | MIP_MEIP);
    sim_state->sie     = s->mie & ~(MIP_MSIP | MIP_MTIP | MIP_MEIP);
}

unsigned long dtengine_t::emu_difftest_valid() {
    return emu_state->valid;
}

unsigned long dtengine_t::emu_difftest_poweroff() {
    return emu_state->poweroff;
}

void dtengine_t::sim_sync_cycle() {
    spike->sync_cycle();
}

bool dtengine_t::is_finish() {
    return Verilated::gotFinish() || emu_difftest_poweroff() != 0;
}