#include "engine.h"

sim_t* dtengine_t::sim_init(std::string elfpath) {
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
        spike->set_procs_debug(true);
    #endif
    
    // spike->run();
    spike->difftest_setup();
}

emu_t* dtengine_t::emu_init(std::string elfpath) {
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
    emu_get_state();
}

void dtengine_t::sim_reset(uint cycle) {
    // TODO
}

void dtengine_t::sim_step(uint step) {
    spike->difftest_continue(1);
    sim_get_state();
}

void dtengine_t::sim_checkINT() {
    spike->difftest_checkINT();
    sim_get_state();
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
    emu_get_state();
}

dtengine_t::dtengine_t(std::string elfpath) {

    file_fifo_path = "/tmp/zjv";

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

void dtengine_t::emu_get_state() {
    #define emu_get_reg(n) emu_state.regs[n] = zjv->io_difftest_regs_##n
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
    emu_state.pc    = zjv->io_difftest_pc;
    emu_state.inst  = zjv->io_difftest_inst;
    emu_state.valid = zjv->io_difftest_valid;
    emu_state.interrupt = zjv->io_difftest_int;
    emu_state.poweroff  = zjv->io_poweroff;

    // printf(">>>> %lx\n", emu_state.poweroff);
}

void dtengine_t::sim_get_state() {
    spike->get_state(&sim_state);       
}

unsigned long dtengine_t::emu_difftest_valid() {
    return emu_state.valid;
}

unsigned long dtengine_t::emu_difftest_poweroff() {
    return emu_state.poweroff;
}

unsigned long dtengine_t::emu_get_mcycle() {
    return zjv->io_difftest_mcycler;
}

unsigned long dtengine_t::emu_get_pc() {
    return emu_state.pc;
}

unsigned long dtengine_t::emu_get_inst() {
    return emu_state.inst;
}

unsigned long dtengine_t::emu_get_mstatus() {
    return zjv->io_difftest_mstatusr;
}

unsigned long dtengine_t::emu_get_mideleg() {
    return zjv->io_difftest_midelegr;
}

unsigned long dtengine_t::emu_get_medeleg() {
    return zjv->io_difftest_medelegr;
}

unsigned long dtengine_t::emu_get_priv() {
    return zjv->io_difftest_privilege;
}

unsigned long dtengine_t::emu_get_mepc() {
    return zjv->io_difftest_mepcr;
}

unsigned long dtengine_t::emu_get_mtval() {
    return zjv->io_difftest_mtvalr;
}

unsigned long dtengine_t::emu_get_mcause() {
    return zjv->io_difftest_mcauser;
}

unsigned long dtengine_t::emu_get_mtvec() {
    return zjv->io_difftest_mtvecr;
}

unsigned long dtengine_t::emu_get_stvec() {
    return zjv->io_difftest_stvecr;
}

unsigned long dtengine_t::emu_get_sstatus() {
    return zjv->io_difftest_sstatusr;
}

unsigned long dtengine_t::emu_get_sepc() {
    return zjv->io_difftest_sepcr;
}

unsigned long dtengine_t::emu_get_stval() {
    return zjv->io_difftest_stvalr;
}

unsigned long dtengine_t::emu_get_scause() {
    return zjv->io_difftest_scauser;
}

unsigned long dtengine_t::sim_get_pc() {
    return sim_state.pc;
}

//unsigned long dtengine_t::sim_get_mstatus() {
//    return sim_state.mstatus;
//}
//
//unsigned long dtengine_t::sim_get_satp() {
//    return sim_state.satp;
//}
//
unsigned long dtengine_t::sim_get_priv() {
    return sim_state.priv;
}

unsigned long dtengine_t::sim_get_mideleg() {
    return sim_state.mideleg;
}

unsigned long dtengine_t::sim_get_medeleg() {
    return sim_state.medeleg;
}

unsigned long dtengine_t::emu_get_int() {
    return emu_state.interrupt;
}

void dtengine_t::sim_sync_cycle() {
    spike->sync_cycle();
}

bool dtengine_t::is_finish() {
    return Verilated::gotFinish() || emu_difftest_poweroff() != 0;
}