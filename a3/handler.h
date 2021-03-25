#ifndef __HANDLER_H__
#define __HANDLER_H__
#include <stdbool.h>
void handle_CWD(char*,int);
void handle_CDUP(char*,int,char*);
void handle_TYPE(char*,int);
void handle_MODE(char*,int);
void handle_STRU(char*,int);
void handle_RETR(char*,int,struct clientStatus*);
void handle_PASV(char*,int,struct clientStatus*);
void handle_NLST(char*,int,struct clientStatus*);
#endif

