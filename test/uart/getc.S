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


1:    	lb   t1, 0(s0)
	beq  t1, zero, 1b	


	addi t0, zero, '['
	sb   t0, 0(s0)
	sb   t1, 0(s0)
	addi t0, zero, ']'
	sb   t0, 0(s0)

	j 1b


RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
