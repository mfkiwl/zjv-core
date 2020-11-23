#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "simuart.h"
#include <fstream>
#include <iostream>

#include <unistd.h>
#include <sys/ioctl.h>

std::ifstream file_fifo; 

static char interrupt_enable;               // b001
static char interrupt_status = 1;           // b010 R
static char fifo_control;                   // b010 W
static char line_control;                   // b011
static char modem_control;                  // b100
static char line_status = '\x61';           // b101 R
static char modem_status;                   // b110 R
static char scratch_pad;                    // b111
static char divisor_latch_low = '\x01';     // b000
static char divisor_latch_high = '\x01';    // b001
static char prescalar_division;             // b101 W

void init_uart (const std::string file_path) {

    std::ofstream init_cmd;
    init_cmd.open (file_path, std::ios::out | std::ios::trunc);
    // init_cmd << "echo zjv\nls\nps\n";
    init_cmd.close();

    file_fifo.open(file_path);
    if (file_fifo.is_open()) {
      printf("[UART] open uart file fifo %s\n", file_path.c_str());
    }
}

bool data_in_keyboard() {
    int amt;
    if ((ioctl(0, FIONREAD, &amt) == 0) && (amt > 0))
        return true;
    else
        return false;
}

static void uart_dequeue(char* data) {
    if (file_fifo.is_open()) {
        if (!file_fifo.eof()) 
            file_fifo.get(*data);
        if (file_fifo.eof()) {
            file_fifo.close();
            fprintf(stderr, "[UART] close fifo \n");
        }
    }
    else {
        if (data_in_keyboard())
            read(0, data, 1);
    }
}

extern "C" void uart_getc(char addr, char *data) // read
{
    *data = 0;
    switch (addr)
    {
    case UART_RHR: // 0
        if (line_control < 0)
            *data = divisor_latch_low;
        else
            uart_dequeue(data);
        break;
    case UART_IER: // 1
        if (line_control < 0)
            *data = divisor_latch_high;
        else
            *data = interrupt_enable;
        break;
    case UART_ISR: // 2
        *data = interrupt_status;
        break;
    case UART_LCR: // 3
        *data = line_control;
        break;
    case UART_MCR: // 4
        *data = modem_control;
        break;
    case UART_LSR: // 5
        *data = 0x40 | 0x20;
        if ((file_fifo.is_open() && !file_fifo.eof()) || data_in_keyboard()) {
            *data |= 1; 
        }
        break;
    case UART_MSR: // 6
        *data = modem_status;
        break;
    case UART_SPR: // 7
        *data = scratch_pad;
        break;

    default:
        break;
    }
    // printf("[UART] In uart_getc: addr = 0x%x, data = 0x%x\n", addr, *data);

}

extern "C" void uart_putc(char addr, char data) // write
{
    switch (addr)
    {
    case UART_THR: // 0
        if (line_control < 0)
            divisor_latch_low = data;
        else {
            fprintf(stdout, "%c", data);
            fflush(stdout);
        }
        break;
    case UART_IER: // 1
        if (line_control < 0)
            divisor_latch_high = data;
        else
            interrupt_enable = data;
        break;
    case UART_FCR: // 2
        fifo_control = data;
        break;
    case UART_LCR: // 3
        line_control = data;
        break;
    case UART_MCR: // 4
        modem_control = data;
        break;
    case UART_PSD: // 5
        if (line_control < 0)
            prescalar_division = data & 0x0f;
        break;
    case UART_SPR: // 7
        scratch_pad = data;
        break;
    default:
        // printf("[UART] Store illegal address 0x%x[%x] \n", addr, data);
        break;
    }
    // printf("[UART] In uart_putc: addr = 0x%x, data = 0x%x\n", addr, data);
}

extern "C"  void uart_irq (char* irq) {

    if (file_fifo.is_open() && !file_fifo.eof()) {
        *irq = -1;
    }
    else if (data_in_keyboard()) {
        *irq = -1;
    }
    else {
        *irq = 0;
    }
}