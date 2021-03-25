#ifndef __PROCESSCMD_H__
#define __PROCESSCMD_H__
#include <stdbool.h>
#include <stdint.h>
void send_status(int,int);
void execute_cmd(int,char*);
struct clientStatus {
    bool loggedIn;
    bool quit;
    bool passive ;
    int socket;
    int channel;
};
void init(struct clientStatus*);
int count_arg(char*);
char* getArg(char*);


#endif

