#include <stdio.h>
#include <stdlib.h>

#define QUEUE_SIZE 1024
static char queue[QUEUE_SIZE] = {};
static int front = 0, rear = 0;
static char interrupt_enable;     // b001
static char interrupt_status = 1; // b010 R
static char fifo_control;         // b010 W
static char line_control;         // b011
static char modem_control;        // b100
static char line_status = '\x60'; // b101 R
static char modem_status;         // b110 R
static char scratch_pad;          // b111

static char divisor_latch_low;  // b000
static char divisor_latch_high; // b001
static char prescalar_division; // b101 W

static void uart_enqueue(char ch)
{
    int next = (rear + 1) % QUEUE_SIZE;
    if (next != front)
    {
        queue[rear] = ch;
        rear = next;
    }
}

static int uart_dequeue(void)
{
    int k = 0;
    if (front != rear)
    {
        k = queue[front];
        front = (front + 1) % QUEUE_SIZE;
    }
    // else
    // {
    //     static int last = 0;
    //     k = "root\n"[last++];
    //     if (last == 5)
    //     {
    //         last = 0;
    //     }
    // }
    return k;
}

static void update_value()
{
}

extern "C" void uart_getc(char addr, char *data) // read
{
    switch (addr)
    {
    case 0:
        if (line_control < 0)
        {
            *data = divisor_latch_low;
        }
        else
        {
            *data = uart_dequeue();
        }
        break;
    case 1:
        if (line_control < 0)
        {
            *data = divisor_latch_high;
        }
        else
        {
            *data = interrupt_enable;
        }
        break;
    case 2:
        *data = interrupt_status;
        break;
    case 3:
        *data = line_control;
        break;
    case 4:
        *data = modem_control;
        break;
    case 5:
        *data = line_status;
        break;
    case 6:
        *data = modem_status;
        break;
    case 7:
        *data = scratch_pad;
        break;

    default:
        break;
    }
    // printf("In uart_getc: addr = %d, data = %d\n", addr, *data);
}

extern "C" void uart_putc(char addr, char data) // write
{
    switch (addr)
    {
    case 0:
        if (line_control < 0)
        {
            divisor_latch_low = data;
        }
        // else
        // {
        //     uart_enqueue(data);
        // }
        break;
    case 1:
        if (line_control < 0)
        {
            divisor_latch_high = data;
        }
        else
        {
            interrupt_enable = data;
        }
        break;
    case 2:
        fifo_control = data;
        break;
    case 3:
        line_control = data;
        break;
    case 4:
        modem_control = data;
        break;
    case 5:
        if (line_control < 0)
        {
            prescalar_division = data & 0x0f;
        }
    case 7:
        scratch_pad = data;
        break;

    case 6:
    default:
        break;
    }
    update_value();
    // printf("In uart_putc: addr = %d, data = %d\n", addr, data);
}