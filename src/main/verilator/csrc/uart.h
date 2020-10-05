// See LICENSE for license details.

#ifndef _UART_H
#define _UART_H

/* ns16550a Register offsets */
#define UART_RHR   0x00  // Receiver Holding Register 
#define UART_THR   0x00  // Transmitter Holding Register 
#define UART_IER   0x01  // Interrupt Enable Register 
#define UART_ISR   0x02  // Interrupt Status Register
#define UART_FCR   0x02  // FIFO Control Register 
#define UART_LCR   0x03  // Line Control Register 
#define UART_MCR   0x04  // Modem Control Register 
#define UART_LSR   0x05  // Line Status Register 
#define UART_MSR   0x06  // Modem Status Register 
#define UART_SPR   0x07  // Scratch Pad Register 
#define UART_DLL   0x00  // Divisor LSB (LCR_DLAB) 
#define UART_DLM   0x01  // Divisor MSB (LCR_DLAB) 
#define UART_PSD   0x05  // Prescaler's Division Factor (LCR_DLAB) 

#endif /* _UART_H */