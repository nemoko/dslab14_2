package client;

import cli.Command;
import cli.Shell;
import util.Config;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client implements IClientCli, Runnable {

    private String componentName;

    private Config config;
    private Config configUsers;

    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;

    private String loggedInUser = null;
    private long credits;

    private static String host;
    private static int tcpPort;
    private static Socket socket;
    private BufferedReader in;
    private PrintWriter out;

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

            System.out.println("Verbindung erfolgreich aufgebaut. Bitte schreiben Sie einen Befehlt");
        } catch (UnknownHostException ex) {
            System.out.println("Unbekannter host: " + host);

        } catch (IOException err) {
            System.out.println("Es konnte keine Verbindung zu " + host + ":" + tcpPort + " aufgebaut werden");

        }
    }

    public String makeRequest(String request){
        String response = "";

        try {
            if (socket!=null && socket.isConnected()) {
                out.println(request);
                response = in.readLine();
            } else {
                System.out.println("Es konnte keine Verbindung zu " + host + ":" + tcpPort + " aufgebaut werden");
                Reconnect();
            }
        } catch (UnknownHostException err) {
            System.out.println("Unbekannter host: " + host);
        } catch (IOException err) {
            System.out.println("Unbekannter host: " + host);
            try {
                Reconnect();
            } catch (IOException e) {

            }
        }

        if(response == null) return "";
        return response;
    }

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
            return "Die Verbindung zum Server wurde getrennt. Die Wiederverbindung funktioniert nicht!";

        }
        return "Die Wiederverbindung funktioniert. Bitte verbinden Sie sich nocheinmal";
    }

    @Override
    @Command
    public String login(String username, String password) throws IOException {

        return makeRequest("login " + username + " " + password);
    }

    @Override
    @Command
    public String logout() throws IOException {

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
