package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

public class Value {
	private String $type;
	private String $ref;
	
	public Value() {
	};
	
	public String get$type() {
		return this.$type;
	}
	
	public String get$ref() {
		return this.$ref;
	}
	
	public void setType(String value) {
		this.$type = value;
	}
	
	public void setRef(String value) {
		this.$ref = value;
	}
}
