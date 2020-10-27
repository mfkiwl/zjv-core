#
# STEP#1: define the output directory area.
#
if {[llength $argv] > 0} {
  set outputDir [lindex $argv 0]
#   file mkdir $outputDir 
} else {
  puts "project output directory is not given!"
  return 1
}
if {[llength $argv] > 1} {
  set board [lindex $argv 1]
} else {
  puts "board type is not given!"
  return 1
}
# set outputDir ./Tutorial_Created_Data/cpu_project
# file mkdir $outputDir
create_project phvntom $outputDir -part xc7a100tcsg324-1 -force
#
# STEP#2: setup design sources and constraints
#
# add_files -fileset sim_1 ./Sources/hdl/cpu_tb.v
# add_files [ glob ./Sources/hdl/bftLib/*.vhdl ]
# add_files ./Sources/hdl/bft.vhdl
# add_files [ glob ./Sources/hdl/*.v ]
# add_files [ glob ./Sources/hdl/mgt/*.v ]
# add_files [ glob ./Sources/hdl/or1200/*.v ]
# add_files [ glob ./Sources/hdl/usbf/*.v ]
# add_files [ glob ./Sources/hdl/wb_conmax/*.v ]
add_files -fileset constrs_1 ./board/$board/constr/Nexys-A7-100T-Master.xdc
add_files [ glob ../build/verilog/*/*.v ] 
# add_files [ glob ../src/main/verilator/vsrc/*.v ] 

# set_property library bftLib [ get_files [ glob ./Sources/hdl/bftLib/*.vhdl ]]
#
# Physically import the files under project_cpu.srcs/sources_1/imports directory
import_files -force -norecurse
#
# Physically import bft_full.xdc under project_cpu.srcs/constrs_1/imports directory
# import_files -fileset constrs_1 -force -norecurse ./Sources/top_full.xdc
# Update compile order for the fileset 'sources_1'
set_property top top [current_fileset]
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1
#
# STEP#3: run synthesis and the default utilization report.
#
launch_runs synth_1
wait_on_run synth_1
#
# STEP#4: run logic optimization, placement, physical logic optimization, route and
# bitstream generation. Generates design checkpoints, utilization and timing
# reports, plus custom reports.
# set_property STEPS.PHYS_OPT_DESIGN.IS_ENABLED true [get_runs impl_1]
# set_property STEPS.OPT_DESIGN.TCL.PRE [pwd]/pre_opt_design.tcl [get_runs impl_1]
# set_property STEPS.OPT_DESIGN.TCL.POST [pwd]/post_opt_design.tcl [get_runs impl_1]
# set_property STEPS.PLACE_DESIGN.TCL.POST [pwd]/post_place_design.tcl [get_runs impl_1]
# set_property STEPS.PHYS_OPT_DESIGN.TCL.POST [pwd]/post_phys_opt_design.tcl [get_runs impl_1]
# set_property STEPS.ROUTE_DESIGN.TCL.POST [pwd]/post_route_design.tcl [get_runs impl_1]
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1
puts "Implementation done!"