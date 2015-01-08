package node;

import model.ComputationRequestInfo;
import org.bouncycastle.util.encoders.Base64;
import util.DateFormatter;
import util.Keys;

import javax.crypto.Mac;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Ein Arbeiter, der sich um eine Anfrage vom Server kümmert
 */
public class NodeWorker implements Runnable{

    private Socket socket;
    private String logDir;
    private String operators;
    private String name;
    private int rmin;
    private Node node;
    private String hmacFile;

    private BufferedReader in;
    private PrintWriter out;

    private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss.SSS");
        }
    };

    public NodeWorker(Socket socket, String logDir, String operators, String name, int rmin, String hmacFile, Node node) {
        this.socket = socket;
        this.logDir = logDir;
        this.operators = operators;
        this.name = name;
        this.rmin = rmin;
        this.node = node;
        this.hmacFile = hmacFile;

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
    }

    @Override
    public void run() {
        try {
            String received = null;

                received = in.readLine();

                if (Thread.currentThread().isInterrupted()) {
                    out.println("Verbindung beendet");
                }

                String[] input = received.split(" ");

                String type = "";
                if(input.length > 1) {
                	type = input[1];
                }
                

                if (type.equals("compute")) {

                    boolean equalHMACs = isEqualHMAC(getHMAC(received.substring(input[0].length()).trim()), decodeBase64(input[0].getBytes()));

                    if(equalHMACs) {
                        if (input.length != 5) out.println("Error: Das Format zu berechnen stimmt nicht");
                        else out.println(new String(encodeBase64(getHMAC(compute(input[2] + " " + input[3] + " " + input[4])))) + " " + compute(input[2] + " " + input[3] + " " + input[4]));
                    } else {
                        out.println(new String(encodeBase64(getHMAC("!tampered " + received))) + " !tampered " + received);
                    }
                } else if (received.equals("!getLogs")) {
                	ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
    				
    				outputStream.writeObject(getLogs());
                } else if (type.equals("!share")) {
                    int share = Integer.parseInt(input[2]);
                    if(share >= rmin) out.println("!ok");
                    else out.println("!nok");
                } else if (type.equals("!commit")) {
                    node.setResources(Integer.parseInt(input[2]));
                } else if (type.equals("!rollback")) {
                }
                else {
                    out.println("Keine richtige Anfrage");
                }
        } catch (IOException ex) {
            write("Es kam zu einem Fehler. Die Verbindung wird beendet");
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }

                if (out != null) {
                    out.close();
                }
                socket.close();

                return;
            } catch (IOException ex) {
                System.err.println(ex.getMessage());
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            }
        }
    }

    /*
     * Eine Berechnung wird durchgeführt.
     *
     * Zum Beispiel 55 + 10
     */
    private String compute(String compute) {
        if(compute == null || compute.equals("")) return "Error: Das Format zu berechnen stimmt nicht";

        try{
            String firstNumber = getNumberFromBeginning(compute);
            compute = compute.substring(firstNumber.length()).trim();
            String operator = compute.charAt(0) + "";
            String lastNumber = compute.substring(2).trim();

            int firstNumberInteger = Integer.parseInt(firstNumber);
            int lastNumberInteger = Integer.parseInt(lastNumber);

            if(!checkIfCorrectOperator(operator)) return "Error: Diese Node kann nicht mit diesem " + operator + " Operator rechnen";

            //System.out.println("Berechnung: " + firstNumberInteger + " " + operator + " " + lastNumberInteger);

            String ergebnis = "";

            switch (operator){
                case "+":
                    ergebnis = (firstNumberInteger + lastNumberInteger) + "";
                    break;
                case "-":
                    ergebnis = (firstNumberInteger - lastNumberInteger) + "";
                    break;
                case "*":
                    ergebnis = (firstNumberInteger * lastNumberInteger) + "";
                    break;
                case "/":
                    if(lastNumberInteger == 0 || lastNumberInteger == -0) {
                        ergebnis = "Error: division by 0";
                        break;
                    }
                    double first = firstNumberInteger;
                    double second = lastNumberInteger;
                    double result = first / second;
                    int res = (int) result;
                    double diff = result - (double) res;
                    if(diff != 0) res++;
                    ergebnis = res + "";
                    break;
                default: return "Error: Das Format zu berechnen stimmt nicht";
            }

            DateFormatter dateFormatter = new DateFormatter();

            if(!exist(logDir)) create(logDir);

            writing(logDir + "/" + dateFormatter.formatDate(new Date()) + "_" + name + ".log",
                    firstNumberInteger + " " + operator + " " + lastNumberInteger + "\n" + ergebnis);

            return ergebnis;

        } catch (Exception e){
            return "Error: Das Format zu berechnen stimmt nicht";
        }
    }
    
    /*
     * get logs generated by this node
     */
    private List<ComputationRequestInfo> getLogs() {
		List<ComputationRequestInfo> nodeRequestInfo = new ArrayList<ComputationRequestInfo>();
		
		File directory = new File(logDir);
		
		File [] fList = directory.listFiles();
		
		for(File file : fList) {
			String logName = file.getName();
			
			FileReader in = null;
			BufferedReader br = null;
			try {
				in = new FileReader(logDir + "/" + file.getName());
				br = new BufferedReader(in);
				
				int lastOccurence = logName.lastIndexOf("_");
				String timestamp = logName.substring(0, lastOccurence);
				String nodeName = logName.substring(lastOccurence + 1, logName.length() - 4);
				String request_line = br.readLine();
				String response_line = br.readLine();
				
				ComputationRequestInfo requestInfo = new ComputationRequestInfo(nodeName, request_line, response_line, timestamp);
				
				nodeRequestInfo.add(requestInfo);
				
				
				
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			} finally {
				try {
					br.close();
					in.close();
				} catch (IOException e) {
				}
				
			}	
		}
		
		return nodeRequestInfo;
	}

    /*
     * Erzeugt einen oder mehrere Ordner
     */
    public void create(String path) {
        new File(path).mkdirs();
    }

    /*
     * Kontrolliert, ob es bereits diesen Pfad gibt
     */
    public boolean exist(String path) {
        Path p = Paths.get(path);
        return Files.exists(p);
    }

    /*
     * Eine Logdate wird erstellt
     */
    public void writing(String path, String text) {
        try {
            File statText = new File(path);
            FileOutputStream is = new FileOutputStream(statText);
            OutputStreamWriter osw = new OutputStreamWriter(is);
            Writer w = new BufferedWriter(osw);
            w.write(text);
            w.close();
        } catch (IOException e) {
            write("Es gab ein Problem beim Schreiben in die Datei!");
        }
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
     * Kontrolliert, ob diese Node mit dem Operator rechnen kann
     */
    private boolean checkIfCorrectOperator(String operator) {
        if(operator == null || operator.equals("")) return false;
        return operators.contains(operator);
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
}
