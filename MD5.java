import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
 
/**
 * Test MD5 digest computation from http://rosettacode.org/wiki/MD5#Java
 *
 * @author Roedy Green
 * @version 1.0
 * @since 2004-06-07
 */
public final class MD5 {

	public static byte[] generateHash(String text) throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		byte[] theTextToDigestAsBytes = text.getBytes("8859_1");
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(theTextToDigestAsBytes);
		byte[] digest = md.digest();
		return digest;
	}

	public static void main(String[] args) throws UnsupportedEncodingException,
			NoSuchAlgorithmException{
		byte[] theTextToDigestAsBytes=
				"The quick brown fox jumped over the lazy dog's back"
						.getBytes("8859_1");
		MessageDigest md= MessageDigest.getInstance("MD5");
		md.update(theTextToDigestAsBytes);
		byte[] digest= md.digest();
 
		// dump out the hash
		for(byte b: digest){
			System.out.printf("%02X", b & 0xff);
		}
		System.out.println();
	}
}