```
Copyright (C) 2020 by phantom
Email: admin@phvntom.tech
This file is under MIT License, see http://phvntom.tech/LICENSE.txt
You can find more details in RISC-V Spec I && II, see https://github.com/riscv/riscv-isa-manual/releases
```

###  R-Type
```    
    31__________25_24______20_19______15_14__12_11______7_6__________0
    |_____________|__________|__________|______|_________|___________|
        funct7        rs2        rs1     funct3    rd        opcode
 add:  0000000                             0                 0110011
 sub:  0100000                             0
 sll:  0000000                             1
 slt:  0000000                             2
 sltu: 0000000                             3
 xor:  0000000                             4
 srl:  0000000                             5
 sra:  0100000                             5
 or:   0000000                             6
 and:  0000000                             7
```


###  I-Type
```    
    31____________________20_19______15_14__12_11______7_6__________0
    |_______________________|__________|______|_________|___________|
            imm[11:0]           rs1     funct3    rd        opcode
 addi:                                    0                 0010011
 slli:    000000  shamt                   1
 slti:                                    2
 sltiu:                                   3
 xori:                                    4
 srli:    000000  shamt                   5
 srai:    010000  shamt                   5
 ori:                                     6
 andi:                                    7
```
