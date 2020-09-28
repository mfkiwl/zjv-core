#include "test_macros.h"
#include "riscv_test.h"

.macro init; 
.endm

.section .text.init;
.align  6;
.globl _start;

_start:
  TEST_IMM_OP( 2,  addiw, 0x00000000, 0x00000000, 0x000 );
  TEST_IMM_OP( 3,  addiw, 0x00000002, 0x00000001, 0x001 );
  TEST_IMM_OP( 4,  addiw, 0x0000000a, 0x00000003, 0x007 );

  TEST_IMM_OP( 5,  addiw, 0xfffffffffffff800, 0x0000000000000000, 0x800 );
  TEST_IMM_OP( 6,  addiw, 0xffffffff80000000, 0xffffffff80000000, 0x000 );
  TEST_IMM_OP( 7,  addiw, 0x000000007ffff800, 0xffffffff80000000, 0x800 );

  TEST_IMM_OP( 8,  addiw, 0x00000000000007ff, 0x00000000, 0x7ff );
  TEST_IMM_OP( 9,  addiw, 0x000000007fffffff, 0x7fffffff, 0x000 );
  TEST_IMM_OP( 10, addiw, 0xffffffff800007fe, 0x7fffffff, 0x7ff );

  TEST_IMM_OP( 11, addiw, 0xffffffff800007ff, 0xffffffff80000000, 0x7ff );
  TEST_IMM_OP( 12, addiw, 0x000000007ffff7ff, 0x000000007fffffff, 0x800 );

  TEST_IMM_OP( 13, addiw, 0xffffffffffffffff, 0x0000000000000000, 0xfff );
  TEST_IMM_OP( 14, addiw, 0x0000000000000000, 0xffffffffffffffff, 0x001 );
  TEST_IMM_OP( 15, addiw, 0xfffffffffffffffe, 0xffffffffffffffff, 0xfff );

  TEST_IMM_OP( 16, addiw, 0xffffffff80000000, 0x7fffffff, 0x001 );


TEST_PASSFAIL

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
