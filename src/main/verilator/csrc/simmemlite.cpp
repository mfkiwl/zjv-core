#include <stdint.h>
#include <stdio.h>
#include <simmem.h>

typedef uint64_t paddr_t;

htif_simmem_t *mem;
memif_t *memif;

void init_ram(const char *img)
{

  mem = new htif_simmem_t();
  memif = new memif_t(mem);
  reg_t entry;
  load_elf(img, memif, &entry);

  printf("[SimMem] load elf %s\n", img);

  //  mem->mem_check();
}

extern "C" void SimMemAccess(paddr_t raddr, paddr_t *rdata, paddr_t waddr, paddr_t wdata, paddr_t wmask, uint8_t wen)
{

  printf("[Memory Access] \n");
  printf("iaddr %lx type %lx\n", raddr, rdata);
  printf("daddr %lx type %lx dwdata %lx wen %d\n", waddr, wdata, wmask, wen);

#define RACCESS(addr, memtype, rdata)  \
  if (memtype == memByte)              \
    *rdata = memif->read_int8(addr);   \
  else if (memtype == memByteU)        \
    *rdata = memif->read_uint8(addr);  \
  else if (memtype == memHalf)         \
    *rdata = memif->read_int16(addr);  \
  else if (memtype == memHalfU)        \
    *rdata = memif->read_uint16(addr); \
  else if (memtype == memWord)         \
    *rdata = memif->read_int32(addr);  \
  else if (memtype == memWordU)        \
    *rdata = memif->read_uint32(addr); \
  else if (memtype == memDouble)       \
    *rdata = memif->read_int64(addr);  \
  else if (memtype == memXXX)          \
    *rdata = memif->read_uint64(addr); \
  else                                 \
    throw std::runtime_error("Unexpect Read Memory Access Type %d\n");

#define WACCESS(addr, memtype, wen, wdata)                           \
  if (wen)                                                           \
  {                                                                  \
    if (memtype == memByte)                                          \
      memif->write_int8(addr, wdata);                                \
    else if (memtype == memByteU)                                    \
      memif->write_uint8(addr, wdata);                               \
    else if (memtype == memHalf)                                     \
      memif->write_int16(addr, wdata);                               \
    else if (memtype == memHalfU)                                    \
      memif->write_uint16(addr, wdata);                              \
    else if (memtype == memWord)                                     \
      memif->write_int32(addr, wdata);                               \
    else if (memtype == memWordU)                                    \
      memif->write_uint32(addr, wdata);                              \
    else if (memtype == memDouble)                                   \
      memif->write_int64(addr, wdata);                               \
    else if (memtype == memXXX)                                      \
      memif->write_uint64(addr, wdata);                              \
    else                                                             \
      throw std::runtime_error("Unexpect Write Memory Access Type"); \
  }

  if (raddr != 0xdeadbeefL)
  {
    // daddr = daddr - mem->get_base();
    RACCESS(raddr, memWord, rdata);
    printf("Read Done %lx -> %lx\n", raddr, *rdata);
  }

  if (waddr != 0xdeadbeefL)
  {
    WACCESS(waddr, memWord, wen, wdata & wmask);
    printf("Write Done %lx -> %lx\n", waddr, wdata);
  }
}

/*

// old version

#define RAMSIZE (128 * 1024 * 1024)
static paddr_t ram[RAMSIZE / sizeof(paddr_t)] = {0x07b08093f8508093L};

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
    // printf("Read Done\n");
  }
}

*/