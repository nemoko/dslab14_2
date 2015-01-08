package client;

import cli.Command;
import cli.Shell;
import org.bouncycastle.util.encoders.Base64;
import security.Cryptography;
import util.Config;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Client implements IClientCli, Runnable {

    private String componentName;

    private Config config;
    private Config configUsers;

    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;

    private String loggedInUser = null;

    private static String host;
    private static int tcpPort;
    private static Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private IvParameterSpec IV = null;
    private SecretKey SECRET_KEY = null;
    private boolean authenticated;


    private static Cryptography Crypto;

    private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss.SSS");
        }
    };

    /**
     * @param componentName
     *            the name of the component - represented in the prompt
     * @param config
     *            the configuration to use
     * @param userRequestStream
     *            the input stream to read user input from
     * @param userResponseStream
     *            the output stream to write the console output to
     */
    public Client(String componentName, Config config,
                  InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;
        this.authenticated = false;

        this.configUsers = new Config("user");

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);

        host = config.getString("controller.host");
        tcpPort = config.getInt("controller.tcp.port");

    }

    @Override
    public void run() {
        new Thread(shell).start();

        try {
            socket = new Socket(host, tcpPort);
            socket.setKeepAlive(true);

            if (socket.isConnected()) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out = new PrintWriter(socket.getOutputStream(), true);
            }

            write("Die Verbindung zum Server war erfolgreich!");
        } catch (UnknownHostException ex) {
            write("Unbekannter host: " + host);

        } catch (IOException err) {
            write("Es konnte keine Verbindung zu " + host + ":" + tcpPort + " aufgebaut werden");
        }
    }

    /*
     * Wenn die Verbindung ausfällt, wird versucht eine neue aufzubauen
     */
    public String Reconnect() throws IOException {
        try {
            if(socket!=null) socket.close();

            socket = new Socket(host, tcpPort);
            socket.setKeepAlive(true);

            if (socket.isConnected()) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out = new PrintWriter(socket.getOutputStream(), true);
            }
        } catch (IOException e) {
            return "Die Verbindung zum Server konnte nicht hergestellt werden!";

        }
        return "Die Verbindung zum Server war erfolgreich!";
    }

    /*
     * Der Parameter request wird an den Server per TCP geschickt
     */
    public String makeRequest(String request){
        String response = "";

        try {
            if(socket == null){
                write(Reconnect());
            }

            if (socket != null && socket.isConnected()) {
                //SEND AES OUTPUT
                if(authenticated) {
                    try {
                        byte[] msg = Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, request.getBytes());
                        //System.out.println("SENDING: " + new String(msg));
                        out.println(new String(msg));
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (InvalidAlgorithmParameterException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    }

                    //READ INPUT
//                    response = "";
//                    do {
//                        response += in.readLine();
//                    } while (in.ready());

                    response = in.readLine(); //TODO 1 line enough?

                    //DECRYPT AES INPUT
                    try {
                        response = new String(Crypto.runAes(Cipher.DECRYPT_MODE, SECRET_KEY, IV, response.getBytes()));
                        if(response == null) return "";
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (InvalidAlgorithmParameterException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    }
                } else {
                    out.println(request);
                    response = in.readLine();
                }
            }
        } catch (UnknownHostException err) {
            write("Unbekannter host: " + host);
        } catch (IOException err) {
            try {
                write(Reconnect());

                if (socket != null && socket.isConnected()) {
                    out.println(request);
                    response = in.readLine();
                }
            } catch (IOException e) {}
        }

        if(response == null) return "";
        return response;
    }

    @Override
    @Command
    public String login(String username, String password) throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        if(loggedInUser == null) {
            String response = makeRequest("login " + username + " " + password);
            if (response.contains("erfolgreich")) loggedInUser = username;
            return response;
        } else if(!loggedInUser.equals(username)){
            return "Sie müssen sich zuerst ausloggen um sich neu einloggen zu können";
        } else {
            return "Sie sind bereits eingeloggt!";
        }
    }

    @Override
    @Command
    public String authenticate(String username) throws IOException {

        Crypto = new Cryptography(config,shell,username);

        byte[] clientChallenge;
        byte[] clientChallenge64;
        byte[] rsaMessage = null;

        //TODO make sure a private key for this user exists or print an error
        PrivateKey privKey;
        try {
            privKey = Crypto.getPrivKey(username);
        } catch (IOException e) {
            return "Es gibt kein Schluessel fuer diesen User"; //TODO fix lang
        }

        clientChallenge = Crypto.genSecRandom(32);
        clientChallenge64 = Base64.encode(clientChallenge);

        String message = "authenticate " + username + " " + new String(clientChallenge64);

        //System.out.println("Client plaintextmessage: " + message);

        try {
            rsaMessage = Crypto.runRsa(Cipher.ENCRYPT_MODE, Crypto.getPubKey("controller"), message.getBytes());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        String response = makeRequest(new String(rsaMessage));
        byte[] plaintext = null;

        try {
            plaintext = Crypto.runRsa(Cipher.DECRYPT_MODE, privKey, response.getBytes());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        String decodedResponse = new String(plaintext);

        //!ok <client-challenge> <controller-challenge> <secret-key> <iv-parameter>
        String[] challengeResponse = decodedResponse.split(" ");

        if(challengeResponse[1].equals(new String(clientChallenge64))) ;  //continue
        else return "Authentication failed, server responded with wrong Client Challenge Random Number"; //end

        String ivParameter = challengeResponse[4]; //base64 encoded!

        byte[] secKey = Base64.decode(challengeResponse[3].getBytes());
        this.SECRET_KEY = new SecretKeySpec(secKey, "AES");
        this.IV = new IvParameterSpec(Base64.decode(ivParameter.getBytes()));

        String controllerChallenge = challengeResponse[2];

        this.authenticated = true;

        String authenticateSuccess = makeRequest(controllerChallenge);
        return "";
    }

    @Override
    @Command
    public String logout() throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        String response = makeRequest("logout");

        authenticated = false;
        SECRET_KEY = null;
        IV = null;

        loggedInUser = null;
        return response;
    }

    @Override
    @Command
    public String credits() throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        return makeRequest("credits");
    }

    @Override
    @Command
    public String buy(long credits) throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        return makeRequest("buy " + credits);
    }

    @Override
    @Command
    public String list() throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        return makeRequest("list");
    }

    @Override
    @Command
    public String compute(String term) throws IOException {
        if(!authenticated) return "Sie müssen sich zuerst authentifizieren!";

        return makeRequest("compute " + term);
    }

    @Override
    @Command
    public String exit() throws IOException {
        try {
            String result = makeRequest("exit");
            if(socket!=null) socket.close();
            shell.close();
            System.in.close();
            return result;
        } catch (Exception err) {
            return err.getMessage();
        }
    }

    /*
     *   Schreibt direkt in die Konsole mit dem Format wie die Shell
     *                 Zeit                              Text
     *   Beispiel: 08:27:37.425		Die Verbindung zum Server war erfolgreich!
     */
    public void write(String write){
        System.out.println(DATE_FORMAT.get().format(new Date()) + "\t\t" + write);
    }

    /**
     * @param args
     *            the first argument is the name of the {@link Client} component
     */
    public static void main(String[] args) {
        Client client = new Client(args[0], new Config("client"), System.in, System.out);
        //Client client = new Client("client", new Config("client"), System.in, System.out);
        client.run();
    }

}
