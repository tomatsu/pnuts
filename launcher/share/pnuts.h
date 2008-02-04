#ifndef _PNUTS_H_
#define _PNUTS_H_

#define PNUTS_JAVA_COMMAND "java"
#define PNUTS_DEFAULT_MODULE "pnuts.tools"
#define PNUTS_JAR_FILE "pnuts.jar"
#define PNUTS_MODULE_DIR "modules"
#define BUF_MARGIN 256

#include "pnuts_md.h"

typedef struct {
    char* block;
    int pos;
    int size;
} buffer_t;

extern char* getPnutsHome();
extern int spawnPnuts_md(char** vmArgv);

#endif
