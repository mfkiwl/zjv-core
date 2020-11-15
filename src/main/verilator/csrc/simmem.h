#ifndef _SIMMEM_H
#define _SIMMEM_H  

#include <iostream>
#include <map>
#include <vector>
#include <stdlib.h>
#include <cassert>

#include "fesvr/memif.h"
#include "fesvr/elfloader.h"

#define memXXX     0
#define memByte    1
#define memHalf    2
#define memWord    3
#define memDouble  4
#define memByteU   5
#define memHalfU   6
#define memWordU   7


class htif_simmem_t : public chunked_memif_t
{
public:
  htif_simmem_t(size_t w=8, size_t b=0x40000000);

  void mem_check () {
    for(auto entry : mem) {
        printf("0x%lx ", entry.first);
        for (auto ele : entry.second) {
            printf("%02hhx ", ele);
        }
        printf("\n");    
    }
    printf("[CHECK] %ld\n", mem.size());
  }

  size_t get_base() {return base;}

protected:
  size_t base;
  size_t width;

  std::map<addr_t,std::vector<char>> mem;

  void read_chunk(addr_t taddr, size_t len, void* dst);
  void write_chunk(addr_t taddr, size_t len, const void* src);
  void clear_chunk(addr_t taddr, size_t len);

  size_t chunk_max_size() { return width; }
  size_t chunk_align()    { return width; }
};




#endif // _SIMMEM_H