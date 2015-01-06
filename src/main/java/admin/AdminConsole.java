package admin;

import controller.IAdminConsole;
import model.ComputationRequestInfo;
import util.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.Key;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class AdminConsole implements IAdminConsole, Runnable, INotificationCallback {
	
	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private IAdminConsole remoteInterface;
	
	private Registry registry;
	
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
	public AdminConsole(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		// TODO
	}

	private boolean bindRemoteInterface() {
		// obtain registry that was created by the server
		try {
			registry = LocateRegistry.getRegistry(
					config.getString("controller.host"),
					config.getInt("controller.rmi.port"));
			// look for the bound server remote-object implementing the
			// IAdminConsole
			// interface
			remoteInterface = (IAdminConsole) registry.lookup(config
					.getString("binding.name"));
			

		} catch (RemoteException e1) {
			return false;
		} catch (NotBoundException e1) {
			return false;
		} 
		return true;
	}

	@Override
	public void run() {

		bindRemoteInterface();

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				userRequestStream));

		String command = "";

		try {
			while ((command = reader.readLine()) != null) {

				try {
					if (command.startsWith("!getLogs")) {
						if(remoteInterface == null) {
							throw new RemoteException("Connection wasn't established");
						}
						String logs = formatLogs(getLogs());
						userResponseStream.println(logs);

					}
					else if (command.startsWith("!statistics")) {
						if(remoteInterface == null) {
							throw new RemoteException("Connection wasn't established");
						}
						String statistics = formatStatistics(statistics());
						userResponseStream.println(statistics);
					}
					else if (command.startsWith("!subscribe")) {
						if(remoteInterface == null) {
							throw new RemoteException("Connection wasn't established");
						}
						int spaceIndex = command.indexOf(" ", 11);
						String username = command.substring(11, spaceIndex);
						String creditsString = command.substring(spaceIndex + 1);
						int credits = Integer.parseInt(creditsString);
						boolean succ = subscribe(username, (int)credits, this);
						userResponseStream.println(formatSubscribtion(username, credits, succ));
					}

				} catch (RemoteException e ) {
					if(bindRemoteInterface()) { //if cloudcontroller was down (connection with adminconsole get lost) and was started again
						String resp = "";
						if(command.startsWith("!getLogs")) {
							resp = formatLogs(getLogs());
						} else if (command.startsWith("!statistics")) {
							resp = formatStatistics(statistics());
						} else if (command.startsWith("!subscribe")) {
							int spaceIndex = command.indexOf(" ", 11);
							String username = command.substring(11, spaceIndex);
							String creditsString = command.substring(spaceIndex + 1);
							int credits = Integer.parseInt(creditsString);
							boolean succ = subscribe(username, (int)credits, this);
							resp = formatSubscribtion(username, credits, succ);
						}
						userResponseStream.println(resp);
					} else {
						userResponseStream.println("Problem invoking remote method.");
					}
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private String formatLogs(List<ComputationRequestInfo> logs) {
		String logsString = "";
		if(logs == null || logs.size() == 0){
			return "There are no nodes alive.";
		}
		for(ComputationRequestInfo cr : logs){
			logsString = logsString + cr.toString() + '\n';
		}
		
		return logsString;
	}
	
	private String formatStatistics(LinkedHashMap<Character, Long> operators) {
		String statistics = "";
		if(operators == null || operators.size() == 0) {
			return "No operator was used";
		}
		for (Map.Entry<Character, Long> entry : operators.entrySet()) {
			Character key = entry.getKey();
			Long value = entry.getValue();
			statistics = statistics + key + " " + value + '\n';
		}
		return statistics;
	}
	
	private String formatSubscribtion(String username, int credits, boolean successfull) {
		if(successfull) {
			return "Successfully subscribed for user "+username+".";
		} else {
			return "Subscribtion failed.";
		}
	}

	@Override
	public boolean subscribe(String username, int credits,
			INotificationCallback callback) throws RemoteException {
		
		// create a remote object of INotificationCallback object
		INotificationCallback notifCallback = null;
		try {
			notifCallback = (INotificationCallback) UnicastRemoteObject.exportObject(callback, 0);
		} catch (java.rmi.server.ExportException e) {
			UnicastRemoteObject.unexportObject(callback, true);
			notifCallback = (INotificationCallback) UnicastRemoteObject.exportObject(callback, 0);
		}
		
		return remoteInterface.subscribe(username, credits, notifCallback);
	}

	@Override
	public List<ComputationRequestInfo> getLogs() throws RemoteException {
		return remoteInterface.getLogs();
	}

	@Override
	public LinkedHashMap<Character, Long> statistics() throws RemoteException {
		return remoteInterface.statistics();
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

	/**
	 * @param args
	 *            the first argument is the name of the {@link AdminConsole}
	 *            component
	 */
	public static void main(String[] args) {
		AdminConsole adminConsole = new AdminConsole(args[0], new Config(
				"admin"), System.in, System.out);
		new Thread(adminConsole).start();
	}

	@Override
	public void notify(String username, int credits) throws RemoteException {
		userResponseStream.println("Notification: "+ username + " has less than " + credits + " credits.");
		
	}
	
}
