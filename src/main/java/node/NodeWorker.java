package node;

import util.DateFormatter;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Created by tomi on 10.11.14.
 */
public class NodeWorker implements Runnable{

    private Socket socket;
    private String logDir;
    private String operators;
    private String name;

    private BufferedReader in;
    private PrintWriter out;

    public NodeWorker(Socket socket, String logDir, String operators, String name) {
        this.socket = socket;
        this.logDir = logDir;
        this.operators = operators;
        this.name = name;

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

                String type = getType(received);

                if (type.equals("compute")) {
                    out.println(compute(received.substring(type.length() + 1)));
                } else {
                    out.println("Keine richtige Anfrage");
                }
        } catch (IOException ex) {
            System.out.println("Es kam zu einem Fehler. Die Verbindung wird beendet");
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
                System.out.println(ex.getMessage());
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

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

    public void create(String path) {
        new File(path).mkdirs();
    }

    public boolean exist(String path) {
        Path p = Paths.get(path);
        return Files.exists(p);
    }

    public void writing(String path, String text) {
        try {
            File statText = new File(path);
            FileOutputStream is = new FileOutputStream(statText);
            OutputStreamWriter osw = new OutputStreamWriter(is);
            Writer w = new BufferedWriter(osw);
            w.write(text);
            w.close();
        } catch (IOException e) {
            System.err.println("Es gab ein Problem beim Schreiben in die Datei!");
        }
    }

    private boolean checkIfCorrectOperator(String operator) {
        if(operator == null || operator.equals("")) return false;
        return operators.contains(operator);
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
