#include <stdint.h>
#include <stdio.h>
#include <simmem.h>

typedef uint64_t paddr_t;

htif_simmem_t *mem;
memif_t *memif;

void init_ram(const std::string img)
{

  mem = new htif_simmem_t();
  memif = new memif_t(mem);
  reg_t entry;
  load_elf(img.c_str(), memif, &entry);

  fprintf(stderr, "[SimMem] load elf %s\n", img.c_str());

  //  mem->mem_check();
}


static int getOffset (paddr_t mask) {
  int res = 0;
  if (mask)
    while ((mask & 1) == 0)
      mask >>= 1, res++;
  return res;
}

static int getSize (paddr_t mask) {
  if (mask & (mask >> 63))
    return memDouble;
  else if (mask & (mask >> 31))
    return memWord;
  else if (mask & (mask >> 15))
    return memHalf;
  else if (mask & (mask >> 7))
    return memByte;
  else 
    return memDouble;
}

extern "C" void SimMemAccess(paddr_t raddr, paddr_t *rdata, paddr_t waddr, paddr_t wdata, paddr_t wmask, uint8_t wen)
{

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
    RACCESS(raddr, memDouble, rdata);
    // fprintf(stderr, "Read Done %lx -> %lx\n", raddr, *rdata);
  }

  if (waddr != 0xdeadbeefL)
  {
    int offset = getOffset(wmask);
    int size   = getSize(wmask);
    WACCESS(waddr + (offset >> 3), size, wen, wdata >> offset);
    // fprintf(stderr, "Write Done %lx -> %lx, offset = %d, size = %d\n", waddr, wdata, offset, size);
  }

  fprintf(stderr, "[Memory Access] \n");
  fprintf(stderr, "raddr %lx rdata %lx\n", raddr, *rdata);
  fprintf(stderr, "waddr %lx wdata %lx mask %lx wen %d\n", waddr, wdata, wmask, wen);
}
