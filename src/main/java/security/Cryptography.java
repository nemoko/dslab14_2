package security;

import cli.Shell;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;
import util.SecurityUtils;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.*;

public class Cryptography {

    private Config conf;
    private Shell shell;
    private String user;

    private final String RSA_CYPHER = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";
    private final String AES_CYPHER = "AES/CTR/NoPadding";
    private Cipher rsa = null;
    private Cipher aes = null;
    private SecureRandom random;


    public Cryptography(Config conf)
    {
        SecurityUtils.registerBouncyCastle();

        this.conf = conf;

        try {
            rsa = Cipher.getInstance(RSA_CYPHER);
            aes = Cipher.getInstance(AES_CYPHER);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    public Cryptography(Config conf, Shell shell, String user)
    {
        SecurityUtils.registerBouncyCastle();

        this.user = user;
        this.conf = conf;
        this.shell = shell;

        try {
            rsa = Cipher.getInstance(RSA_CYPHER);
            aes = Cipher.getInstance(AES_CYPHER);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    /**
     * generates a "length" byte secure random number
     * */
    public byte[] genSecRandom(int length) {
        this.random = new SecureRandom();
        byte randomNumbers[] = new byte[length];
        random.nextBytes(randomNumbers);

        return randomNumbers;
    }

    public SecretKey genAesKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey secKey = generator.generateKey();
        return secKey;
    }

    public byte[] runRsa(int mode, Key key, byte[] message) throws InvalidKeyException, ClassNotFoundException, IOException, IllegalBlockSizeException, BadPaddingException {
        rsa.init(mode,key);

        if(mode == Cipher.DECRYPT_MODE) {
            //decode 64
            byte[] encrypted = Base64.decode(message);
            //decrypt
            byte[] plaintext = rsa.doFinal(encrypted);
            return plaintext;
        } else {
            //encrypt
            byte[] encrypted = rsa.doFinal(message);
            //encode 64
            byte[] base64message = Base64.encode(encrypted);
            return base64message;
        }
    }

    public byte[] runAes(int mode, Key key, IvParameterSpec iv, byte[] message) throws InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        aes.init(mode, key, iv);

        if(mode == Cipher.ENCRYPT_MODE) {
            byte[] encrypted = aes.doFinal(message);
            byte[] encoded64 = Base64.encode(encrypted);
            return encoded64;
        } else {
            byte[] decoded = Base64.decode(message);
            byte[] decrypted = aes.doFinal(decoded);
            return decrypted;
        }
    }

    public PublicKey getPubKey(String keyname) throws IOException {
        String keysDir = conf.getString("keys.dir");
        String pathToControllerPubKey = keysDir + File.separator + keyname + ".pub.pem";

        PEMReader pemReader = new PEMReader(new FileReader(pathToControllerPubKey));
        PublicKey pubKey = (PublicKey) pemReader.readObject();
        pemReader.close();

        return pubKey;
    }

    //TODO make sure a private key for this user exists or print an error
    public PrivateKey getPrivKey(String keyname) throws IOException {
        String privateKeyLoc;

        if(keyname.equals("controller")) privateKeyLoc = conf.getString("key");
        else privateKeyLoc = conf.getString("keys.dir") + File.separator + keyname + ".pem";

        PrivateKey privkey = Keys.readPrivatePEM(new File(privateKeyLoc));

        return privkey;
    }

    public IvParameterSpec generateIV() {
        SecureRandom secR = new SecureRandom();
        byte[] iv = new byte[16];
        secR.nextBytes(iv);

        return new IvParameterSpec(iv);
    }


}
