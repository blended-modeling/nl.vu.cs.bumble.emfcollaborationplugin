package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class Payload {
	private Patch data;
	private static final String PATCH_TYPE = "modelserver.patch";
	
	public Payload() {
	}
	
	public String getType() {
		return Payload.PATCH_TYPE;
	}
	
	public Patch getData() {
		return this.data;
	}
	
	public void setData(Patch patch) {
		this.data = patch;
	}
}
