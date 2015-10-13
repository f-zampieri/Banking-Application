import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * This class encapsulates methods for maintaining bank records consisting of
 * users with passwords and balances. Balances can be retrieved, incremented,
 * and decremented. Note that balances are stored as number of cents, an
 * integer quantity.
 */ 
public class Bank {
	
	// List of all usernames in our system
	List<String> usernames;
	// Maps from username to password
	Map<String, String> passwords;
	// Maps from username to balance in number of cents
	Map<String, Integer> balances;

	/**
	 * Constructor for Bank class. Initializes users, passwords, and balances
	 * based on contents of users.txt.
	 */
	public Bank() throws IOException {
		usernames = new ArrayList<>();
		passwords = new HashMap<>();
		balances  = new HashMap<>();


		FileReader fr = new FileReader("users.txt");
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		while (!(line = br.readLine()).equals("---"));
		while ((line = br.readLine()) != null) {
			// using " | " as string delimiter
		    String[] userInfo = line.split(" \\| ");
		    String username = userInfo[0];
		    String password = userInfo[1];
			// populate list of usernames
		    usernames.add(username);
		    // populate map of usernames to passwords
		    passwords.put(username, password);
		    // populate map of usernames to balances of 0 cents
		    int balance = (userInfo.length == 3 
		    	? Integer.parseInt(userInfo[2]) : 0);
		    balances.put(username, balance);
		}
	}

	/**
	 * Given a username, checks if they're in the system.
	 * @return Whether or not the username is in the system.
	 */ 
	public boolean isPresent(String username) {
		return usernames.contains(username);
	}

	/**
	 * Given a username, returns their password.
	 * @return Returns a user's password.
	 */
	public String getPassword(String username) {
		return passwords.get(username);
	}

	/**
	 * Retrieves the balance of a given user.
	 * @param username Username of accountholder whose balance is retrieved.
	 */ 
	public int getBalance(String username) {
		return balances.get(username);
	}

	/**
	 * @param balance An amount of money in cents.
	 * @return A balanced formatted in the form $xx.yy
	 */
	public String formatBalance(int balance) {
        String balanceAsString =
            new Integer(balance).toString();
        if (balanceAsString.length() == 1) {
            balanceAsString = "0" + balanceAsString;
        }
        String dollars
            = balanceAsString.substring(0, balanceAsString.length() - 2);
        dollars = (dollars.length() == 0 ? "0" : dollars);
        String cents
            = balanceAsString.substring(balanceAsString.length() - 2);
        return "$" + dollars + "." + cents;
	}

	/**
	 * Given a username and an amount, this method deposits money into an
	 * account.
	 * @param username Identifies which account is receiving the deposit.
	 * @param amount   The amount in cents being deposited.
	 * @return Returns the new balance.
	 */
	public int deposit(String username, int amount) {
		// account's current balance
		int curBalance = balances.get(username);
		// the new balance
		int newBalance = curBalance + amount;
		// update record
		balances.put(username, newBalance);
		// return new amount
		return newBalance;
	}

	/**
	 * Given a username and an amount, this method withdraws money from an
	 * account.
	 * @param username Identifies which account is being withdrawn from.
	 * @param amount   The amount in cents being withdrawn.
	 * @return Returns whether or not the withdrawal was successful.
	 */ 
	public boolean withdraw(String username, int amount) {
		int curBalance = balances.get(username);
		// if the user tries to withdraw more money than present in the
		// account, just return false
		if (amount > curBalance) {
			return false;
		}
		// the new balance
		int newBalance = curBalance - amount;
		// update record
		balances.put(username, newBalance);
		return true;
	}
}