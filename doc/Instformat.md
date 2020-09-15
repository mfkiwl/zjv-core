```
Copyright (C) 2020 by phantom
Email: admin@phvntom.tech
This file is under MIT License, see http://phvntom.tech/LICENSE.txt
You can find more details in RISC-V Spec I && II, see https://github.com/riscv/riscv-isa-manual/releases
```

> Cover RV32I

###  R-Type
```    
    31__________25_24______20_19______15_14__12_11______7_6__________0
    |_____________|__________|__________|______|_________|___________|
        funct7        rs2        rs1     funct3    rd        opcode
 add:  0000000                             000               0110011
 sub:  0100000                             000
 sll:  0000000                             001
 slt:  0000000                             010
 sltu: 0000000                             011
 xor:  0000000                             100
 srl:  0000000                             101
 sra:  0100000                             101
 or:   0000000                             110
 and:  0000000                             111
```


###  I-Type
```    
    31____________________20_19______15_14__12_11______7_6__________0
    |_______________________|__________|______|_________|___________|
            imm[11:0]           rs1     funct3    rd        opcode
 addi:                                    000               0010011
 slli:    000000(0)  shamt                001
 slti:                                    010
 sltiu:                                   011
 xori:                                    100
 srli:    000000(0)  shamt                101  /* RV64 shamt is 6 bits */
 srai:    010000(0)  shamt                101
 ori:                                     110
 andi:                                    111

 lb:                                      000               0000011
 lh:                                      001               0000011
 lw:                                      010               0000011
 lbu:                                     100               0000011
 lhu:                                     101               0000011

 jalr:                                    000               1100111
```

### S-Type
```    
    31__________25_24______20_19______15_14__12_11______7_6__________0
    |_____________|__________|__________|______|_________|___________|
       imm[11:5]       rs2        rs1    funct3  imm[4:0]    opcode
 sb:                                       000               0100011
 sh:                                       001               0100011
 sw:                                       010               0100011
```

### B-Type
```    
    31__________25_24______20_19______15_14__12_11______7_6__________0
    |_____________|__________|__________|______|_________|___________|
      imm[12|10:5]     rs2        rs1    funct3 imm[4:1|11]  opcode
 beq:                                      000               1100011
 bne:                                      001               1100011
 blt:                                      100               1100011
 bge:                                      101               1100011
 bltu:                                     110               1100011
 bgeu:                                     111               1100011
```

### U-Type
```    
    31____________________________________12_11______7_6__________0
    |_______________________________________|_________|___________|
                     imm[31:12]                 rd        opcode
 lui:                                                     0110111
 auipc:                                                   0010111
```

### J-Type
```    
    31____________________________________12_11______7_6__________0
    |_______________________________________|_________|___________|
               imm[20|10:1|11|19:12]            rd        opcode
 jal:                                                     1101111
```










