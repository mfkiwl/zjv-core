wrkdir	:=	$(CURDIR)/build

.PHONY: clean build generate_verilog

generate_verilog:
	sbt "test:runMain Sodor.elaborate"

clean:
	rm -rf build

