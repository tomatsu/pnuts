#ifndef _PNUTS_MD_H_
#define _PNUTS_MD_H_

#ifdef darwin
#define LIBRARY_PATH "DYLD_LIBRARY_PATH"
#else
#define LIBRARY_PATH "LD_LIBRARY_PATH"
#endif
#define PATHSEP ":"

#endif
