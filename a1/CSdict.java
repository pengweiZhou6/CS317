
// You can use this file as a starting point for your dictionary client
// The file contains the code for command line parsing and it also
// illustrates how to read and partially parse the input typed by the user. 
// Although your main class has to be in this file, there is no requirement that you
// use this template or hav all or your classes in this file.

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.System;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.BufferedReader;
import java.util.List;
//
// This is an implementation of a simplified version of a command
// line dictionary client. The only argument the program takes is
// -d which turns on debugging output.
//

// make
// javac CSdict.java
// java -jar CSdict.jar -d

public class CSdict {

    // original
    static Boolean debugOn = false;
    static final int MAX_LEN = 255;

    private static final int PERMITTED_ARGUMENT_COUNT = 1;
    private static String command;
    private static String[] arguments;
    private static String[] empty;
    // added
    static PrintWriter out;
    static BufferedReader in;

    static final int connection_time_out = 30000;
    static Socket socket;

    static Boolean connected = false;
    static Boolean running = true;
    // current dictionary
    static String dictionary = "*";

    public static void main(String [] args) {
        byte cmdString[] = new byte[MAX_LEN];
        int len;
        // Verify command line arguments

        if (args.length == PERMITTED_ARGUMENT_COUNT) {
            debugOn = args[0].equals("-d");
            if (debugOn) {
                //java -jar CSdict.jar -d
                System.out.println(args[0]);
                System.out.println("Debugging output enabled");
            } else {
                //java -jar CSdict.jar -s
                System.out.println(args[0]);
                System.out.println("997 Invalid command line option - Only -d is allowed");
                return;
            }
        } else if (args.length > PERMITTED_ARGUMENT_COUNT) {
            //java -jar CSdict.jar -ss -sss -sss
            System.out.println("996 Too many command line options - Only -d is allowed");
            return;
        }
        //java -jar CSdict.jar


        // Example code to read command line input and extract arguments.
        try {
            while(running){
                System.out.print("csdict> ");
                System.in.read(cmdString);

                // Convert the command string to ASII
                String inputString = new String(cmdString, "ASCII");

                if (inputString.startsWith("#") || inputString.trim().equals("")) {
                    continue;
                }
                cmdString = new byte[MAX_LEN];
                // System.out.println("inputString:" + inputString);
                // Split the string into words
                String[] inputs = inputString.trim().split("[\\s+( |\t)+]");
                // Set the command
                command = inputs[0].toLowerCase().trim();
                arguments = Arrays.copyOfRange(inputs, 1, inputs.length);
                // Remainder of the inputs is the arguments.

                len = inputs.length;
                // System.out.println("The arguments are: ");

                switch(command){
                    case "open":
                        if(isCorrectSize(len,3)) {
                            String host = inputs[1];
                            String port = inputs[2];
                            if(isNumeric(port)){
                                open(host, port);
                            }
                        }
                        break;

                    case "dict":
                        if(isConnected() && isCorrectSize(len,1)) {
                            dict();
                        }
                        break;

                    case "set" :
                        if(isConnected() && isCorrectSize(len,2)) {
                            String temp = inputs[1];
                            setDictionary(temp);
                        }
                        break;

                    case "define" :
                        if(isConnected() && isCorrectSize(len,2)) {
                            String wordDef = inputs[1];
                            define(wordDef);
                        }
                        break;

                    case "match" :
                        if(isConnected() && isCorrectSize(len,2)) {
                            String word = inputs[1];
                            match(word, "exact");
                        }
                        break;

                    case "prefixmatch" :
                        if(isConnected() && isCorrectSize(len,2)) {
                            String wordPre = inputs[1];
                            match(wordPre, "prefix");
                        }
                        break;

                    case "close" :
                        if(isConnected() && isCorrectSize(len,1)) {
                            close();
                        }
                        break;

                    case "quit" :
                        if(isCorrectSize(len,1)) {
                            quit();
                        }
                        break;

                    default:
                        System.err.println("900 Invalid command");
                }
            }
            System.out.println("Done.");


        } catch (IOException exception) {
            System.err.println("998 Input error while reading commands, terminating.");
            System.exit(-1);
        }
    }




    // USER COMMANDS
    private static void quit() {
        if(connected){
            close();
        }
        running = false;
    }

    private static void close() {
        try {
            String cmd = "QUIT";
            out.println(cmd);
            printcmd(cmd);
            String respond = in.readLine();
            if(debugOn && respond != null && isStatus(respond)){
                System.out.println("<-- " + respond);
            }
            socket.close();
            connected = false;
        } catch (IOException exception) {
            System.out.println("999 Processing error. Close faied.");
        }
    }

