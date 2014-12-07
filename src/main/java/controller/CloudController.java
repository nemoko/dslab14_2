package controller;

import cli.Command;
import cli.Shell;
import client.ClientInfo;
import node.NodeInfo;
import util.Config;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudController implements ICloudControllerCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;

    private int tcpPort;
    private int udpPort;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    public static int nodeTimeout;
    public int nodeCheckPeriod;

    static ExecutorService executor;

    private ArrayList<NodeInfo> nodes;
    private ArrayList<ClientInfo> clients;

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
    public CloudController(String componentName, Config config,
                           InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);

        nodes = new ArrayList<NodeInfo>();

        tcpPort = this.config.getInt("tcp.port");
        udpPort = this.config.getInt("udp.port");
        nodeTimeout = this.config.getInt("node.timeout");
        nodeCheckPeriod = this.config.getInt("node.checkPeriod");

        executor = Executors.newFixedThreadPool(10);
    }

    @Override
    public void run() {

        new Thread(shell).start();

        loadUsers();

        //UDP
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpServer = new DatagramSocket(udpPort);

                    System.out.println("UDP Server laeuft..");

                    byte[] data = new byte[17];

                    while (true) {
                        DatagramPacket packet = new DatagramPacket(data, data.length);

                        udpServer.receive(packet);

                        String response = new String(packet.getData());

                        if(response.startsWith("!alive")) {

                            updateNodeList(packet.getAddress(), Integer.parseInt(response.substring(7, 12).trim()), response.substring(13).trim());
                        }
                    }
                } catch (IOException ex) {

                    System.out.println(ex.getMessage());
                }
            }
        }).start();

        //TCP
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    tcpServer = new ServerSocket(tcpPort);

                    System.out.println("TCP Server laeuft..");

                    while (true) {
                        Runnable pw = new CloudControllerWorker(tcpServer.accept(), nodes, clients);

                        executor.execute(pw);
                    }
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }).start();
    }

    private void updateNodeList(InetAddress address, int port, String operators) {

        boolean exists = false;

        for (NodeInfo node : nodes) {
            if (node.getPort() == port) {
                node.setLastSignOfLive(new Date());
                node.setOnline(true);
                exists = true;
            }
            else node.updateNode();
        }

        if (!exists) nodes.add(new NodeInfo(port, address, operators, 0, new Date(), true));
    }

    /*
     * User befuellen von user.properties
     */
    private void loadUsers() {
        clients = new ArrayList<ClientInfo>();

        URL url = ClassLoader.getSystemClassLoader().getResource("user.properties");

        ArrayList<String> zeilen = new ArrayList<String>();

        try {
            Charset charset = Charset.defaultCharset();

            BufferedReader lineReader = new BufferedReader(new InputStreamReader(url.openStream(), charset));

            try {
                for (String line; ((line = lineReader.readLine()) != null); ) {
                    zeilen.add(line);
                }
            } finally {
                lineReader.close();
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

        for (String zeile : zeilen) {
            if (zeile.contains("credits")) {

                String username = zeile.substring(0, zeile.indexOf('.'));
                Integer credits  = Integer.parseInt(zeile.substring(zeile.indexOf('=') + 1).trim());

                ClientInfo user = new ClientInfo(username, credits, false);

                if (!clients.contains(user)) {
                    clients.add(user);
                }
            }
        }
    }

    @Override
    @Command
    public String nodes() throws IOException {
        String result = "";
        int counter = 1;

        for(NodeInfo node : nodes){
            node.updateNode();
            result += counter + ". " + node + "\n";
            counter++;
        }

        if(result.equals("")) return "Es gibt keine Nodes";
        return result;
    }

    @Override
    @Command
    public String users() throws IOException {
        String result = "";
        int counter = 1;

        for(ClientInfo user : clients){
            result += counter + ". " + user + "\n";
            counter++;
        }

        if(result.equals("")) return "Es gibt keine Users";
        return result;
    }

    @Override
    @Command
    public String exit() throws IOException {
        try {

            executor.shutdownNow();

            if (!clients.isEmpty()) {
                for (ClientInfo user : clients) {
                    user.setOnline(false);
                }
            }

            if (udpServer != null) {
                udpServer.close();
            }

            if (tcpServer != null) {
                tcpServer.close();
            }

            return "Controller beendet";
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return "Ein Fehler ist aufgetretten beim beenden";
        } finally {

            if (shell != null) {
                shell.close();
            }

            System.in.close();
        }
    }

    /**
     * @param args
     *            the first argument is the name of the {@link CloudController}
     *            component
     */
    public static void main(String[] args) {
        CloudController cloudController = new CloudController(args[0], new Config("controller"), System.in, System.out);
        //CloudController cloudController = new CloudController("CloudController", new Config("controller"), System.in, System.out);

        cloudController.run();
    }
}
