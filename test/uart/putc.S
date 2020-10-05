# See LICENSE for license details.

#*****************************************************************************
# add.S
#-----------------------------------------------------------------------------
#
# Test add instruction.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64U
RVTEST_BARE_CODE_BEGIN

	lui  s0, 0x10000

	addi t0, zero, 'H'
	sb   t0, 0(s0)
	addi t0, zero, 'e'
	sb   t0, 0(s0)
	addi t0, zero, 'l'
	sb   t0, 0(s0)
	addi t0, zero, 'l'
	sb   t0, 0(s0)
	addi t0, zero, 'o'
	sb   t0, 0(s0)
	addi t0, zero, ','
	sb   t0, 0(s0)
	addi t0, zero, ' '
	sb   t0, 0(s0)
	addi t0, zero, 'W'
	sb   t0, 0(s0)
	addi t0, zero, 'o'
	sb   t0, 0(s0)
	addi t0, zero, 'r'
	sb   t0, 0(s0)
	addi t0, zero, 'l'
	sb   t0, 0(s0)	
	addi t0, zero, 'd'
	sb   t0, 0(s0)
	addi t0, zero, '!'
	sb   t0, 0(s0)

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
