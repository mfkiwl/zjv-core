```
Copyright (C) 2020 by phantom
Email: admin@phvntom.tech
This file is under MIT License, see http://phvntom.tech/LICENSE.txt
Recording some concepts of verilator 

[Referencs]
    https://www.veripool.org/wiki/verilator/Manual-verilator
```

### *What is Verilator ?*

The "Verilator" package converts all synthesizable, and many behavioral, Verilog and SystemVerilog designs into a C++ or SystemC model that after compiling can be executed. 
Verilator is not a traditional simulator, but a compiler.

### Connecting to C++
Verilator creates a `prefix.h` and `prefix.cpp` file for the top level module, together with additional .h and .cpp files for internals. 

After the model is created, there will be a `prefix.mk` file that may be used with Make to produce a `prefix__ALL.a` file with all required objects in it. 
This is then linked with the user's C++ main loop to create the simulation executable.

When using SystemC, evaluation of the Verilated model is managed by the SystemC kernel, and for the most part can be ignored. 
When using C++, the user must call eval(), or eval_step() and eval_end_step(). The user must write the C++ main loop of the simulation. 

The signals are read and written as member variables of the model. 
User can call the eval() method to evaluate the model. 
When the simulation is complete call the final() method to execute any SystemVerilog final blocks, and complete any assertions.

1. When there is a single design instantiated at the C++ level that needs to evaluate, just call designp->eval().

2. When there are multiple designs instantiated at the C++ level that need to evaluate, call first_designp->eval_step() then ->eval_step() on all other designs. Then call ->eval_end_step() on the first design then all other designs. If there is only a single design, you would call eval_step() then eval_end_step(); in fact eval() described above is just a wrapper which calls these two functions.

When eval() is called Verilator looks for changes in clock signals and evaluates related sequential always blocks, such as computing always_ff @ (posedge...) outputs. Then Verilator evaluates combinatorial logic.

Note combinatorial logic is not computed before sequential always blocks are computed (for speed reasons). Therefore it is best to set any non-clock inputs up with a separate eval() call before changing clocks.

Alternatively, if all always_ff statements use only the posedge of clocks, or all inputs go directly to always_ff statements, as is typical, then you can change non-clock inputs on the negative edge of the input clock, which will be faster as there will be fewer eval() calls.







