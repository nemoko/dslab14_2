package client;

import cli.Command;
import cli.Shell;
import util.Config;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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
                out.println(request);
                response = in.readLine();
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
    public String logout() throws IOException {

        loggedInUser = null;
        return makeRequest("logout");
    }

    @Override
    @Command
    public String credits() throws IOException {

        return makeRequest("credits");
    }

    @Override
    @Command
    public String buy(long credits) throws IOException {

        return makeRequest("buy " + credits);
    }

    @Override
    @Command
    public String list() throws IOException {

        return makeRequest("list");
    }

    @Override
    @Command
    public String compute(String term) throws IOException {

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

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---

    @Override
    public String authenticate(String username) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
