package controller;

import admin.Subscribtion;
import client.ClientInfo;
import node.NodeInfo;
import security.Cryptography;
import org.bouncycastle.util.encoders.Base64;
import util.Config;
import util.Keys;

import javax.crypto.Mac;
import java.io.*;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Ein Arbeiter, der sich um einen Client kümmert
 */
public class CloudControllerWorker implements Runnable {

    private Config config;
    private Config configCC;
    private Cryptography Crypto;
    private SecretKey SECRET_KEY = null;
    private IvParameterSpec IV = null; 
    private boolean authenticated;

    private Socket socket;

    private CloudController cloudController;

    private BufferedReader in;
    private PrintWriter out;

    private String logedInUser;
    private String hmacFile;

    private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss.SSS");
        }
    };

    public CloudControllerWorker(Socket socket, CloudController cloudController, String hmacFile) {
        this.socket = socket;
        this.cloudController = cloudController;
        this.hmacFile = hmacFile;
        this.authenticated = false;

        try {
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            out = new PrintWriter(this.socket.getOutputStream(), true);
        } catch (IOException e) {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }

            if (out != null) {
                out.close();
            }
        }

        this.config = new Config("user");
        this.configCC = new Config("controller");

        Crypto = new Cryptography(configCC);
    }

    @Override
    public void run() {

        try {

            do {
                String received = "";

                do {
                    received += in.readLine();
                } while (in.ready());

                if (Thread.currentThread().isInterrupted()) {
                    if(authenticated) out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, "Verbindung unterbrochen".getBytes())));
                    else out.println("Verbindung unterbrochen");
                    break;
                }

                /** RSA BLOCK */
                if(!authenticated) {
                    //System.out.println("RSA");
                    PrivateKey privKey = Crypto.getPrivKey("controller");
                    //TODO ked sa zavrie client, tu dostaneme bad padding exception
                    byte[] plaintext = Crypto.runRsa(Cipher.DECRYPT_MODE, privKey, received.getBytes());
                    received = new String(plaintext);
                    //System.out.println("CC RECEIVED: " + new String(plaintext));

                    if(!getType(received).equals("authenticate")) {
                        out.println("Sie müssen sich zuerst authentifizieren!");
                        System.out.println("REQUEST DENIED: AUTHENTICATE FIRST");
                        continue;
                    }
                }
                /** RSA END */

                /** AES BLOCK */
                else {
                    //System.out.println("AES");
                    byte[] plaintext = Crypto.runAes(Cipher.DECRYPT_MODE, SECRET_KEY, IV, received.getBytes());
                    received = new String(plaintext);
                    //System.out.println("CC RECEIVED: " + new String(plaintext));
                }
                /** AES END */

                String type = getType(received);

                if (type.equals("login")) {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, login(received.substring(type.length() + 1)).getBytes())));
                }
                else if (type.equals("authenticate")) {
                    out.println(authenticate(received.substring(type.length() + 1)));
                }
                else if (type.equals("logout")) {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, logout().getBytes())));
                }
                else if (type.equals("credits")) {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, credits().getBytes())));
                }
                else if (type.equals("buy")) {
                    Long credits = null;
                    try {
                        credits = Long.parseLong(received.substring(type.length() + 1));
                    } catch (Exception e){
                        out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, "Nur Zahlen verwenden!".getBytes())));
                    }
                    if(credits != null) out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, buy(credits).getBytes())));
                }
                else if (type.equals("list")) {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, list().getBytes())));
                }
                else if (type.equals("compute")) {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, compute(received.substring(type.length() + 1)).getBytes())));
                }
                else if (type.equals("exit")) {
                    if(logedInUser == null ) out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, "Ende".getBytes())));
                    else out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, ("Client: " + logedInUser + " wurde beendet").getBytes())));
                    break;
                }
                else {
                    out.println(new String(Crypto.runAes(Cipher.ENCRYPT_MODE, SECRET_KEY, IV, "Keine richtige Anfrage".getBytes())));
                }

            } while (!Thread.currentThread().isInterrupted());

        } catch (Exception ex) {
            //ex.printStackTrace(); //TODO YOLO?
            logoutClient();
        } finally {
            try {
                logoutClient();

                if (in != null) {
                    in.close();
                }

                if (out != null) {
                    out.close();
                }

                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    /*
     *  Eine Anfrage besteht aus !type ...
     *  Das !type wird zurückgegeben
     *
     *  type ist zum Beispiel: login, logout,..
     */
    private String getType(String inputLine) {
        if(inputLine != null) {
            if (!inputLine.contains(" ")) return inputLine;
            return inputLine.substring(0, inputLine.indexOf(" "));
        } else return "";
    }

    public String login(String input) {

        String username = input.substring(0,input.indexOf(" ")).trim();
        String password = input.substring(input.indexOf(" ") + 1).trim();

        try {
            for (int i = 0; i < cloudController.getClients().size(); i++) {
                ClientInfo user = cloudController.getClients().get(i);

                if ((user.getName().trim().compareToIgnoreCase(username) == 0)
                        && (password.compareTo(config.getString(username.concat(".password"))) == 0)) {
                    if (user.isOnline()) {
                        return "Dieser Benutzer ist bereits eingeloggt!";
                    }

                    user.setOnline(true);

                    logedInUser = username;

                    write(username + " wurde eingeloggt");

                    return "Anmeldung erfolgreich!";
                }
            }
        } catch (Exception ex) {
            System.out.print(ex.getMessage());
        }

        return "Der Username oder das Passwort ist falsch!";
    }



    public String authenticate(String input) throws IOException { //alice KF06ENB8WgaWFqQq38e80kXxr5Enp/0WFBTFZXkme5I=

        String[] values = input.split(" ");

        byte[] serverChallenge = Crypto.genSecRandom(32);
        byte[] serverChallenge64 = Base64.encode(serverChallenge);

        this.IV = Crypto.generateIV();
        byte[] encodedIV = Base64.encode(IV.getIV());

        try {
            this.SECRET_KEY = Crypto.genAesKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] encodedSecRandom = Base64.encode(SECRET_KEY.getEncoded());

        //!ok <client-challenge> <controller-challenge> <secret-key> <iv-parameter>
        String response = "!ok " + values[1] + " " + new String(serverChallenge64) + " " + new String(encodedSecRandom) + " " + new String(encodedIV);

        byte[] rsaResponse = null;
        try {
            //System.out.println("RESPONSE: " + response);
            rsaResponse = Crypto.runRsa(Cipher.ENCRYPT_MODE, Crypto.getPubKey(values[0]), response.getBytes());

        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        //send back rsaResponse
        String clientResponse = respondToChallenge(new String(rsaResponse));
        byte[] decodedResponse = null;

        try {
            decodedResponse = Crypto.runAes(Cipher.DECRYPT_MODE, SECRET_KEY, IV, clientResponse.getBytes());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        //System.out.println("OK: Correct server challenge return, AES setup complete.");
        authenticated = true;

        if(decodedResponse.equals(serverChallenge64)) {
            authenticated = true; //TODO why isnt decoded response = serverchallenge 64?
            return null;
        }
        else return null; //TODO what if incorrect server challenge?
        //TODO the null is encoded and returned as garbage, fix
    }

    public String respondToChallenge(String challengeResponse){

        String response = "";

        try {
            if (socket != null && socket.isConnected()) {
                out.println(challengeResponse);
                response = in.readLine();
            } else {
                write("Es konnte keine Verbindung zu " + socket.getLocalPort() + " gemacht werden");
            }
        } catch (IOException err) {
            write("Es konnte keine Verbindung zu " + socket.getLocalPort() + " gemacht werden");
        }

        if(response == null) return "";
        return response;
    }

    public String logout() {
        if (logedInUser == null) {
            return "Sie sind nicht eingeloggt!";
        } else {
            logoutClient();
            return "Abmeldung erfolgreich!";
        }
    }

    private void logoutClient() {

        synchronized (cloudController.getClients()) {

            if (logedInUser != null) {
                for (ClientInfo client : cloudController.getClients()) {
                    if (client.getName().equals(logedInUser)) {
                        client.setOnline(false);
                    }
                }

                authenticated = false;
                SECRET_KEY = null;
                IV = null;

                write(logedInUser + " wurde ausgeloggt");
                logedInUser = null;
            }
        }
    }

    public String credits() {
        if(logedInUser == null ) return "Sie sind nicht eingeloggt!";

        for (ClientInfo client : cloudController.getClients()) {
            if (client.isOnline() && client.getName().equals(logedInUser)) {
                return client.getCredits() + "";
            }
        }

        return "Sie sind nicht eingeloggt!";
    }

    public String buy(long credits) {
        if(logedInUser == null ) return "Sie sind nicht eingeloggt!";

        for (ClientInfo client : cloudController.getClients()) {
            if (client.isOnline() && client.getName().equals(logedInUser)) {
                client.setCredits(client.getCredits() + credits);

                return "Ihre Kredite: " + client.getCredits();
            }
        }

        return "Sie sind nicht eingeloggt!";
    }

    public String list() {
        if(logedInUser == null ) return "Sie sind nicht eingeloggt!";

        String result = "";

        for (NodeInfo node : cloudController.getNodes()) {
            node.updateNode();
            String nodeOperator = "";
            if(node.isOnline()) nodeOperator = node.getOperators();

            for(int i = 0; i < nodeOperator.length(); i++){
                if(!result.contains(nodeOperator.charAt(i) + "")) result = result + nodeOperator.charAt(i);
            }
        }

        if(result.equals("")) return "Es gibt keine Nodes";
        return result;
    }


    private String compute(String compute) {
        try{
            int amount = getAmount(compute.trim());
            int countings = amount / 2;

            long creditsAfterCountings = getCreditsOfLogedInUser() - (countings * 50);
            if(creditsAfterCountings < 0) return "Sie haben nicht genug Kredite";

            String resultFromPreviousNode = null;

            for(int i = 0; i < countings; i++){

                String firstNumber = "";
                if(resultFromPreviousNode == null) {
                    firstNumber = getNumberFromBeginning(compute);
                    compute = compute.substring(firstNumber.length()).trim();
                } else {
                    firstNumber = resultFromPreviousNode;
                }

                String operator = compute.charAt(0) + "";
                compute = compute.substring(2).trim();

                String secondNumber = getNumberFromBeginning(compute.trim());
                compute = compute.substring(secondNumber.length()).trim();

                NodeInfo node = FindOnlineServerWithMinimalUsageAndApropriateOperator(operator);

                if(cloudController.getOperatorStatistics().containsKey(operator.charAt(0))) {
                    Long operatorUse = cloudController.getOperatorStatistics().get(operator.charAt(0));
                    cloudController.getOperatorStatistics().put(operator.charAt(0), operatorUse + 1);
                } else {
                    cloudController.getOperatorStatistics().put(operator.charAt(0), 1l);
                }
                
                if (node == null) {
                    return "Es gibt keinen verfügbaren Server zum berechnen ihrer Rechnung! Bitte versuchen Sie es später nocheinmal.";
                } else {

                    String response = makeRequest(node, new String(encodeBase64(getHMAC("compute " + firstNumber + " " + operator + " " + secondNumber))) + " compute " + firstNumber + " " + operator + " " + secondNumber);

                    response = checkResponse(response);

                    if(response.startsWith("Error")) return response;
                    else {
                        setNodeUsage(node, 50 * response.length());
                        if(compute.equals("")) {
                            setUserCredits(creditsAfterCountings);

                            Subscribtion toRemove = null;
                            for(Subscribtion s : cloudController.getSubscriptions()) {
                                if(s.getSubscribedForUsername().equals(logedInUser)) {
                                    if(s.getCreditsLimit() > creditsAfterCountings) {
                                        s.getNotificationCallback().notify(logedInUser, (int)creditsAfterCountings);
                                        toRemove = s;
                                    }
                                }
                            }
                            if(toRemove != null) {
                                cloudController.getSubscriptions().remove(toRemove);
                            }


                            return response;
                        }
                        else resultFromPreviousNode = response;
                    }
                }
            }
        } catch (IOException e){
            return "Error: Es ist ein Fehler am Server aufgetretten. Bitte loggen Sie sich aus und wieder ein.";
        } catch (Exception e){
            return "Error: Das Format zu berechnen stimmt nicht";
        }
        return "Error: Das Format zu berechnen stimmt nicht";
    }

    private String checkResponse(String response) {

        String[] input = response.split(" ");

        if (input[1].equals("!tampered")) {
            write("Es kam zu einem Kommunikationsfehler zur Node!");
            return "Error: Ihre Berechnung konnte nicht durchgeführt werden, da es zu einem Kommunikationsfehler zwischen Servern kam!";
        }

        boolean equalHMACs = isEqualHMAC(getHMAC(response.substring(input[0].length()).trim()),decodeBase64(input[0].getBytes()));

        if(equalHMACs) {
            return response.substring(input[0].length()).trim();
        } else {
            write("Es kam zu einem Kommunikationsfehler zur Node! Die Node schickte den falschen HMAC!");
            return "Error: Ihre Berechnung konnte nicht durchgeführt werden, da es zu einem Kommunikationsfehler zwischen Servern kam!";
        }
    }

    private NodeInfo FindOnlineServerWithMinimalUsageAndApropriateOperator(String operator) {

        synchronized (cloudController.getNodes()) {

            for (NodeInfo node : cloudController.getNodes()) {
                node.updateNode();
            }

            NodeInfo minimum = null;

            for (NodeInfo node : cloudController.getNodes()) {
                if (node.getOperators().contains(operator)) {
                    if (((minimum == null) && node.isOnline()) || ((minimum != null) && node.isOnline() && (minimum.getUsage() > node.getUsage()))) {
                        minimum = node;
                    }
                }
            }
            return minimum;
        }
    }

    private void setUserCredits(long creditsAfterCountings){
        for (ClientInfo user : cloudController.getClients()) {
            if(user.getName().equals(logedInUser)) user.setCredits(creditsAfterCountings);
        }
    }

    private long getCreditsOfLogedInUser() throws IOException{
        for (ClientInfo user : cloudController.getClients()) {
            if(user.getName().equals(logedInUser)) return user.getCredits();
        }

        throw new IOException("Kein User gefunden");
    }

    private void setNodeUsage(NodeInfo n, long i) {
        for (NodeInfo node : cloudController.getNodes()) {
            if(node.getPort() == n.getPort() && node.getAdress() == n.getAdress()) node.setUsage(node.getUsage() + i);
        }
    }

    /*
     * Es wird eine Verbinung zu einem Node mit der Anfrage request aufgebaut
     */
    public String makeRequest(NodeInfo node, String request){

        Socket s  = null;
        BufferedReader in = null;
        PrintWriter out = null;

        String response = "";

        try {
            s = new Socket(node.getAdress().getHostAddress(), node.getPort());

            if (s != null && s.isConnected()) {
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);

                out.println(request);
                response = in.readLine();
            } else {
                write("Es konnte keine Verbindung zu " + node.getAdress().getHostAddress() + ":" + node.getPort() + " gemacht werden");
            }
        } catch (IOException err) {
            write("Es konnte keine Verbindung zu " + node.getAdress().getHostAddress() + ":" + node.getPort() + " gemacht werden");
        }

        if(response == null) return "";
        return response;
    }

    public int getAmount(String input){
        int amount = 0;
        for(int i = 0; i < input.length(); i++){
            if(input.charAt(i) == ' '){
                amount++;
            }
        }
        return amount;
    }

    public byte[] getHMAC(String message){

        try {
            File file = new File(hmacFile);

            Key secretKey = Keys.readSecretKey(file);
            // make sure to use the right ALGORITHM for what you want to do (see text)
            Mac hMac = Mac.getInstance("HmacSHA256");
            hMac.init(secretKey);
            // MESSAGE is the message to sign in bytes
            hMac.update(message.getBytes("UTF-8"));
            return hMac.doFinal();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return new byte[]{};
    }

    public boolean isEqualHMAC(byte[] computedHash, byte[] receivedHash){
        return MessageDigest.isEqual(computedHash, receivedHash);
    }

    public byte[] encodeBase64(byte[] encryptedMessage){
        // encode into Base64 format
        return Base64.encode(encryptedMessage);
    }

    public byte[] decodeBase64(byte[] encryptedMessage){
        // encode into Base64 format
        return Base64.decode(encryptedMessage);
    }

    /*
     * Diese Methode liefert nur die erste Zahl zurück
     *
     * wenn zum Beispiel input "15 + 5" ist, dann wird 15 zurückgeliefert
     */
    public String getNumberFromBeginning(String input ) {
        if(input != null && !input.trim().equals("")) {
            String zahl = "";
            for (int i = 0; i < input.length(); i++) {
                if(isInteger(input.charAt(i) + "") || (input.charAt(i) + "").equals("-")) zahl = zahl + input.charAt(i);
                else return zahl;
            }
            return zahl;
        } else return "";
    }

    /*
     * true wenn input Integer ist
     * false wenn input kein Integer ist
     */
    public static boolean isInteger( String input ) {
        try {
            Integer.parseInt( input );
            return true;
        }
        catch( Exception e ) {
            return false;
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
}
