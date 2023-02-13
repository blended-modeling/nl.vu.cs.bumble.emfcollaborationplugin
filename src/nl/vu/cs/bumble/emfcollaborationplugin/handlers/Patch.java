package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class Patch<T> {
	private String op;
	private String path;
	private T value;
	
	public Patch() {
		
	};
	
	public String getOp() {
		return this.op;
	}
	
	public String getPath() {
		return this.path;
	}
	
	public T getValue() {
		return this.value;
	}
	
	public void setOp(String op) {
		this.op = op;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	public void setValue(T value) {
		this.value = value;
	}
}
