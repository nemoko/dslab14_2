package node;

import controller.CloudController;

import java.net.InetAddress;
import java.util.Date;

/**
 * Created by tomi on 08.11.14.
 */
public class NodeInfo {

    private int port;
    private InetAddress adress;

    private Date lastSignOfLive;
    private boolean online;

    private String operators;
    private long usage;

    public NodeInfo(int port, InetAddress adress, String operators, long usage, Date lastSignOfLive, boolean online) {
        this.port = port;
        this.adress = adress;
        this.operators = operators;
        this.usage = usage;
        this.lastSignOfLive = lastSignOfLive;
        this.online = online;
    }

    public String getOperators() {
        return operators;
    }

    public void setOperators(String operators) {
        this.operators = operators;
    }

    public long getUsage() {
        return usage;
    }

    public void setUsage(long usage) {
        this.usage = usage;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public InetAddress getAdress() {
        return adress;
    }

    public void setAdress(InetAddress adress) {
        this.adress = adress;
    }

    public Date getLastSignOfLive() {
        return lastSignOfLive;
    }

    public void setLastSignOfLive(Date lastSignOfLive) {
        this.lastSignOfLive = lastSignOfLive;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    /*
     *  Kontrolliert ob ein Node online oder offline geschaltet werden soll.
     *  Wenn der Zeitunterschied größer als nodeTimeout beträgt, wird die Node
     *  offline geschlatet.
     */
    public void updateNode() {

        long diff = Math.abs(lastSignOfLive.getTime() - new Date().getTime());

        if(diff > CloudController.nodeTimeout) this.online = false;
        else this.online = true;
    }

    public String toString(){
        return "IP: " + adress + " Port: " + port + " " + (online == false ? "offline" : "online") + " Usage: " + usage;
    }
}
