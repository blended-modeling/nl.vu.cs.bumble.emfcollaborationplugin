package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class Payload<T> {
	private T data;
	private static final String PATCH_TYPE = "modelserver.patch";
	
	public Payload() {
	}
	
	public String getType() {
		return Payload.PATCH_TYPE;
	}
	
	public T getData() {
		return this.data;
	}
	
	public void setData(T patch) {
		this.data = patch;
	}
}
