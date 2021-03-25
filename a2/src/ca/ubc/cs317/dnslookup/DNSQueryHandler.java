package ca.ubc.cs317.dnslookup;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;

// make
// java -jar DNSLookupService.jar rootDNS [-p1]
public class DNSQueryHandler {

    private static final int DEFAULT_DNS_PORT = 53;
    private static DatagramSocket socket;
    private static boolean verboseTracing = false;
    private static short ID;
    public static boolean isAuthoritative = false;
    public static int anCount;
    private static final Random random = new Random();

    /**
     * Sets up the socket and set the timeout to 5 seconds
     *
     * @throws SocketException if the socket could not be opened, or if there was an
     *                         error with the underlying protocol
     */
    public static void openSocket() throws SocketException {
        socket = new DatagramSocket();
        socket.setSoTimeout(5000);
    }

    /**
     * Closes the socket
     */
    public static void closeSocket() {
        socket.close();
    }

    /**
     * Set verboseTracing to tracing
     */
    public static void setVerboseTracing(boolean tracing) {
        verboseTracing = tracing;
    }

    /**
     * Builds the query, sends it to the server, and returns the response.
     *
     * @param message Byte array used to store the query to DNS servers.
     * @param server  The IP address of the server to which the query is being sent.
     * @param node    Host and record type to be used for search.
     * @return A DNSServerResponse Object containing the response buffer and the transaction ID.
     * @throws IOException if an IO Exception occurs
     */
    public static DNSServerResponse buildAndSendQuery(byte[] message, InetAddress server,
                                                      DNSNode node) throws IOException {

        try {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        ID = (short) random.nextInt(Short.MAX_VALUE + 1);

        // write ID
        dataOutputStream.writeShort(ID);

        //initiate
        dataOutputStream.writeShort(0x0000);
        dataOutputStream.writeShort(0x0001);
        dataOutputStream.writeShort(0x0000);
        dataOutputStream.writeShort(0x0000);
        dataOutputStream.writeShort(0x0000);


        String[] domainNames = node.getHostName().split("[.]");

        for (int i = 0; i < domainNames.length; i++) {
            byte[] domain = domainNames[i].getBytes("UTF-8");
            dataOutputStream.writeByte(domain.length);
            dataOutputStream.write(domain);
        }

        dataOutputStream.writeByte(0x00);
        dataOutputStream.writeShort((short) node.getType().getCode());

        dataOutputStream.writeShort(0x0001);

        //write data to byteArrayOutputStream
        message = byteArrayOutputStream.toByteArray();
        DatagramPacket dnsReqPacket = new DatagramPacket(message, message.length, server, DEFAULT_DNS_PORT);
        socket.send(dnsReqPacket);


        if (verboseTracing) {
            System.out.print("Query ID     " + ID + " ");
            System.out.print(node.getHostName() + "  ");
            System.out.print(RecordType.getByCode(node.getType().getCode()) + " --> ");
            System.out.print(server.getHostAddress() + "\n");
        }

        // response write to buffer
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.receive(packet);

        //initiate DNSServerResponse
        ByteBuffer buf = ByteBuffer.wrap(buffer);
        DNSServerResponse response = new DNSServerResponse(buf, ID);

        return response;

        } catch (java.net.SocketTimeoutException s) {
            throw new SocketTimeoutException("Time out!");
        } catch (java.io.IOException e) {
            throw new IOException("buildAndSendQuery error");
        }

    }
    //l www.cs.ubc.ca AAAA

