package node;

import cli.Command;
import cli.Shell;
import util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    static ServerSocket tcpServer;
    static DatagramSocket udpServer;
    static ExecutorService executor;

    private Shell shell;

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

                        Runnable pw = new NodeWorker(tcpServer.accept(), logDir, operators, componentName);

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

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        InetAddress IPAddress = InetAddress.getByName(controllerHost);

                        while (!Thread.currentThread().isInterrupted()) {
                            Thread.sleep(alivePeriod);

                            byte[] sendData = new byte[17];

                            String op = operators;

                            if(op.length() == 0) op = "    ";
                            else if(op.length() == 1) op = op + "   ";
                            else if(op.length() == 2) op = op + "  ";
                            else if(op.length() == 3) op = op + " ";

                            String port = tcpPort + "";

                            if(port.length() == 0) op = "     ";
                            else if(port.length() == 1) port = port + "    ";
                            else if(port.length() == 2) port = port + "   ";
                            else if(port.length() == 3) port = port + "  ";
                            else if(port.length() == 4) port = port + " ";

                            sendData = ("!alive " + tcpPort + " " + op).getBytes();

                            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, IPAddress, udpServerPort);

                            udpServer.send(packet);
                        }
                    } catch (Exception ex) {
                        break;
                    }
                }
            }
        });
        udpNode.start();
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
        Node node = new Node(args[0], new Config(args[0]), System.in, System.out);
        //Node node = new Node("node2", new Config("node2"), System.in, System.out);
        node.run();
    }

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---

    @Override
    public String resources() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
