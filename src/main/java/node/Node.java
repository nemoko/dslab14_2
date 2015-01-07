package node;

import cli.Command;
import cli.Shell;
import util.Config;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Node implements INodeCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private static Thread tcpNode;
    private static int tcpPort;

    private static Thread udpNode;
    private static int udpServerPort;

    private String controllerHost;
    private int alivePeriod;
    private String logDir;
    private String operators;
    private int rmin;

    static ServerSocket tcpServer;
    static DatagramSocket udpServer;
    static ExecutorService executor;

    private InetAddress IPAddress;

    private Shell shell;

    private ArrayList<String> nodesIP;
    private Integer cloudResources;

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
    public Node(String componentName, Config config,
                InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);

        tcpPort = config.getInt("tcp.port");
        controllerHost = config.getString("controller.host");
        alivePeriod = config.getInt("node.alive");
        logDir = config.getString("log.dir");
        operators = config.getString("node.operators");
        rmin = config.getInt("node.rmin");
        udpServerPort = config.getInt("controller.udp.port");

        try {
            udpServer = new DatagramSocket();
        } catch (SocketException ex) {
            write("Der UDP Server konnte nicht gestartet werden");
        }

        try {
            tcpServer = new ServerSocket(tcpPort);
        } catch (IOException ex) {
            write("Der TCP Server konnte nicht gestartet werden");
        }

        executor = Executors.newFixedThreadPool(10);
        nodesIP = new ArrayList<>();
    }

    @Override
    public void run() {
        new Thread(shell).start();

        //TCP
        tcpNode = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    write("TCP läuft..");

                    while (!Thread.currentThread().isInterrupted()) {

                        Runnable pw = new NodeWorker(tcpServer.accept(), logDir, operators, componentName, rmin, Node.this);

                        executor.execute(pw);
                    }
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        });
        tcpNode.start();

        //UDP
        udpNode = new Thread(new Runnable() {
            @Override
            public void run() {

                write("UDP läuft..");

                    try {
                        IPAddress = InetAddress.getByName(controllerHost);

                        sendHelloMessage();

                    } catch (Exception ex) {
                        write("Ein Fehler ist aufgetretten. Bitte starten Sie die Node neu!");
                    }
            }

        });
        udpNode.start();

        // Waiting for receiving UDP Packet !init from CloudController
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpServer = new DatagramSocket(tcpPort + 1000);

                    byte[] data = new byte[4096];

                    while (!udpServer.isClosed()) {
                        DatagramPacket packet = new DatagramPacket(data,
                                data.length);

                        udpServer.receive(packet);

                        String response = new String(packet.getData());

                        if (response.startsWith("!init")) {

                            String[] parts = clearResponse(response.split(" "));

                            if(parts.length == 1) write("Ein fehlerhaftes Packet vom CloudController!");
                            else if(parts.length == 2) {
                                cloudResources = Integer.parseInt(parts[1]);
                                if(cloudResources >= rmin) sendAliveMessages();
                                else write("Dieser Node konnte nicht vom CloudController aufgenommen werden, weil zu wenige Resourcen zur Verfügung stehen.");
                                break;
                            }
                            else {
                                for(int i = 1; i < parts.length-1; i++){
                                    nodesIP.add(parts[i]);
                                }
                                cloudResources = Integer.parseInt(parts[parts.length-1]);
                                contactAllNodesAndWaitForAnswer("!share " + (cloudResources / (nodesIP.size() + 1)));
                                break;
                            }
                        }
                    }
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String[] clearResponse(String[] split) {
        String neu = "";
        for(int i = 0; i < split.length; i++){
            if(split[i].length() == 1 && split[i].charAt(0) == 0) return neu.trim().split(" ");
            else neu += " " + split[i].replace("/", "");
        }
        return neu.trim().split(" ");
    }

    private void sendHelloMessage() {
        byte[] sendData = new byte[4096];

        sendData = ("!hello").getBytes();

        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, udpServerPort);

        try {
            udpServer.send(packet);
        } catch (IOException e) {
            write("Der CloudController konnte nicht erreicht werden");
        }
    }

    private void contactAllNodesAndWaitForAnswer(final String request) throws InterruptedException, ExecutionException, IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Set<Callable<String>> callables = new HashSet<Callable<String>>();

        for(final String ip : nodesIP){
            callables.add(new Callable<String>() {
                public String call() throws Exception {
                    return makeRequest(request, ip);
                }
            });
        }

        List<Future<String>> futures = executorService.invokeAll(callables);
        decideCommitOrRollback(executorService, futures);
    }

    private void contactAllNodes(final String request) throws InterruptedException, ExecutionException, IOException {

        for(final String ip : nodesIP){
            makeRequest(request, ip);
        }
    }

    public void decideCommitOrRollback(ExecutorService executorService, List<Future<String>> futures) throws ExecutionException, InterruptedException, IOException {

        String response = "";

        for(Future<String> future : futures){
            if(future.get().equals("!nok")) {
                response = "!rollback " + (cloudResources / (nodesIP.size() + 1));
                write("Dieser Node konnte nicht vom CloudController aufgenommen werden, weil zu wenige Resourcen zur Verfügung stehen.");
                break;
            }
        }

        executorService.shutdown();

        if(response.equals("")) {
            response = "!commit " + (cloudResources / (nodesIP.size() + 1));
            contactAllNodes(response);
            this.cloudResources = cloudResources / (nodesIP.size() + 1);
            sendAliveMessages();
        }
        else contactAllNodes(response);
    }

    private void sendAliveMessages() throws InterruptedException, IOException {
        while (!Thread.currentThread().isInterrupted()) {

            Thread.sleep(alivePeriod);

            byte[] sendData = new byte[4096];

            sendData = ("!alive " + tcpPort + " " + operators).getBytes();

            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, udpServerPort);

            udpServer.send(packet);
        }
    }

    /*
     * Der Parameter request wird an den Server per TCP geschickt
     */
    public String makeRequest(String request, String nodeAdress) throws IOException {

        String[] parts = nodeAdress.split(":");

        Socket socket = null;
        try {
            socket = new Socket(parts[0] + "", Integer.parseInt(parts[1]));
        } catch (IOException e) {
            return "Es konnte keine Verbindung zum " + nodeAdress + " hergestellt werden";
        }

        socket.setKeepAlive(true);

        String response = "";

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            if (socket != null && socket.isConnected()) {
                out.println(request);
                response = in.readLine();
            }
        } catch (UnknownHostException err) {
            return "Unbekannter host: " + parts[0];
        } catch (IOException err) {
            return "Ein Fehler trat auf!";
        }

        socket.close();

        if(response == null) return "";
        return response;
    }

    public void setResources(Integer resources){
        this.cloudResources = resources;
    }

    @Override
    @Command("exit")
    public String exit() throws IOException {
        try {
            if (executor != null) {
                executor.shutdownNow();
            }

            udpNode.interrupt();
            tcpNode.interrupt();

            if (udpServer != null) {
                udpServer.close();
            }

            if (tcpServer != null) {
                tcpServer.close();
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

        if (shell != null) {
            shell.close();
        }

        System.in.close();

        Thread.currentThread().interrupt();

        return "Node wurde  beendet";
    }

    /*
     *   Schreibt direkt in die Konsole mit dem Format wie die Shell
     *                 Zeit                              Text
     *   Beispiel: 08:27:37.425		Die Verbindung zum Server war erfolgreich!
     */
    public void write(String write){
        System.out.println(DATE_FORMAT.get().format(new Date()) + "\t\t" + write);
    }

    @Override
    @Command
    public String history(int numberOfRequests) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param args
     *            the first argument is the name of the {@link Node} component,
     *            which also represents the name of the configuration
     */
    public static void main(String[] args) {
        //Node node = new Node(args[0], new Config(args[0]), System.in, System.out);
        Node node = new Node("node2", new Config("node2"), System.in, System.out);
        node.run();
    }

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---

    @Override
    @Command("resources")
    public String resources() throws IOException {
        if(cloudResources == null) return "Es wurde keine Verbindung zum CloudController hergestellt und deshalb kann die Resource höhe nicht ermittelt werden";
        return cloudResources + "";
    }

}
