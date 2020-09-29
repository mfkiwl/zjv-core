#include <stdint.h>
#include <stdio.h>
#include <simmem.h>

typedef uint64_t paddr_t;

htif_simmem_t *mem;
memif_t *memif;


void init_ram(const char *img) {

  mem = new htif_simmem_t();
  memif = new memif_t(mem);
  reg_t entry;
  load_elf(img, memif, &entry);

#ifdef ZJV_DEBUG
  printf("[SimMem] load elf %s\n", img);
#endif
}

const char* getType(unsigned long memtype) {
  if      (memtype == memByte)      return "byte";     
  else if (memtype == memByteU)     return "unsigned byte";  
  else if (memtype == memHalf)      return "half";  
  else if (memtype == memHalfU)     return "unsigned half"; 
  else if (memtype == memWord)      return "word";  
  else if (memtype == memWordU)     return "unsigned word"; 
  else if (memtype == memDouble)    return "double";  
  else if (memtype == memXXX)       return "default"; 
  else                              return "ERROR!";
} 


extern "C"
void SimMemAccess (paddr_t iaddr, paddr_t *idata,  paddr_t itype,
                   paddr_t daddr, paddr_t *drdata, paddr_t dwdata, paddr_t dtype, uint8_t dwen) {

#ifdef ZJV_DEBUG
//  printf("[Memory Access] \n");
//  printf("iaddr %016lx %s\n", iaddr, getType(itype));
//  printf("daddr %016lx %s dwdata %016lx wen %d\n", daddr, getType(dtype), dwdata, dwen);
#endif

#define RACCESS(addr, memtype, rdata)                                     \
  if      (memtype == memByte)       *rdata = memif->read_int8(addr);     \
  else if (memtype == memByteU)      *rdata = memif->read_uint8(addr);    \
  else if (memtype == memHalf)       *rdata = memif->read_int16(addr);    \
  else if (memtype == memHalfU)      *rdata = memif->read_uint16(addr);   \
  else if (memtype == memWord)       *rdata = memif->read_int32(addr);    \
  else if (memtype == memWordU)      *rdata = memif->read_uint32(addr);   \
  else if (memtype == memDouble)     *rdata = memif->read_int64(addr);    \
  else if (memtype == memXXX)        *rdata = memif->read_uint64(addr);   \
  else throw std::runtime_error("Unexpect Read Memory Access Type %d\n"); 
  

#define WACCESS(addr, memtype, wen, wdata)                                \
  if (wen) {                                                              \
    if      (memtype == memByte)       memif->write_int8(addr, wdata);    \
    else if (memtype == memByteU)      memif->write_uint8(addr, wdata);   \
    else if (memtype == memHalf)       memif->write_int16(addr, wdata);   \
    else if (memtype == memHalfU)      memif->write_uint16(addr, wdata);  \
    else if (memtype == memWord)       memif->write_int32(addr, wdata);   \
    else if (memtype == memWordU)      memif->write_uint32(addr, wdata);  \
    else if (memtype == memDouble)     memif->write_int64(addr, wdata);   \
    else if (memtype == memXXX)        memif->write_uint64(addr, wdata);  \
    else throw std::runtime_error("Unexpect Write Memory Access Type");   \
  }


  if (iaddr != 0xdeadbeefL) {
    // iaddr = iaddr - mem->get_base();
    RACCESS(iaddr, itype, idata);
    #ifdef ZJV_DEBUG
      // printf("[READ] iaddr %016lx %016lx\n", iaddr, *idata);
    #endif
  }

  if (daddr != 0xdeadbeefL) {
    // daddr = daddr - mem->get_base();
    WACCESS(daddr, dtype, dwen, dwdata);
    RACCESS(daddr, dtype, drdata);
    #ifdef ZJV_DEBUG
      // if (dwen)
      //   printf("[WRITE] daddr %016lx %016lx\n", daddr, *drdata);
      // printf("[READ] iaddr %016lx %016lx\n", daddr, *drdata);
    #endif
  }

}