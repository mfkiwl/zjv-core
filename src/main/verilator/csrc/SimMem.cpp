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
}

extern "C"
void SimMemAccess (paddr_t iaddr, paddr_t *idata, paddr_t imask,
                   paddr_t daddr, paddr_t *drdata, paddr_t dwdata, paddr_t dmask, uint8_t dwen) {

  *idata = ram[iaddr] & imask;
  *drdata = ram[daddr] & dmask;

  if (dwen) {
    ram[daddr] = (ram[daddr] & ~dmask) | (dwdata & dmask);
  }
}


