package controller;

import admin.INotificationCallback;
import admin.Subscribtion;
import cli.Command;
import cli.Shell;
import client.ClientInfo;
import model.ComputationRequestInfo;
import node.NodeInfo;
import util.Config;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.Key;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudController implements ICloudControllerCli, Runnable,
		IAdminConsole {

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

    private int rmax;

	static ExecutorService executor;

	private ArrayList<NodeInfo> nodes;
	private ArrayList<ClientInfo> clients;
	private ArrayList<Subscribtion> subscriptions;
	
	private ArrayList<Socket> sockets;

	private Registry registry;
	
	private LinkedHashMap<Character, Long> operatorStatistics;

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
	public CloudController(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);

		sockets = new ArrayList<Socket>();
		nodes = new ArrayList<NodeInfo>();
		subscriptions = new ArrayList<Subscribtion>();
		operatorStatistics = new LinkedHashMap<>();

		tcpPort = this.config.getInt("tcp.port");
		udpPort = this.config.getInt("udp.port");
		nodeTimeout = this.config.getInt("node.timeout");
		nodeCheckPeriod = this.config.getInt("node.checkPeriod");
        rmax = this.config.getInt("controller.rmax");

		executor = Executors.newFixedThreadPool(10);
	}

	@Override
	public void run() {

		new Thread(shell).start();

		loadUsers();

		// UDP
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					udpServer = new DatagramSocket(udpPort);

					write("UDP Server läuft..");

					byte[] data = new byte[17];

					while (!udpServer.isClosed()) {
						DatagramPacket packet = new DatagramPacket(data,
								data.length);

						udpServer.receive(packet);

						String response = new String(packet.getData());

						if (response.startsWith("!alive")) {

							updateNodeList(packet.getAddress(),
									Integer.parseInt(response.substring(7, 12)
											.trim()), response.substring(13)
											.trim());
						} else if (response.startsWith("!hello")) {

                            makeUDPResponse(packet.getAddress(), packet.getPort(),"!init ");
                        }
					}
				} catch (IOException ex) {

					System.out.println(ex.getMessage());
				}
			}
		}).start();

		// TCP
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					tcpServer = new ServerSocket(tcpPort);

					write("TCP Server läuft..");

					while (!tcpServer.isClosed()) {
						Socket socket = tcpServer.accept();
						Runnable pw = new CloudControllerWorker(socket, CloudController.this);

						sockets.add(socket);
						executor.execute(pw);
					}
				} catch (IOException ex) {
					System.out.println(ex.getMessage());
				}
			}
		}).start();

		//RMI
		try {
			// create and export the registry instance on localhost at the
			// specified port
			registry = LocateRegistry.createRegistry(config
                    .getInt("controller.rmi.port"));

			// create a remote object of this cloudcontroller object
			IAdminConsole remote = (IAdminConsole) UnicastRemoteObject
					.exportObject(this, 0);

			// bind the obtained remote object on specified binding name in the
			// registry
			registry.bind(config.getString("binding.name"), remote);

		} catch (RemoteException e1) {
			e1.printStackTrace();
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
		}
		
		write("Registry instance wurde erfolgreich eingerichtet.");

	}

    private void makeUDPResponse(InetAddress address, int port, String response) {

            byte[] sendData = new byte[4096];

            sendData = ("!init " + getNodeList() + rmax).getBytes();

            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);

        try {
            udpServer.send(packet);
        } catch (IOException e) {
            write("Die Node von " + address + ":" + port + " konnte nicht erreicht werden");
        }
    }

    private String getNodeList() {
        synchronized (nodes) {

            String result = "";

            for (NodeInfo node : nodes) {
                if (node.isOnline()) {
                    result += " " + node.getAdress() + ":" + node.getPort();
                }
            }

            if(result.equals("")) return result;
            else return result.trim() + " ";
        }
    }


    /*
     * Jedes Mal, wenn der UDP Server ein Packet bekommt, wird die Nodes Liste
     * aktualisiert
     */
	private void updateNodeList(InetAddress address, int port, String operators) {

		synchronized (nodes) {

			boolean exists = false;

			for (NodeInfo node : nodes) {
				if (node.getPort() == port) {
					node.setLastSignOfLive(new Date());
					node.setOnline(true);
					exists = true;
				} else
					node.updateNode();
			}

			if (!exists)
				nodes.add(new NodeInfo(port, address, operators, 0, new Date(),
						true));
		}
	}

	/*
	 * Client Liste befüllen von user.properties
	 */
	private void loadUsers() {
		clients = new ArrayList<ClientInfo>();

		URL url = ClassLoader.getSystemClassLoader().getResource(
				"user.properties");

		ArrayList<String> zeilen = new ArrayList<String>();

		try {
			Charset charset = Charset.defaultCharset();

			BufferedReader lineReader = new BufferedReader(
					new InputStreamReader(url.openStream(), charset));

			try {
				for (String line; ((line = lineReader.readLine()) != null);) {
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
				Integer credits = Integer.parseInt(zeile.substring(
						zeile.indexOf('=') + 1).trim());

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

		synchronized (nodes) {

			String result = "";
			int counter = 1;

			for (NodeInfo node : nodes) {
				node.updateNode();
				result += counter + ". " + node + "\n";
				counter++;
			}

			if (result.equals(""))
				return "Es gibt keine Nodes";
			return result;
		}
	}

	@Override
	@Command
	public String users() throws IOException {
		synchronized (clients) {

			String result = "";
			int counter = 1;

			for (ClientInfo user : clients) {
				result += counter + ". " + user + "\n";
				counter++;
			}

			if (result.equals(""))
				return "Es gibt keine Users";
			return result;
		}
	}

	@Override
	@Command
	public String exit() throws IOException {
		try {

			executor.shutdownNow();

			for (Socket socket : sockets) {
				socket.close();
			}

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
			return "Ein Fehler beim Beenden ist aufgetretten";
		} finally {

			unbindRegistry();

			if (shell != null) {
				shell.close();
			}

			System.in.close();
		}
	}

	private void unbindRegistry() {

		try {
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(this, true);

		} catch (NoSuchObjectException e) {
			e.printStackTrace();
		}

		try {
			// unbind the remote object so that a client can't find it anymore
			registry.unbind(config.getString("binding.name"));
		} catch (AccessException e) {
			e.printStackTrace();
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Schreibt direkt in die Konsole mit dem Format wie die Shell Zeit Text
	 * Beispiel: 08:27:37.425 Die Verbindung zum Server war erfolgreich!
	 */
	public void write(String write) {
		System.out.println(DATE_FORMAT.get().format(new Date()) + "\t\t"
				+ write);
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link CloudController}
	 *            component
	 */
	public static void main(String[] args) {
		//CloudController cloudController = new CloudController(args[0], new Config("controller"), System.in, System.out);
	    CloudController cloudController = new CloudController("CloudController", new Config("controller"), System.in, System.out);

		cloudController.run();
	}

	@Override
	public boolean subscribe(String username, int credits,
			INotificationCallback callback) throws RemoteException {
		
		for(Subscribtion s : getSubscriptions()) {
			if(s.getSubscribedForUsername().equals(username)) {
				return false;
			}
		}
		
		Subscribtion subscribtion = new Subscribtion(credits, username, callback);
		getSubscriptions().add(subscribtion);
		
		return true;
		
	}

	@Override
	public List<ComputationRequestInfo> getLogs() throws RemoteException {
		userResponseStream.println("CloudController method getLogs was invocated");
		
		List<ComputationRequestInfo> requestInfos = new ArrayList<ComputationRequestInfo>();
		
		for(NodeInfo nodeInfo : getNodes()) {
			if (nodeInfo.isOnline()) {
				Socket socket;
				try {
					socket = new Socket(nodeInfo.getAdress(), nodeInfo.getPort());
				
					// create a writer to send messages to the
					// server
					PrintWriter nodeServerWriter = new PrintWriter(socket.getOutputStream(), true);
					nodeServerWriter.println("!getLogs");
					
					ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
					List<ComputationRequestInfo> nodeRequestInfo = (List<ComputationRequestInfo>) inputStream.readObject();
					requestInfos.addAll(nodeRequestInfo);
					
					socket.close();
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
			}
		}
		
		Collections.sort(requestInfos);
		
		return requestInfos;
	}

	@Override
	public LinkedHashMap<Character, Long> statistics() throws RemoteException {
//		LinkedHashMap<Character, Long> operatorStatistics = new LinkedHashMap<>();
//		
//		List<ComputationRequestInfo> requestInfos = getLogs();
//		for(ComputationRequestInfo cr : requestInfos) {
//			String operationRequest = cr.getOperationRequest();
//			int firstSpace = operationRequest.indexOf(" ");
//			String operator = operationRequest.substring(firstSpace + 1, firstSpace + 3);//operation request is the form a [+|-|*|/] b
//			
//			if(operatorStatistics.containsKey(operator.charAt(0))) {
//				Long operatorUse = operatorStatistics.get(operator.charAt(0));
//				operatorStatistics.put(operator.charAt(0), operatorUse + 1);
//			} else {
//				operatorStatistics.put(operator.charAt(0), 1l);
//			}
//			
//		}
		
		List<Map.Entry<Character, Long>> entries = new LinkedList<Map.Entry<Character, Long>>(operatorStatistics.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<Character, Long>>() {

			@Override
			public int compare(Map.Entry<Character, Long> arg0,
					Map.Entry<Character, Long> arg1) {
				return -arg0.getValue().compareTo(arg1.getValue());
			}
        });
		
		LinkedHashMap<Character, Long> sortedMap = new LinkedHashMap<>();
		
		for(Map.Entry<Character, Long> entry: entries) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		
		return sortedMap;
	}

	@Override
	public Key getControllerPublicKey() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setUserPublicKey(String username, byte[] key)
			throws RemoteException {
		// TODO Auto-generated method stub

	}

	public LinkedHashMap<Character, Long> getOperatorStatistics() {
		return operatorStatistics;
	}
	
	public ArrayList<NodeInfo> getNodes() {
		return nodes;
	}

	public ArrayList<ClientInfo> getClients() {
		return clients;
	}

	public ArrayList<Subscribtion> getSubscriptions() {
		return subscriptions;
	}

	public void setSubscriptions(ArrayList<Subscribtion> subscriptions) {
		this.subscriptions = subscriptions;
	}
	
}