    /*
        Pre-condition:    not Connected,
     */
    private static void open(String host, String port) {
        //open test.dict.org 2628

        if(connected){
            System.err.println("903 Supplied command not expected at this time.");
            return;
        }
        try {
            socket=new Socket();
            socket.connect(new InetSocketAddress(host,Integer.parseInt(port)),connection_time_out);
            socket.setSoTimeout(connection_time_out);
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(CSdict.socket.getOutputStream(), true);

                String respond = in.readLine();
                if(debugOn && respond != null && isStatus(respond)){
                    System.out.println("<-- "+respond);
                }
                connected = true;
            } catch (IOException e) {
                System.err.println("999 Processing error. Timed out while waiting for a response.");
            }
        } catch (IOException e) {
            System.err.println("920 Control connection to" + host + "on port" + port + " failed to open.");
        }
    }



    private static void dict() {
        try {
            Boolean isEmpty = false;
            String cmd = "SHOW DB";
            out.println(cmd);

            printcmd(cmd);

            String respond;
            if((respond = in.readLine()) != null && isStatus(respond)){
                if(debugOn){
                    System.out.println("<-- "+respond);
                    if(findStatusCode(respond) == 554){
                        isEmpty = true;
                    }
                }
            }else{
                System.out.println(respond);
            }

            while ((respond = in.readLine()) != null && !respond.startsWith(".") && !isEmpty){
                System.out.println(respond);
            }
            respond = in.readLine();
            if(debugOn && respond != null && isStatus(respond)){
                System.out.println("<-- "+respond);
            }
        } catch (IOException e) {
            System.err.println("925 Control connection I/O error, closing control connection.");
            close();
        }
    }
    // assignmentnote: This command does not have to verify that the specified DICTIONARY actually exists.
    private static void setDictionary(String d) {
        if (d != null){
            dictionary = d;
        }
    }

    private static void define(String word) {


        try {
            String respond;

            String cmd = "DEFINE " + dictionary + " " + word;
            out.println(cmd);

            printcmd(cmd);

            Integer[] noArray = new Integer[] { 552,550};
            List<Integer> noList = Arrays.asList(noArray);

            while ((respond = in.readLine()) != null ) {

                // if debugOn, send banner
                if (debugOn && isStatus(respond)) {
                    printBanner(respond);
                }

                if (isStatus(respond)) {
                    // if no match

                    if (noList.contains(findStatusCode(respond))){
                        System.out.println("****No matches found****");
                        break;
                    } else if (findStatusCode(respond) == 250 ){
                        break;
                    } else if (findStatusCode(respond) == 151 ) {
                        // @ wn "WWDADADAD"
                        System.out.println(parseDefineDict(respond));
                    }

                    // ignore .
                } else if (respond.startsWith(".")){
                    System.out.println(respond);
                    continue;
                } else {
                    System.out.println(respond);
                }

            }
        } catch (IOException e) {
            System.out.println("925 Control connection I/O error, closing control connection.");
            close();
        }

    }

    private static void match(String word, String strategy) {

        try {

            String cmd = "MATCH " + dictionary + " " + strategy + " " + word;

            out.println(cmd);

            printcmd(cmd);

            String respond;
            Integer[] noArray = new Integer[] { 552,551,550 };
            List<Integer> noList = Arrays.asList(noArray);

            while ((respond = in.readLine()) != null ) {

                // if debugOn, send banner
                if (debugOn && isStatus(respond)) {
                    printBanner(respond);
                }

                if (isStatus(respond)) {
                    // if no match

                    if (noList.contains(findStatusCode(respond))){
                        System.out.println("****No matches found****");
                        break;
                    } else if (findStatusCode(respond) == 250 ){
                        break;
                    }

                    // ignore .
                } else if (respond.startsWith(".")){
                    System.out.println(respond);
                    continue;
                } else {
                    System.out.println(respond);
                }

            }
        } catch (IOException e) {
            System.out.println("925 Control connection I/O error, closing control connection.");
            close();
        }

    }


    // helpers
    private static boolean isCorrectSize(int len, int i) {
        if(len == i){
            return true;
        }
        System.err.println("901 Incorrect number of arguments.");
        return false;
    }

    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            System.err.println("902 Invalid argument.");
            return false;
        }
    }

    // print this at the end of server ouput
//    private static void printEnd() {
//        System.out.println(".");
//    }

    private static boolean isStatus(String line) {
        return line != null && line.length() > 3 && line.substring(0,3).matches("[0-9]+");
    }
    private static int findStatusCode(String line) {
        return Integer.parseInt(line.substring(0,3));
    }


    private static void printBegin(String str) {
        System.out.println("> " + str);
    }
    private static void printBanner(String str) {
        System.out.println("<-- " + str);
    }

    //  151 "mother" wn "WordNet (r) 3.0 (2006)"
    //  returns @ wn "WordNet (r) 3.0 (2006)"
    private static String parseDefineDict (String str) {
        String[] arr = str.trim().split(" ",3);
        return "@ " + arr[2];
    }

    private static void printcmd(String command) {
        if(debugOn){
            System.out.println("> "+ command);
        }
    }

    private static boolean isConnected() {
        if(!connected){
            System.err.println("903 Supplied command not expected at this time.");
            return false;
        }
        return true;
    }
}

