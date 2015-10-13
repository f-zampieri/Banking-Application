import java.io.*;
import java.net.*;
import java.util.Random;

public class ServerTcp {

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

        Bank theBank = new Bank();
        String fromclient;
        String toclient;

        if (args.length == 0) {
            throw new IllegalArgumentException("Port number required");
        }

        int portNumber = Integer.parseInt(args[0]);
        boolean debug  = args[args.length - 1].equals("-d");

        ServerSocket server = new ServerSocket(portNumber);

        System.out.println("Server waiting for client on port " + args[0]);

        while(true) {
            // blocking call to accept() method
            Socket connected = server.accept();

            // if we reach this point, a client connected
            if (debug) {
                System.out.println("Client from " + connected.getInetAddress()
                    + ":" + connected.getPort() + " wishes to authenticate");
            }
            
            // this will handle messages from the client
            BufferedReader inFromClient =
                new BufferedReader(new InputStreamReader(connected.getInputStream()));
                  
            // this will send messages to the client
            PrintWriter outToClient =
                new PrintWriter(connected.getOutputStream(), true);
            
            // send command from the client of the following form:
            // IP:PORT "USERNAME" "PASSWORD" COMMAND [AMOUNT]
            String response = inFromClient.readLine();
            // split into constituent strings
            String[] responseComponents = response.split(" ");
            String username = responseComponents[1];
            String command  = responseComponents[2];
            int    amount   = (responseComponents.length > 3
                ? (int) (Float.parseFloat(responseComponents[3]) * 100) : 0);

            if (debug) {
                System.out.println("Received command from client: " + response);
            }

            // ensure that the user is in the system
            if (!theBank.isPresent(username)) {
                outToClient.println("Error: user not present in system.");
                if (debug) {
                    System.out.println("Disconnected: Client provided nonexistent "
                                 + "username.");
                }
            } else {
                // generate challenge string consisting of 64 randomly
                // generated characters
                String challenge = generateRandomChallenge();
                outToClient.println(challenge);
                if (debug) {
                    System.out.println("Sent challenge text: " + challenge);
                }

                // take in hash from client
                String hashFromClient = inFromClient.readLine();
                // generate own hash
                String serverHash = new String(
                    MD5.generateHash(username
                        + theBank.getPassword(username)
                        + challenge));
                // compare hashes
                if (!(hashFromClient.equals(serverHash))) {
                    if (debug) {
                        System.out.println("User " + username + " failed to authenticate.");
                    }
                    outToClient.println("Error: authentication failed");
                } else {
                    if (debug) {
                        System.out.println(username + " authenticated successfully.");
                    }
                    // get user's balance, format properly, and send to the client
                    int balance = theBank.getBalance(username);
                    String formattedBalance = theBank.formatBalance(balance);
                    String connectionSuccess = 
                          "Thanks for connecting " + username + "."
                        + " Your current balance is "
                        + formattedBalance + ".";
                    outToClient.println(connectionSuccess);
                    // check command string. the check command check should do
                    // nothing, as we have already shown the user their balance.
                    if (command.equals("deposit")) {
                        // no checking needed here. the amount has already been
                        // confirmed to be positive.
                        theBank.deposit(username, amount);
                        outToClient.println("Your new balance is "
                            + theBank.formatBalance(theBank.getBalance(username))
                            + ".");
                    } else if (command.equals("withdraw")) {
                        // if the user has less money in their account than they
                        // would like to withdraw, the transaction is cancelled.
                        if (theBank.withdraw(username, amount)) {
                            outToClient.println("Your new balance is "
                            + theBank.formatBalance(theBank.getBalance(username))
                            + ".");
                        } else {
                            outToClient.println("Unable to make withdrawal,"
                                + " you have insufficient funds.");
                        }
                    } else if (!(command.equals("check"))) {
                        // redundancy check for bad command. bad commands are handled
                        // by the client.
                        if (debug) {
                            System.out.println("User " + username + " gave bad command.");
                        }
                        outToClient.println("Error: bad command");            
                    }
                    outToClient.println("Closing connection.");
                }
            }
            if (debug) {
                System.out.println("Closing connection.");
            }
            connected.close();
        }
    }
}