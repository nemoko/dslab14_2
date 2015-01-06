package admin;

public class Subscribtion {

	private long creditsLimit;
	private String subscribedForUsername;
	
	private INotificationCallback notificationCallback;
	
	public Subscribtion(long creditsLimit, String subscribedForUsername, INotificationCallback notification) {
		super();
		this.creditsLimit = creditsLimit;
		this.setSubscribedForUsername(subscribedForUsername);
		this.notificationCallback = notification;
	}

	public long getCreditsLimit() {
		return creditsLimit;
	}

	public void setCreditsLimit(long creditsLimit) {
		this.creditsLimit = creditsLimit;
	}

	public INotificationCallback getNotificationCallback() {
		return notificationCallback;
	}

	public void setNotificationCallback(INotificationCallback notificationCallback) {
		this.notificationCallback = notificationCallback;
	}

	public String getSubscribedForUsername() {
		return subscribedForUsername;
	}

	public void setSubscribedForUsername(String subscribedForUsername) {
		this.subscribedForUsername = subscribedForUsername;
	}
	
	
	
	
}
