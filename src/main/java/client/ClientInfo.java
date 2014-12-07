package client;

/**
 * Informationen über Client
 */
public class ClientInfo {

    private String name;
    private long credits;
    private boolean online;

    public ClientInfo(String name, long credits, boolean online) {
        this.name = name;
        this.credits = credits;
        this.online = online;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCredits() {
        return credits;
    }

    public void setCredits(long credits) {
        this.credits = credits;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String toString(){
        return name + " " + (online == false ? "offline" : "online") + " Credits: " + credits;
    }
}
