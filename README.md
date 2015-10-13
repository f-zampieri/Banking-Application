README

users.txt: this text file stores users, their passwords, and an optional initial balance. A proper user format is as follows:
<USERNAME> | <PASSWORD> [| <INITIAL BALANCE>]
Note that the initial balance is in cents, not dollars.
MD5: this class has a method for producing an MD5 hash of a given string. It has a main method you can run to test this functionality.
Bank: this class maintains a list of all users in the system, along with a mapping from username to balance, and a mapping from username to password. Upon instantiation of a Bank object, users are added to the system based on the contents of the users.txt file.
ClientSequence: an enum encapsulating the sequence of packets the server expects to receive from the client. The sequence is as follows: REQUEST_CONNECTION, SEND_USERNAME, SEND_HASH, SEND_COMMAND, FIN, FIN_ACK.
ServerSequence: an enum encapsulating the sequence of packets the client expects to receive from the server. The sequence is as follows: REQUEST_USERNAME, SEND_CHALLENGE, CHALLENGE_RESULT, SEND_RESULT, FIN, FIN_ACK.
RemoteBankTcp: the client banking application implementation. This class uses TCP to communicate with the server.
RemoteBankUdp: the client banking application implementation. This class uses UDP to communicate with the server. Implements a method for producing a string of 64 randomly generated characters.
ServerTcp: the server banking application implementation. This class uses TCP to communicate with the client. Implements a method for producing a string of 64 randomly generated characters.
ServerUdp: the server banking application implementation. This class uses UDP to communicate with the client.

HOW TO RUN THIS PROGRAM

Run the server in a terminal window with the command "java ServerTcp <PORT>" or "java ServerUdp <PORT>". You may add an optional debug (-d) flag to the end of the command.
In another terminal window, run the client program with the corresponding transport protocol with the command "java RemoteBankTcp <IP>:<PORT>"<USERNAME>" "<PASSWORD>" <COMMAND> <AMOUNT>" or "java RemoteBankUdp <IP>:<PORT>"<USERNAME>" "<PASSWORD>" <COMMAND> <AMOUNT>"
Note colon separating IP and port, the quotes around username and password, and that amount takes on the format "<DOLLARS>[.<CENTS>]". The IP must be the IP of the server. The port must be the port of the server's socket. The client may check their balance, or withdraw or deposit money.

APPLICATION PROTOCOL DESCRIPTION
PROTOCOL-INDEPENDENT
The server maintains a backing Bank object.
The client is responsible for ensuring valid inputs. Invalid inputs include negative amounts of money, a badly formatted IP address or port, or a misspelled command. Invalid input causes the program to yield an exception. If the client sends a non-existent username or the wrong password, the transaction is cancelled and the client is notified by the server. 
Authentication is handled via MD5 hashing: the server produces a string of 64 randomly generated characters and sends it to the client. The client concatenates their username, password, and the challenge string and produces an MD5 hash. The client sends this hash to the server, who then performs the same hashing operation. If the hashes are equal, the client is authenticated. Otherwise the transaction is cancelled and the client is notified by the server.

TCP
Using TCP, we are able to avoid a great deal of complexity in our application. There is no need for our application to handle sequence numbers, packet loss, etc. The protocol is as follows: the client begins by opening a connection with the server. If the server is busy, the connection will be queued up. Once the server is free, a connection is established between the client and the server. The client authenticates with the server as described above. The client sends the server all the information needed to make a banking transaction in one packet: username, command, and an amount to withdraw or deposit if desired. The server processes this information and performs the appropriate action. The client is notified of their previous balance and new balance if applicable.

UDP
To handle multiple concurrent users and ensure that packets are delivered in the correct order, the application using UDP implements its own set of sequence numbers. The client and the server have their own distinct set of sequence numbers corresponding to the series of interactions they will carry out with one another. Using a series of consecutive numbers, rather than an alternating-bit strategy, allows us to ensure concurrency. If a server is in the middle of a transaction with client A and client B requests a connection, the server discards the packet upon seeing that client B's sequence number (which will correspond to the REQUEST_CONNECTION) value is not what it was expecting. At each packet reception the server also inspects the port number and IP address of the incoming packet to ensure it is coming from the same client. This provides a degree of security: a client cannot spoof sequence numbers and interpose itself in the midst of a connection between the server and another client, because its IP address and port will differ.
Packet data takes on the form <SEQUENCE NUMBER>:<DATA1>[:<DATA2>[:<DATA3>...]]. Data may or may not contain meaningful information based on what is required. In my implementation each packet contains at most 2 data portions.
The client initiates the connection by sending a packet. Each time a packet is sent going in either direction, the entity sending the packet expects a response of some sort. If possibly invalid, incorrect, or otherwise "unhealthy" data is transmitted, the connection is closed and the client is notified. Sequence number, IP address, and port number are checked at the reception of every packet. If any of them are not what the entity expected, the packet is dropped and the entity resumes waiting.
Here is the sequence of packets transmitted between client and server in a "healthy" transaction: first, the client sends a REQUEST_CONNECTION packet. The server responds with a REQUEST_USERNAME packet (this can be considered an ACK). The client responds with a SEND_USERNAME packet containing their username. If the server does not find the username in the system, the transaction fails and skips to the FIN step. Otherwise the server responds with a SEND_CHALLENGE packet containing a string of 64 randomly generated characters. The client generates an MD5 hash as described above and sends a SEND_HASH packet containing the hash. The server compares the hashes. If they are equal, the server replies with a CHALLENGE_RESULT packet whose data is either the string "GOOD" or the string "FAIL". In the case where it is "FAIL" the transaction is cancelled. Otherwise the client sends a SEND_COMMAND packet with the command string and the withdrawal or deposit amount, if desired. If the "check" command is chosen, the amount defaults to 0. The server in turn processes the result, changing the state of the bank as needed. The server sends a SEND_RESULT packet, whose data is a friendly message indicated the status of the transaction including previous balance, new balance, and transaction failure if needed. The client sends a FIN packet to the server. The server responds with a FIN_ACK packet. It is important that the client receives this FIN_ACK packet, so that the client knows that the server knows the client is done sending data. The server sends a FIN packet to the client. The client sends a FIN_ACK packet, but it does not particularly matter that the server receive this. 

ASSUMPTIONS
PROTOCOL-INDEPENDENT
The client will reasonably limit the lengths of their transactions including username, password, and amount to withdraw or deposit. The client will give valid input.

UDP
The client, once initializing a connection, will complete their transaction to its end. 

BUGS
If you specify a deposit or withdraw command with no amount (with or without the debug flag) the client throws an unhandled exception.

LIMITATIONS/PERFORMANCE
The receiving packet's buffer in the UDP server implementation is cleared by setting its buffer to a newly initialized 1024 byte array. There is probably a better way to do this.