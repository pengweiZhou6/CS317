#include <sys/types.h>
#include <stdio.h>
#include <sys/socket.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/time.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/sendfile.h>
#include "usage.h"
#include "processcmd.h"
#include "handler.h"
#include "dir.h"
#define BUF_LEN 1024
#define USER_NAME "cs317"
#define BACKLOG 10     // how many pending connections queue will hold


void handle_CWD(char* buf,int fd){
    if(count_arg(buf) == 2){
        char* arg = getArg(buf);
        char *word1 = "../";
        char *word2 = "./";
        char curr[BUF_LEN];
        getcwd(curr, sizeof(curr));
       
        //not accept any CWD command that starts with ./ or ../ or contains ../ in it. 
        if(strstr(arg, word1) != NULL || strstr(arg, word2) != NULL) {
            send_status(fd,550);
        }else if(chdir(arg) == 0) {
            getcwd(curr, sizeof(curr));
            send_status(fd,250);
        }else{
            getcwd(curr, sizeof(curr));
	        send_status(fd,550);
        }
    }else{
        send_status(fd,501);
    }
}

void handle_CDUP(char* buf,int fd,char* root_dir){
    if(count_arg(buf) == 1){
        char curr[BUF_LEN];
        getcwd(curr, sizeof(curr));

        //For security reasons do not allow a CDUP command to set 
        //the working directory to be the parent directory of 
        //where your ftp server is started from.
        if (!strcmp(curr, root_dir)){
            send_status(fd,550);
        }else{
            if(chdir("../") == 0) {
                send_status(fd,200);
            } else {
                send_status(fd,550);
            }
        }
    }else{
        send_status(fd,501);
    }
}

void handle_TYPE(char* buf,int fd){
    if(count_arg(buf) == 2){
        char* arg = getArg(buf);
        
        //only support the Image and ASCII type (3.1.1, 3.1.1.3)
        if ((strcmp(arg, "A") == 0) || (strcmp(arg, "a") == 0)
            ||(strcmp(arg, "I") == 0) ||(strcmp(arg, "i") == 0)){
            send_status(fd,200);
        }else if ((strcmp(arg, "E") == 0) || (strcmp(arg, "e") == 0)
        || (strcmp(arg, "L") == 0) || (strcmp(arg, "l") == 0)){
            send_status(fd,504);
        }else{
            send_status(fd,501);
        }
    }else{
        send_status(fd,501);
    }
}

void handle_MODE(char* buf,int fd){
    if(count_arg(buf) == 2){
        char* arg = getArg(buf);

        //only support Stream mode (3.4.1)
        if ((strcmp(arg, "S") == 0) || (strcmp(arg, "s") == 0)){
            send_status(fd,200);
        }else if ((strcmp(arg, "B") == 0) || (strcmp(arg, "b") == 0)
            ||(strcmp(arg, "C") == 0) ||(strcmp(arg, "c") == 0)){
            send_status(fd,504);
        }else{
            send_status(fd,501);
        }
    }else{
        send_status(fd,501);
    }
}

void handle_STRU(char* buf,int fd){
    if(count_arg(buf) == 2){
        char* arg = getArg(buf);
        
        //only support File structure type (3.1.2, 3.1.2.1)
        if ((strcmp(arg, "F") == 0) || (strcmp(arg, "f") == 0)){
            send_status(fd,200);
        }else if ((strcmp(arg, "R") == 0) || (strcmp(arg, "r") == 0)
            ||(strcmp(arg, "P") == 0) ||(strcmp(arg, "p") == 0)){
            send_status(fd,504);
        }else{
            send_status(fd,501);
        }
    }else{
        send_status(fd,501);
    }
}


void handle_RETR(char* buf,int fd,struct clientStatus* CStatus){
    if(CStatus->passive){
        if (count_arg(buf) == 2) {

            char* arg = getArg(buf);
            char* msg;
            struct stat file_stat;
            int sent_bytes = 0;
            int file = open(arg, O_RDONLY);

            if (file == -1) {
                send_status(fd,550);
            } 

            // http://stackoverflow.com/a/11965442
            /* Get file stats */
            if (fstat(file, &file_stat) < 0){
                perror("fstat");
            }

            char heading[100];
            strcpy(heading, "150 Opening BINARY mode data connection for ");
            strcat(heading, arg);
            strcat(heading, " ("); 
            char temp[10];
            int size = file_stat.st_size;
            snprintf(temp, 10, "%d", size);
            strcat(heading, temp);
            strcat(heading, " bytes).\n");
            if (send(fd, heading, strlen(heading), 0) == -1) {
                perror("send");
            }

            off_t offset = 0;
            int remain_data = file_stat.st_size;
            while (((sent_bytes  = sendfile(CStatus->socket, file, &offset, 1024)) > 0) 
                && (remain_data > 0)) {
                remain_data -= sent_bytes;
            }
            
            if(close(file)) {
                perror("close file");
            }

            strcpy(heading, "226 Transfer complete.\n");
            if (send(fd, heading, strlen(heading), 0) == -1) {
                perror("send");
            }
          

            CStatus->passive = false;
            if(CStatus->channel != -1){
                close(CStatus->channel);
                CStatus->channel = -1;
            }
            if(CStatus->socket != -1){
                close(CStatus->socket);
                CStatus->socket = -1;
            }

            CStatus->passive = false;
            
        }else{
            send_status(fd,501);
        }
    }else{
        send_status(fd,425);
    }
}


