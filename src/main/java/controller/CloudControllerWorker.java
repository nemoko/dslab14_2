package controller;

import client.ClientInfo;
import node.NodeInfo;
import util.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by tomi on 09.11.14.
 */
public class CloudControllerWorker implements Runnable {

    private Config config;

    private Socket socket;
    private ArrayList<NodeInfo> nodes;
    private ArrayList<ClientInfo> clients;

    private BufferedReader in;
    private PrintWriter out;

    private String logedInUser;

    public CloudControllerWorker(Socket socket, ArrayList<NodeInfo> nodes, ArrayList<ClientInfo> clients) {
        this.socket = socket;
        this.nodes = nodes;
        this.clients = clients;

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
    }

    @Override
    public void run() {

        try {
            String received = null;

            do {
                received = in.readLine();

                if (Thread.currentThread().isInterrupted()) {
                    out.println("Verbindung unterbrochen");
                    break;
                }

                String type = getType(received);

                if (type.equals("login")) {
                    out.println(login(received.substring(type.length() + 1)));
                }
                else if (type.equals("logout")) {
                    out.println(logout());
                }
                else if (type.equals("credits")) {
                    out.println(credits());
                }
                else if (type.equals("buy")) {
                    Long credits = null;
                    try {
                        credits = Long.parseLong(received.substring(type.length() + 1));
                    } catch (Exception e){
                        out.println("Nur Zahlen verwenden!");
                    }
                    if(credits != null) out.println(buy(credits));
                }
                else if (type.equals("list")) {
                    out.println(list());
                }
                else if (type.equals("compute")) {
                    out.println(compute(received.substring(type.length() + 1)));
                }
                else if (type.equals("exit")) {
                    if(logedInUser == null ) out.println("Ende");
                    else out.println("Client: " + logedInUser + " wurde beendet");
                    break;
                }
                else {
                    out.println("Keine richtige Anfrage");
                }

            } while (!Thread.currentThread().isInterrupted());

        } catch (Exception ex) {
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
            for (int i = 0; i < clients.size(); i++) {
                ClientInfo user = clients.get(i);

                if ((user.getName().trim().compareToIgnoreCase(username) == 0)
                        && (password.compareTo(config.getString(username.concat(".password"))) == 0)) {
                    if (user.isOnline()) {
                        return "Dieser Benutzer ist bereits eingeloggt!";
                    }

                    user.setOnline(true);

                    logedInUser = username;

                    System.out.println(username + " wurde eingelogt");

                    return "Anmeldung erfolgreich: success!";
                }
            }
        } catch (Exception ex) {
            System.out.print(ex.getMessage());
        }

        return "Der Username oder das Passwort ist falsch!";
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

        if (logedInUser != null) {
            for (ClientInfo client : clients) {
                if (client.getName().equals(logedInUser)) {
                    client.setOnline(false);
                }
            }

            System.out.println(logedInUser + " wurde abgemeldet");
            logedInUser = null;
        }
    }

    public String credits() {
        if(logedInUser == null ) return "Sie sind nicht eingeloggt!";

        for (ClientInfo client : clients) {
            if (client.isOnline() && client.getName().equals(logedInUser)) {
                return client.getCredits() + "";
            }
        }

        return "Sie sind nicht eingeloggt!";
    }

    public String buy(long credits) {
        if(logedInUser == null ) return "Sie sind nicht eingeloggt!";

        for (ClientInfo client : clients) {
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

        for (NodeInfo node : nodes) {
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

                if (node == null) {
                    return "Es gibt keinen verfügbaren Server zum berechnen ihrer Rechnung!\nBitte versuchen Sie es später nocheinmal.";
                } else {
                    String response = makeRequest(node, "compute " + firstNumber + " " + operator + " " + secondNumber);
                    if(response.startsWith("Error")) return response;
                    else {
                        setNodeUsage(node, 50 * response.length());
                        if(compute.equals("")) {
                            setUserCredits(creditsAfterCountings);
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

    private NodeInfo FindOnlineServerWithMinimalUsageAndApropriateOperator(String operator) {

        for (NodeInfo node : nodes) {
            node.updateNode();
        }

        NodeInfo minimum = null;

        for (NodeInfo node : nodes) {
            if(node.getOperators().contains(operator)) {
                if (((minimum == null) && node.isOnline()) || ((minimum != null) && node.isOnline() && (minimum.getUsage() > node.getUsage()))) {
                    minimum = node;
                }
            }
        }
        return minimum;
    }

    private void setUserCredits(long creditsAfterCountings){
        for (ClientInfo user : clients) {
            if(user.getName().equals(logedInUser)) user.setCredits(creditsAfterCountings);
        }
    }

    private long getCreditsOfLogedInUser() throws IOException{
        for (ClientInfo user : clients) {
            if(user.getName().equals(logedInUser)) return user.getCredits();
        }

        throw new IOException("Kein User gefunden");
    }

    private void setNodeUsage(NodeInfo n, long i) {
        for (NodeInfo node : nodes) {
            if(node.getPort() == n.getPort() && node.getAdress() == n.getAdress()) node.setUsage(node.getUsage() + i);
        }
    }

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
                System.out.println("Es konnte keine Verbindung zu " + node.getAdress().getHostAddress() + ":" + node.getPort() + " gemacht werden");
            }
        } catch (IOException err) {
            System.out.println("Es konnte keine Verbindung zu " + node.getAdress().getHostAddress() + ":" + node.getPort() + " gemacht werden");
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

    public static boolean isInteger( String input ) {
        try {
            Integer.parseInt( input );
            return true;
        }
        catch( Exception e ) {
            return false;
        }
    }
}
