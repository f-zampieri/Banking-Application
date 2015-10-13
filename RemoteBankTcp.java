import java.io.*;
import java.net.*;

public class RemoteBankTcp {

    public static void main(String args[]) throws Exception {

        String fromServer;

        // need 4 to 6 arguments
        if (!(args.length >= 4 && args.length < 7)) {
            throw new IllegalArgumentException("Wrong number of arguments");
        }

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

        String toServer = args[0] + " "
                        + username + " "
                        + command + " "
                        + amount;

        if (debug) {
            System.out.println("User " + username + " sending \"" + command
                    + "\" command to destination " + args[0] + ".");
        }

        Socket socket = new Socket(InetAddress.getByAddress(ipAddress), port);

        PrintWriter outToServer =
            new PrintWriter(socket.getOutputStream(), true);

        BufferedReader inFromServer =
            new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // send server command of the following form:
        // IP:PORT "USERNAME" COMMAND [AMOUNT]
        outToServer.println(toServer);

        String challenge = inFromServer.readLine();
        if (debug) {
            System.out.println("Authentication: server is processing credentials.");
        }
        if (challenge.substring(0, 5).equals("Error")) {
            System.out.println(challenge);
            socket.close();
        } else {
            if (debug) {
                System.out.println("Received challenge, generating hash.");
            }

            String hash = new String(MD5.generateHash(username + password + challenge));
            outToServer.println(hash);

            String authenticationResult = inFromServer.readLine();
            System.out.println(authenticationResult);
            if (authenticationResult.substring(0, 5).equals("Error")) {
                socket.close();
            } else {
                // handle results of command
                String commandResponse = inFromServer.readLine();
                System.out.println(commandResponse);
                if (commandResponse.substring(0, 5).equals("Error")) {
                    socket.close();
                }
            }
        }
        socket.close();
    }
}