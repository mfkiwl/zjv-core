module Test ();

    reg resetn;
    reg clk;
    reg uart_RX;
    wire uart_TX;

    initial begin
        uart_RX = 1;
        resetn = 0;
        clk = 1;
        #20;
        resetn = 1;
    end
    always #5 clk = ~clk;

    SoC dut (
        .clock_100(clk),
        .btn_resetn(resetn),
        .uart_RX(uart_RX),
        .uart_TX(uart_TX)
    );

endmodule