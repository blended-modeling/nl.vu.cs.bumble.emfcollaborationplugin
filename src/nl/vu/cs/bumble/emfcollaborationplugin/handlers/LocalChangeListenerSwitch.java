package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class LocalChangeListenerSwitch {
	private boolean activated;
	
	private static LocalChangeListenerSwitch instance  = new LocalChangeListenerSwitch();
	
	private LocalChangeListenerSwitch() {
		this.activated = true;
	};
	
	public static LocalChangeListenerSwitch getInstance() {
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