void handle_PASV(char* buf,int fd,struct clientStatus* CStatus){

    //only support the version of NLST with no command parameters.
    if (count_arg(buf) == 1) {

        // in case socket open
        if(CStatus->socket != -1){
            close(CStatus->socket);
            CStatus->socket = -1;
        }

        if(CStatus->channel != -1){
            close(CStatus->channel);
            CStatus->channel = -1;
        }

        if((CStatus->channel = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
            perror("open socket");
        }
        // https://docs.oracle.com/cd/E19620-01/805-4041/6j3r8iu2l/index.html

        /* Get local IP. */
        struct sockaddr_in local;
        socklen_t length = sizeof local;
        if(getsockname(fd, (struct sockaddr *)&local, &length) != 0){
            perror("get local ip");
        }

        char adr[INET_ADDRSTRLEN];
        memset(adr, 0, sizeof(adr));
        inet_ntop(AF_INET, &(local.sin_addr), adr, INET_ADDRSTRLEN);



        /* Create socket. */
        CStatus->channel = socket(AF_INET, SOCK_STREAM, 0);
        if (CStatus->channel == -1) {
           perror("opening stream socket");
        }

        struct sockaddr_in server;
        /* Bind socket using wildcards.*/
        server.sin_family = AF_INET;
        server.sin_addr.s_addr = inet_addr(adr);
        server.sin_port = ntohs(0);
        if (bind(CStatus->channel, (struct sockaddr *) &server, sizeof server)== -1) {
           perror("binding stream socket");
        }

        /* Get fd IP. */

        struct sockaddr_in fd_sockaddr;
        length = sizeof(fd_sockaddr);
        if(getsockname(fd, (struct sockaddr *)&fd_sockaddr, &length) != 0){
            perror("ip");
        }

        memset(adr, 0, sizeof(adr));
        inet_ntop(AF_INET, &(fd_sockaddr.sin_addr), adr, INET_ADDRSTRLEN);


        /*Start listening. */
        if (listen(CStatus->channel,BACKLOG) == -1) {
            perror("listening");
        }

        /* Get fd PORT. */
        struct sockaddr_in socket;
        length = sizeof(socket);
        if(getsockname(CStatus->channel, (struct sockaddr *)&socket, &length) != 0){
            perror("port");
        }

        int port = ntohs(socket.sin_port);

        /* Replace . with , in ip address.*/
        int i = 0;
        while(adr[i]) {
            if(adr[i] == '.'){
                adr[i] = ',';
            }
            ++i;
        }

        char heading[100];
        strcpy(heading, "227 Entering Passive Mode (");
        strcat(heading, adr);
        strcat(heading, ",");

        char temp[10];
        int value = (port/256);
        snprintf(temp, 10, "%d", value);

        strcat(heading, temp);
        strcat(heading, ",");

        value = (port%256);
        snprintf(temp, 10, "%d", value);

        strcat(heading, temp);
        strcat(heading, ")\n");

        if (send(fd, heading, strlen(heading), 0) == -1) {
            perror("send");
        }

        // https://linux.die.net/man/3/fd_set EXAMPLE
        fd_set rfds;
        struct timeval tv = {60,0}; //30 seconds time out
        int retval;

        /* Watch stdin (fd CStatus->socket) to see when it has input. */
        FD_ZERO(&rfds);
        FD_SET(CStatus->channel, &rfds);
        retval = select(CStatus->channel + 1, &rfds, NULL, NULL, &tv);
         /* Don't rely on the value of tv now! */

        if(retval == 0){
            //time out
            close(CStatus->channel);
        }else if (retval < 0){
            //error
            close(CStatus->channel);
            perror("select()");
        }else{
            struct sockaddr res;
            socklen_t sin_size = sizeof(res);
            if((CStatus->socket = accept(CStatus->channel, &res, &sin_size)) == -1){
                perror("accept");
            }
            CStatus->passive = true;
        }


    }else{
        send_status(fd,501);
    }
}

void handle_NLST(char* buf,int fd,struct clientStatus* CStatus){
    if(CStatus->passive){
        if(count_arg(buf) == 1){
            int num=  listFiles(CStatus->socket, ".");
            send_status(fd,150);
            if (num  < 0) {
                send_status(fd,451);
            }else {
              send_status(fd,226);
            }
            CStatus->passive = false;
            if(CStatus->channel != -1){
                close(CStatus->channel);
                CStatus->channel = -1;
            }
            if(CStatus->socket != -1){
                close(CStatus->socket);
                CStatus->socket = -1;
            }
        }else{
            send_status(fd,501);
        }

    }else{
        send_status(fd,425);
    }
}

