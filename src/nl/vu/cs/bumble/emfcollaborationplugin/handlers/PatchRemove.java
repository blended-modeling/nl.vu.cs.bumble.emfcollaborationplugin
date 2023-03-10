package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class PatchRemove {
	private String op;
	private String path;
	
	public PatchRemove() {
		
	};
	
	public String getOp() {
		return this.op;
	}
	
	public String getPath() {
		return this.path;
	}
	
	public void setOp(String op) {
		this.op = op;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
}