    /**
     * Decodes the DNS server response and caches it.
     *
     * @param transactionID  Transaction ID of the current communication with the DNS server
     * @param responseBuffer DNS server's response
     * @param cache          To store the decoded server's response
     * @return A set of resource records corresponding to the name servers of the response.
     */
    public static Set<ResourceRecord> decodeAndCacheResponse(int transactionID, ByteBuffer responseBuffer,
                                                             DNSCache cache) {
        byte[] content = new byte[1024];
        Set<ResourceRecord> loRecords = new LinkedHashSet<>();

        try {
            responseBuffer.get(content, 0, content.length);
            DataInputStream buf = new DataInputStream(new ByteArrayInputStream(content));

            // ID
            short ID = buf.readShort();
            if (transactionID != ID) {
                throw new Exception("ID NOT MATCH!");
            }

            //Authoritative Answer
            byte b = buf.readByte();
            byte AA = (byte) ((b >> 2) & 0x01);
            isAuthoritative = AA == 1;


            if(verboseTracing){
                System.out.println("Response ID: " + ID + " Authoritative = " + isAuthoritative);
            }
            //Retruncode
            b = buf.readByte();
            int rcode = b & 0x0F;
            if (rcode != 0 && rcode != 3 &&rcode != 5 ) {
                throw new Exception("ERROR CODE!" + rcode);
            }


            // number of questions
            int qdCount = buf.readShort();

            // number of answers
            anCount = buf.readShort();

            // number of servers
            int nsCount = buf.readShort();

            // number of additional records
            int arCount = buf.readShort();

            //Skip Questions and get position
            while (qdCount > 0) {
                int len;
                while ((len = buf.readByte()) > 0) {
                    for (int i = 0; i < len; i++) {
                        buf.readByte();
                    }
                }
                buf.readInt();
                qdCount--;
            }

            //Answers
            if(verboseTracing) {
                System.out.println("  Answers (" + anCount + ")");
            }
            decodeData(content, loRecords, buf, anCount,cache);

            //Servers
            if(verboseTracing) {
                System.out.println("  Nameservers  (" + nsCount + ")");
            }
            decodeData(content, loRecords, buf, nsCount,cache);

            //Additional Records
            if(verboseTracing) {
                System.out.println("  Additional Information (" + arCount + ")");
            }
            decodeData(content, loRecords, buf, arCount,cache);


        } catch (IOException e) {
            System.out.println("Response ID ERROR");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return loRecords;
    }

    private static void decodeData(byte[] content, Set<ResourceRecord> loRecords, DataInputStream buf, int count,DNSCache cache) throws IOException {
        while(count > 0){
            String name = getName(content,buf);
            short typeCode = buf.readShort();
            RecordType type = RecordType.getByCode(typeCode);
            buf.readShort();
            int TTL = buf.readInt();
            short addressLen = buf.readShort();
            if (type == RecordType.A || type == RecordType.AAAA) {
                byte[] bytes =  new byte[addressLen];
                buf.read(bytes);
                InetAddress address = InetAddress.getByAddress(bytes);
                ResourceRecord temp = new ResourceRecord(name, type, TTL, address);
                verbosePrintResourceRecord(temp,typeCode);
                loRecords.add(temp);
                cache.addResult(temp);
            }
            else {
                String result = getName(content,buf);
                ResourceRecord temp = new ResourceRecord(name, type, TTL, result);
                verbosePrintResourceRecord(temp,typeCode);
                loRecords.add(temp);
                cache.addResult(temp);
            }
            count--;
        }
    }


    private static String getName(byte[] content, DataInputStream buf) throws IOException {
        StringBuilder s = new StringBuilder();
        int b = buf.readByte();
        while(b != 0) {
            if (b >> 4 == -4) {
                int skip = buf.readByte();
                DataInputStream temp = new DataInputStream(new ByteArrayInputStream(content));
                temp.skipBytes(skip);
                s.append(getName(content, temp));
                return s.toString();
            }

            while(b > 0) {
                int character = buf.readByte();
                s.append((char) character);
                b--;
            }

            s.append('.');
            b = buf.readByte();
        }


        return s.substring(0, s.length() - 1);
    }


    /**
     * Formats and prints record details (for when trace is on)
     *
     * @param record The record to be printed
     * @param rtype  The type of the record to be printed
     */
    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
            if(verboseTracing) {
                System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                        record.getTTL(),
                        record.getType() == RecordType.OTHER ? rtype : record.getType(),
                        record.getTextResult());
            }
    }
}

