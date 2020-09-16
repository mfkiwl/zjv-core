#include <stdint.h>
#include <stdio.h>

#define RAMSIZE (128 * 1024 * 1024)

typedef uint64_t paddr_t;
static paddr_t ram[RAMSIZE / sizeof(paddr_t)] = {0x07b08093f8508093L};

// static long img_size = 0;
// void* get_img_start() { return &ram[0]; }
// long  get_img_size()  { return img_size; }

void init_ram(const char *img) {
  printf("Init Successfully\n");

  // TODO: load elf
}

extern "C"
void SimMemAccess (paddr_t iaddr, paddr_t *idata, paddr_t imask,
                   paddr_t daddr, paddr_t *drdata, paddr_t dwdata, paddr_t dmask, uint8_t dwen) {

  printf("[Memory Access] \n");
  printf("iaddr %lx imask %lx\n", iaddr, dmask);
  printf("daddr %lx dmask %lx dwdata %lx wen %d\n", daddr, dmask, dwdata, dwen);
  if (iaddr >= 0x80000000L || daddr >= 0x80000000L) {
//    printf("Read imem[%x] = %lx\n", iaddr-0x80000000L, ram[iaddr-0x80000000L]);
//    printf("Read dmem[%x] = %lx\n", daddr-0x80000000L, ram[daddr-0x80000000L]);
    if (iaddr-0x80000000 < 10)
        *idata = ram[iaddr-0x80000000] & imask;
    if (daddr-0x80000000 < 10)
        *drdata = ram[daddr-0x80000000] & dmask;

    if (dwen) {
      ram[daddr] = (ram[daddr-0x80000000] & ~dmask) | (dwdata & dmask);
    }
    printf("Read Done\n");
  }

}


