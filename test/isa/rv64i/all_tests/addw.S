#include "test_macros.h"
#include "riscv_test.h"

.macro init; 
.endm

.section .text.init;
.align  6;
.globl _start;

_start:
  TEST_RR_OP( 2,  addw, 0x00000000, 0x00000000, 0x00000000 );
  TEST_RR_OP( 3,  addw, 0x00000002, 0x00000001, 0x00000001 );
  TEST_RR_OP( 4,  addw, 0x0000000a, 0x00000003, 0x00000007 );

  TEST_RR_OP( 5,  addw, 0xffffffffffff8000, 0x0000000000000000, 0xffffffffffff8000 );
  TEST_RR_OP( 6,  addw, 0xffffffff80000000, 0xffffffff80000000, 0x00000000 );
  TEST_RR_OP( 7,  addw, 0x000000007fff8000, 0xffffffff80000000, 0xffffffffffff8000 );

  TEST_RR_OP( 8,  addw, 0x0000000000007fff, 0x0000000000000000, 0x0000000000007fff );
  TEST_RR_OP( 9,  addw, 0x000000007fffffff, 0x000000007fffffff, 0x0000000000000000 );
  TEST_RR_OP( 10, addw, 0xffffffff80007ffe, 0x000000007fffffff, 0x0000000000007fff );

  TEST_RR_OP( 11, addw, 0xffffffff80007fff, 0xffffffff80000000, 0x0000000000007fff );
  TEST_RR_OP( 12, addw, 0x000000007fff7fff, 0x000000007fffffff, 0xffffffffffff8000 );

  TEST_RR_OP( 13, addw, 0xffffffffffffffff, 0x0000000000000000, 0xffffffffffffffff );
  TEST_RR_OP( 14, addw, 0x0000000000000000, 0xffffffffffffffff, 0x0000000000000001 );
  TEST_RR_OP( 15, addw, 0xfffffffffffffffe, 0xffffffffffffffff, 0xffffffffffffffff );

  TEST_RR_OP( 16, addw, 0xffffffff80000000, 0x0000000000000001, 0x000000007fffffff );


TEST_PASSFAIL

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
