import java.net.*;
import java.io.*;
import java.util.Random;

public class ServerUdp {

    /**
     * @return String of 64 randomly generated characters.
     */
    private static String generateRandomChallenge() {
        Random r = new Random();
        char[] chars = new char[64];
        for (int i = 0; i < 64; i++) {
            // gets character in range A-Z
            chars[i] = (char) (r.nextInt('Z' - 'A') + 'A');
        }
        String res = new String(chars);
        return res;
    }

    public static void main(String args[]) throws Exception {
        
        if (args.length == 0) {
            throw new IllegalArgumentException("Port number required");
        }

        Bank theBank = new Bank();

        byte[] inboundData  = new byte[1024];
        byte[] outboundData = new byte[1024];

        int serverPortNumber = Integer.parseInt(args[0]);
        boolean debug  = args[args.length - 1].equals("-d");

        String[] receiveDataComponents;
        int sequenceNumber;
        String receiveData;
        InetAddress ipAddress;
        int clientPortNumber;
        byte[] sendData;

        DatagramSocket serverSocket = new DatagramSocket(serverPortNumber);

        System.out.println("Server waiting for client on port " + serverPortNumber);
        
        while(true) {

            // receive the initial authentication request
            DatagramPacket receivePacket = new DatagramPacket(inboundData,
                                              inboundData.length);
            serverSocket.receive(receivePacket);
            // since a client has connected, we now define a timeout time
            // for receiving packets
            serverSocket.setSoTimeout(1000);

            receiveData = new String(receivePacket.getData(),
                                    0,
                                    receivePacket.getLength());
            ipAddress = receivePacket.getAddress();
            clientPortNumber = receivePacket.getPort();

            receiveDataComponents = receiveData.split(":");
            sequenceNumber = Integer.parseInt(receiveDataComponents[0]);
            receiveData = receiveDataComponents[1];
            if (debug) {
                System.out.println(receiveData);
            }
            boolean isRequest = 
                sequenceNumber == ClientSequence.REQUEST_CONNECTION.ordinal();
            // if the connection is not busy, and we are receiving a
            // connection request packet then begin processing. otherwise
            // just drop the packet
            if (isRequest) {
                // construct ACK packet
                sendData = (ServerSequence.REQUEST_USERNAME.ordinal()
                            + ":ACK").getBytes();
                DatagramPacket sendPacket = 
                    new DatagramPacket(sendData,
                                       sendData.length,
                                       ipAddress,
                                       clientPortNumber);
                if (debug) {
                    System.out.println("Got ping from "
                                        + ipAddress + ":"
                                        + clientPortNumber + ".");
                    System.out.println("Sending ACK and request for username.");
                }
                serverSocket.send(sendPacket);

                // expecting username from client
                boolean gotAck = false;
                receivePacket.setData(new byte[1024]);
                while (!gotAck) {
                    try {
                        serverSocket.receive(receivePacket);
                        receiveDataComponents = 
                            new String(receivePacket.getData(),
                                       0,
                                       receivePacket.getLength()).split(":");
                        sequenceNumber =
                            Integer.parseInt(receiveDataComponents[0]);
                        receiveData = receiveDataComponents[1];
                        gotAck = receivePacket.getPort() == clientPortNumber
                              && ipAddress.equals(receivePacket.getAddress())
                              && sequenceNumber ==
                                ClientSequence.SEND_USERNAME.ordinal();
                    } catch (SocketTimeoutException e) {
                        serverSocket.send(sendPacket);
                        continue;
                    }
                }
                // the server now has the client's username
                sequenceNumber = Integer.parseInt(receiveDataComponents[0]);
                String username = receiveData;
                if (debug) {
                    System.out.println("Got username " + username);
                }

                // generate challenge and send to client
                String challenge = generateRandomChallenge();
                sendData = (ServerSequence.SEND_CHALLENGE.ordinal()
                            + ":"
                            + challenge).getBytes();
                sendPacket.setData(sendData);
                sendPacket.setLength(sendData.length);
                serverSocket.send(sendPacket);
                if (debug) {
                    System.out.println("Send challenge text: " + challenge);
                }

                // send the client the challenge and expect a hash back
                gotAck = false;
                receivePacket.setData(new byte[1024]);
                while (!gotAck) {
                    try {
                        serverSocket.receive(receivePacket);
                        receiveDataComponents = 
                            new String(receivePacket.getData(),
                                       0,
                                       receivePacket.getLength()).split(":");
                        sequenceNumber =
                            Integer.parseInt(receiveDataComponents[0]);
                        receiveData = receiveDataComponents[1];
                        gotAck = receivePacket.getPort() == clientPortNumber
                              && ipAddress.equals(receivePacket.getAddress())
                              && sequenceNumber ==
                                ClientSequence.SEND_HASH.ordinal();
                    } catch (SocketTimeoutException e) {
                        serverSocket.send(sendPacket);
                        continue;
                    }
                }
                // if the hashes match generate a GOOD packet, otherwise
                // generate a FAIL packet and cancel transaction
                String clientHash = receiveData;
                String serverHash = new String(
                    MD5.generateHash(username
                                    + theBank.getPassword(username)
                                    + challenge));
                boolean isGood = clientHash.equals(serverHash);
                if (isGood) {
                    sendData = (ServerSequence.CHALLENGE_RESULT.ordinal()
                                + ":"
                                + "GOOD").getBytes();
                } else {
                    sendData = (ServerSequence.CHALLENGE_RESULT.ordinal()
                                + ":"
                                + "FAIL").getBytes();

                }
                sendPacket.setData(sendData);
                sendPacket.setLength(sendData.length);
                serverSocket.send(sendPacket);

                // if we succeeded, we will expect a command
                // if we failed, we will just skip to the expect FIN part
                if (isGood) {
                    // receive command (check, deposit, or withdraw)
                    // we do not need to check for validity as this is done clientside
                    gotAck = false;
                    receivePacket.setData(new byte[1024]);
                    while (!gotAck) {
                        try {
                            serverSocket.receive(receivePacket);
                            receiveDataComponents = 
                                new String(receivePacket.getData(),
                                           0,
                                           receivePacket.getLength()).split(":");
                            sequenceNumber =
                                Integer.parseInt(receiveDataComponents[0]);
                            gotAck = receivePacket.getPort() == clientPortNumber
                                  && ipAddress.equals(receivePacket.getAddress())
                                  && sequenceNumber ==
                                    ClientSequence.SEND_COMMAND.ordinal();
                        } catch (SocketTimeoutException e) {
                            serverSocket.send(sendPacket);
                            continue;
                        }
                    }
                    String command = receiveDataComponents[1];
                    int    amount  =
                        (int) (Float.parseFloat(receiveDataComponents[2]) * 100);
                    int    balance = theBank.getBalance(username);
                    String commandResult = username
                                        + ", current balance is "
                                        + theBank.formatBalance(theBank.getBalance(username))
                                        + ".";
                    if (command.equals("deposit")) {
                        // no checking needed here. the amount has already been
                        // confirmed to be positive.
                        theBank.deposit(username, amount);
                        commandResult += " After this deposit, your balance is now "
                                + theBank.formatBalance(theBank.getBalance(username))
                                + ".";
                    } else if (command.equals("withdraw")) {
                        // if the user has less money in their account than they
                        // would like to withdraw, the transaction is cancelled.
                        if (theBank.withdraw(username, amount)) {
                            commandResult += " After this withdrawal, your balance is now "
                                + theBank.formatBalance(theBank.getBalance(username))
                                + ".";
                        } else {
                            commandResult += " Unable to make withdrawal, "
                                           + "insufficient funds.";
                        }
                    }

                    if (debug) {
                        System.out.println("Sending result: " + commandResult);
                    }

                    // send the result of the command (old balance and new balance,
                    // or failure)
                    sendData = (ServerSequence.SEND_RESULT.ordinal()
                                + ":"
                                + commandResult).getBytes();
                    sendPacket.setData(sendData);
                    sendPacket.setLength(sendData.length);
                    serverSocket.send(sendPacket);
                }

                // closing the connection consists of a 4-way handshake
                // expect FIN packet
                gotAck = false;
                receivePacket.setData(new byte[1024]);
                while (!gotAck) {
                    try {
                        serverSocket.receive(receivePacket);
                        receiveDataComponents = 
                            new String(receivePacket.getData(),
                                       0,
                                       receivePacket.getLength()).split(":");
                        sequenceNumber =
                            Integer.parseInt(receiveDataComponents[0]);
                        receiveData = receiveDataComponents[1];
                        gotAck = receivePacket.getPort() == clientPortNumber
                                && ipAddress.equals(receivePacket.getAddress())
                                && sequenceNumber == ClientSequence.FIN.ordinal();
                    } catch (SocketTimeoutException e) {
                        serverSocket.send(sendPacket);
                        continue;
                    }
                }

                if (debug) {
                    System.out.println("Received FIN (client ending connection).");
                }

                // send FIN packet
                sendData = (ServerSequence.FIN.ordinal()
                            + ":"
                            + "FIN_ACK").getBytes();
                sendPacket.setData(sendData);
                sendPacket.setLength(sendData.length);
                serverSocket.send(sendPacket);

                if (debug) {
                    System.out.println("Sent FIN.");
                }

                // expect FIN_ACK packet
                gotAck = false;
                receivePacket.setData(new byte[1024]);
                while (!gotAck) {
                    try {
                        serverSocket.receive(receivePacket);
                        receiveDataComponents = 
                            new String(receivePacket.getData(),
                                       0,
                                       receivePacket.getLength()).split(":");
                        sequenceNumber =
                            Integer.parseInt(receiveDataComponents[0]);
                        receiveData = receiveDataComponents[1];
                        gotAck = receivePacket.getPort() == clientPortNumber
                                && ipAddress.equals(receivePacket.getAddress())
                                && sequenceNumber == ClientSequence.FIN_ACK.ordinal();
                    } catch (SocketTimeoutException e) {
                        serverSocket.send(sendPacket);
                        continue;
                    }
                }

                if (debug) {
                    System.out.println("Received FIN_ACK.");
                }
            }
            // now that we are done with this client, we prepare the socket
            // to wait for a new client by setting infinite wait time
            serverSocket.setSoTimeout(0);
        }
    }
}