package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class ChangeFlag {
	boolean changeFlag;
	
	public ChangeFlag() {
		this.changeFlag = true;
	};
	
	public boolean getFlag() {
		return this.changeFlag;
	}
	
	public void setFlag(boolean flag) {
		this.changeFlag = flag;
	}
}
