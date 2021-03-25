#include <sys/types.h>
#include <stdio.h>
#include <sys/socket.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include "processcmd.h"
#include "handler.h"
#define BUF_LEN 1024
#define USER_NAME "cs317"
#define BACKLOG 10     // how many pending connections queue will hold

void execute_cmd(int fd,char * root_dir) {
    struct clientStatus* CStatus = malloc(sizeof(struct clientStatus));
    init(CStatus);
    char buf[BUF_LEN];
    char* fk;
    char* cmd;
    char* temp;
    while(!CStatus->quit){
        memset(buf, 0, sizeof buf);
        int returnNum;
        if((returnNum = recv(fd, buf, BUF_LEN-1, 0)) == -1) {
            perror("recv");
            send_status(fd,500);
            continue;
        }

        // change cmd to uppercase
        strcpy(temp,buf);
        int i = 0;
        cmd = strtok(temp, " ");
        while(i<4){
            cmd[i] = toupper(cmd[i]);
            ++i;
        }

        // quit in all case
        if(strncmp(cmd,"QUIT",4)==0){
            CStatus->quit = true;
            break;
        }

        // handle case when not login
        if(!CStatus->loggedIn){
            if(strncmp(cmd,"USER",4)==0){
                char *username;
                username = strtok(NULL, " ");

                //https://stackoverflow.com/questions/2693776/removing-trailing-newline-character-from-fgets-input
                //The function counts the number of characters until it hits a '\r' or a '\n' (in other words, it finds the first '\r' or '\n').
                //If it doesn't hit anything, it stops at the '\0' (returning the length of the string).
                username[strcspn(username, "\r\n")] = 0;

                if(strncmp(username,USER_NAME,strlen(username))==0) {
                    send_status(fd,230);
                    CStatus->loggedIn = true;
                    continue;
                } else {
                    CStatus->loggedIn = false;
                    send_status(fd,530);
                    continue;
                }
            }else{
                send_status(fd,530);
            }

        // handle case after login
        }else{
            if(strncmp(cmd,"USER",4)==0){
                //Not support changer user
                send_status(fd,500);

            }else if(strncmp(cmd,"CWD",3)==0){
                handle_CWD(buf,fd);

            }else if(strncmp(cmd,"CDUP",4)==0){
                handle_CDUP(buf,fd,root_dir);
                
            }else if(strncmp(cmd,"TYPE",4)==0){
                handle_TYPE(buf,fd); 

            }else if(strncmp(cmd,"MODE",4)==0){
                handle_MODE(buf,fd);
                
            }else if(strncmp(cmd,"STRU",4)==0){
                handle_STRU(buf,fd);
                
            }else if(strncmp(cmd,"RETR",4)==0){
                handle_RETR(buf,fd,CStatus);

            }else if(strncmp(cmd,"PASV",4)==0){
                handle_PASV(buf,fd,CStatus);

            }else if(strncmp(cmd,"NLST",4)==0){
                handle_NLST(buf,fd,CStatus);

            }else{
                send_status(fd,500);
            }
        }
    }
    free(CStatus);
    return;
}




/* HELPER FUNTION*/

void init(struct clientStatus* CStatus){
    CStatus->loggedIn = false;
    CStatus->quit = false;
    CStatus->passive = false;
    CStatus->socket = -1;
    CStatus->channel = -1;
}

int count_arg(char* buf) {
    //for trailing zero
	char temp[strlen(buf) + 1];
	strcpy(temp, buf);
	char* arg = strtok(temp, " ");
	int count = 0;
	while(arg != NULL) {
		arg = strtok(NULL, " ");
		count++;
	}
	return count;
}

char* getArg(char* buf) {
    //for trailing zero
	char temp[strlen(buf) + 1];
    strcpy(temp, buf);
    strtok(temp, " ");
    char* arg = strtok(NULL, " ");
    arg[strcspn(arg, "\r\n")] = 0;
    return arg;
}


void send_status(int sockfd, int status) {
    char* str;
    switch (status) {
    case 150:
        str = "150 File status okay; about to open data connection.\n";
        break;
    case 200:
        str = "200 Command okay.\n";
        break;
    case 220:
        str = "220 Service ready for new user.\n";
        break;
    case 221:
        str = "221 Service closing control connection.\n";
        break;
    case 225:
        str = "225 Can't open data connection.\n";
        break;
    case 226:
        str = "226 Directory send OK.\n";
        break;
    case 227:
        str = "227 Entering Passive Mode.\n";
        break;
    case 230:
        str = "230 User logged in, proceed.\n";
        break;
    case 250:
        str = "250 Requested file action okay, completed.\n";
        break;
    case 425:
        str = "425 Can't open data connection.\n";
        break;
    case 426:
        str = "426 Connection closed; transfer aborted.\n";
        break;
    case 451:
        str = "451 Requested action aborted: local error in processing.\n";
        break;
    case 500:
        str = "500 Syntax error, command unrecognized.\n";
        break;
    case 501:
        str = "501 Syntax error in parameters or arguments.\n";
        break;
    case 504:
        str = "504 Command not implemented for that parameter.\n";
        break;
    case 530:
        str = "530 Not logged in.\n";
        break;
    case 550:
        str = "550 Requested action not taken.\n";
        break;
    }
    if (send(sockfd, str, strlen(str), 0) == -1) {
        perror("send");
    }
}
