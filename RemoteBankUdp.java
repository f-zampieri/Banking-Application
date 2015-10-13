import java.net.*;
import java.io.*;

public class RemoteBankUdp {

    public static void main(String args[]) throws Exception {

        // need 4 to 6 arguments
        if (!(args.length >= 4 && args.length < 7)) {
            throw new IllegalArgumentException("Wrong number of arguments");
        }

        // break up the command line arguments
        String[] addressAndPort = args[0].split(":");
        if (addressAndPort.length != 2) {
            throw new IllegalArgumentException(
                "IP and port must be of the form w.x.y.z:port");
        }
        String[] ipAddressString = addressAndPort[0].split("\\.");
        if (ipAddressString.length != 4) {
            throw new IllegalArgumentException("IP must be of the form w.x.y.z");
        }

        byte[] ipAddress = {Byte.parseByte(ipAddressString[0]),
                            Byte.parseByte(ipAddressString[1]),
                            Byte.parseByte(ipAddressString[2]),
                            Byte.parseByte(ipAddressString[3])};
        int port = Integer.parseInt(addressAndPort[1]);

        String username = args[1];
        String password = args[2];
        String command  = args[3];
        float  amount   = (!command.equals("check")
            ? Float.parseFloat(args[4]) : 0);
        boolean debug   = args[args.length - 1].equals("-d");

        if (!(command.equals("deposit")
           || command.equals("withdraw")
           || command.equals("check"))) {
            System.out.println("Command must be deposit, withdraw, or check.");
            System.exit(0);
        }

        if (amount < 0) {
            throw new IllegalArgumentException("Must give nonnegative amount");
        }

        String[] receiveDataComponents;
        int sequenceNumber;
        String receiveData = "";
        byte[] sendData;

        // define the socket
        DatagramSocket clientSocket = new DatagramSocket();
        clientSocket.setSoTimeout(1000);

        String request  = ClientSequence.REQUEST_CONNECTION.ordinal()
                        + ":Authentication request";
        sendData = request.getBytes();

        // initialize packet with REQUEST data and appropriate IP + port
        DatagramPacket sendPacket = 
            new DatagramPacket(sendData,
                               sendData.length,
                               InetAddress.getByAddress(ipAddress),
                               port);

        if (debug) {
            System.out.println(username + ": Sending authentication request.");
        }

        byte[] inboundData = new byte[1024];

        DatagramPacket receivePacket =
            new DatagramPacket(inboundData,
                               inboundData.length);

        clientSocket.send(sendPacket);

        // the ACK will be of the form SEQNUMBER:ACK
        boolean gotAck = false;
        receivePacket.setData(new byte[1024]);
        while (!gotAck) {
            try {
                clientSocket.receive(receivePacket);
                receiveDataComponents = 
                    new String(receivePacket.getData(),
                               0,
                               receivePacket.getLength()).split(":");
                sequenceNumber =
                    Integer.parseInt(receiveDataComponents[0]);
                receiveData = receiveDataComponents[1];
                gotAck = sequenceNumber == ServerSequence.REQUEST_USERNAME.ordinal();
            } catch (SocketTimeoutException e) {
                clientSocket.send(sendPacket);
                continue;
            }
        }

        // at this point we are guaranteed to have received the ACK
        // split data into components
        // check that it has the right sequence number (REQUEST_USERNAME)
        // we perform these operations for every packet reception
        String[] ackComponents =
            new String(receivePacket.getData()).split(":");
        sequenceNumber = Integer.parseInt(ackComponents[0]);
        // give the packet the correct sequence number and the username
        sendData = (ClientSequence.SEND_USERNAME.ordinal() + ":"
                    + username).getBytes();
        sendPacket.setData(sendData);
        sendPacket.setLength(sendData.length);
        clientSocket.send(sendPacket);
        // get hash from the server
        gotAck = false;
        receivePacket.setData(new byte[1024]);
        while (!gotAck) {
            try {
                clientSocket.receive(receivePacket);
                receiveDataComponents = 
                    new String(receivePacket.getData(),
                               0,
                               receivePacket.getLength()).split(":");
                sequenceNumber =
                    Integer.parseInt(receiveDataComponents[0]);
                receiveData = receiveDataComponents[1];
                gotAck = sequenceNumber == ServerSequence.SEND_CHALLENGE.ordinal();
            } catch (SocketTimeoutException e) {
                clientSocket.send(sendPacket);
                continue;
            }
        }
        // the ACK data will be of the form SEQNUMBER:CHALLENGE
        String[] authenticationResult =
            new String(receivePacket.getData()).split(":");

        sequenceNumber = Integer.parseInt(authenticationResult[0]);
        String challenge = authenticationResult[1].substring(0, 64);

        if (debug) {
            System.out.println("Received challenge text: " + challenge);
        }

        // generate hash
        String hashString = 
            new String(MD5.generateHash(username
                                       + password
                                       + challenge));
        sendData = (ClientSequence.SEND_HASH.ordinal()
                    + ":"
                    + hashString).getBytes();
        // send hash to server
        sendPacket.setData(sendData);
        sendPacket.setLength(sendData.length);
        clientSocket.send(sendPacket);

        // wait for acknowledgment of success
        gotAck = false;
        receivePacket.setData(new byte[1024]);
        while (!gotAck) {
            try {
                clientSocket.receive(receivePacket);
                receiveDataComponents = 
                    new String(receivePacket.getData(),
                               0,
                               receivePacket.getLength()).split(":");
                sequenceNumber =
                    Integer.parseInt(receiveDataComponents[0]);
                receiveData = receiveDataComponents[1];
                gotAck = 
                    sequenceNumber == ServerSequence.CHALLENGE_RESULT.ordinal();
            } catch (SocketTimeoutException e) {
                clientSocket.send(sendPacket);
                continue;
            }
        }

        boolean isGood = receiveData.equals("GOOD");
        if (isGood) {
            sendData = (ClientSequence.SEND_COMMAND.ordinal()
                        + ":"
                        + command
                        + ":"
                        + amount).getBytes();
            // send command to server
            sendPacket.setData(sendData);
            sendPacket.setLength(sendData.length);
            clientSocket.send(sendPacket);

            // wait for results of command
            gotAck = false;
            receivePacket.setData(new byte[1024]);
            while (!gotAck) {
                try {
                    clientSocket.receive(receivePacket);
                    receiveDataComponents = 
                        new String(receivePacket.getData(),
                                   0,
                                   receivePacket.getLength()).split(":");
                    sequenceNumber =
                        Integer.parseInt(receiveDataComponents[0]);
                    receiveData = receiveDataComponents[1];
                    gotAck = 
                        sequenceNumber == ServerSequence.SEND_RESULT.ordinal();
                } catch (SocketTimeoutException e) {
                    clientSocket.send(sendPacket);
                    continue;
                }
            }

            System.out.println(receiveData);
        }

        // send FIN packet
        sendData = (ClientSequence.FIN.ordinal()
                    + ":"
                    + "FIN").getBytes();
        sendPacket.setData(sendData);
        sendPacket.setLength(sendData.length);
        clientSocket.send(sendPacket);

        if (debug) {
            if (isGood) {
                System.out.print("Successful transaction, sending FIN ");
            } else {
                System.out.print("Transaction failed, sending FIN ");
            }
            System.out.println("(ending connection).");
        }

        // expect FIN packet
        gotAck = false;
        receivePacket.setData(new byte[1024]);
        while (!gotAck) {
            try {
                clientSocket.receive(receivePacket);
                receiveDataComponents = 
                    new String(receivePacket.getData(),
                               0,
                               receivePacket.getLength()).split(":");
                sequenceNumber =
                    Integer.parseInt(receiveDataComponents[0]);
                receiveData = receiveDataComponents[1];
                gotAck = 
                    sequenceNumber == ServerSequence.FIN.ordinal();
            } catch (SocketTimeoutException e) {
                clientSocket.send(sendPacket);
                continue;
            }
        }

        // client sends the final FIN_ACK packet to the server
        // we don't particularly care if the server receives it,
        // so at this point we are finished and close the socket
        sendData = (ClientSequence.FIN_ACK.ordinal()
                    + ":"
                    + "FIN_ACK").getBytes();
        sendPacket.setData(sendData);
        sendPacket.setLength(sendData.length);
        clientSocket.send(sendPacket);

        clientSocket.close();
    }
}