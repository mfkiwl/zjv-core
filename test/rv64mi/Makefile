rv64mi_test_target = \
	$(rv64mi_exception) \
	$(rv64mi_z_type) \

rv64mi_exception = \
    access  \
	sbreak  \
	scall   \
    illegal \
	ma_addr     \
	ma_fetch    \
	#breakpoint  \

rv64mi_z_type = \
	csr   \
	mcsr   \


rv64mi_test_src = $(addsuffix .S, $(rv64mi_sc_tests))
