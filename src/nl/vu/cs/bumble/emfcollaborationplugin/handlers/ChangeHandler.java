package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import java.util.Optional;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;

import com.fasterxml.jackson.databind.JsonNode;

public class ChangeHandler {
	
	private ConvertHandler converter = ConvertHandler.getConverter();
	private ModelServerClient client;
	private String modelUri;
	
	public ChangeHandler(EObject root, ModelServerClient client, String modelUri) {
		this.client = client;
		this.modelUri = modelUri;
		
		
		new ChangeRecorder(root) {
			public void notifyChanged(Notification notification) {
				super.notifyChanged(notification);
				handleModelChanges(notification);
			}
		};
		
	}
	
	
	private void handleModelChanges(Notification notification) {
		System.out.println(notification);
		
		
//		testNotification(notification);
		
		Patch patch = new Patch();
		patch.setOp(this.getPatchOp(notification));
		
//		patch.setPath("StateMachine.xmi#//@input.0/name");
		
		patch.setPath(this.getPatchPath(notification));
		patch.setValue(this.getPatchValue(notification));
		
		Payload payload = new Payload();
		payload.setData(patch);
		
		String payloadJson = converter.toJson(payload).get();
		System.out.println("payload: " + payloadJson);
		client.update(modelUri, payloadJson);
		
	}
	
	private void testNotification(Notification note) {
		Object newValue = note.getNewValue();
	    Optional<String> json = converter.toJson(newValue);
		System.out.println("new value: " +  json);
		
		Object oldValue = note.getOldValue();
	    Optional<String> old = converter.toJson(oldValue);
		System.out.println("old value: " +  old);
		
		Object not = note.getNotifier();
	    Optional<String> noteString = converter.toJson(not);
		System.out.println("notifier: " +  noteString);
		
	}
	
	private String getPatchOp(Notification notification) {
		String op = "";
		int type = notification.getEventType();
		
		switch (type) {
			case 1:
				op = "replace";
				break;
			case 3:
				op = "add";
				break;
			case 4:
				op = "remove";
				break;
		}
		
		return op;
		
	}
	
	private String getPatchPath(Notification notification) {
		String path = "/";
		
		String simpleName = notification.getClass().getSimpleName();
		String name = simpleName.substring(0,simpleName.length() - 4).toLowerCase();
		
		String index = "-";
		
		JsonNode featureJson;
		
		String feature = "?";
		
		
		
		try {
			featureJson = converter.objectToJsonNode((EObject)notification.getFeature());
			feature = featureJson.get("name").asText();
		} catch (EncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		path = path + name + index + feature;
		
		return path;
	}
	
	private String getPatchValue(Notification notification) {
		String value = notification.getNewStringValue();
		
		return value;
	}
			
	class Patch {
		private String op;
		private String path;
		private String value;
		
		public Patch() {
			
		};
		
		public String getOp() {
			return this.op;
		}
		
		public String getPath() {
			return this.path;
		}
		
		public String getValue() {
			return this.value;
		}
		
		public void setOp(String op) {
			this.op = op;
		}
		
		public void setPath(String path) {
			this.path = path;
		}
		
		public void setValue(String value) {
			this.value = value;
		}
		
	}
	
	class Payload {
		private String type;
		private Patch data;
		
		public Payload() {
			this.type = "modelserver.patch";
		}
		
		public String getType() {
			return this.type;
		}
		
		public Patch getData() {
			return this.data;
		}
		
		public void setData(Patch patch) {
			this.data = patch;
		}
		
	}

}
