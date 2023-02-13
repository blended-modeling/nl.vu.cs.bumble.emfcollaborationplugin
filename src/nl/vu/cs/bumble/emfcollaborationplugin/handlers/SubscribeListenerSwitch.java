package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class SubscribeListenerSwitch {
	private boolean activated;
	
	private static SubscribeListenerSwitch instance  = new SubscribeListenerSwitch();
	
	private SubscribeListenerSwitch() {
		this.activated = true;
	};
	
	public static SubscribeListenerSwitch getInstance() {
		return instance;
	}
	
	public boolean isActivated() {
		return this.activated;
	}
	
	public void switchOn() {
		this.activated = true;
	}
	
	public void switchOff() {
		this.activated = false;
	}
}
