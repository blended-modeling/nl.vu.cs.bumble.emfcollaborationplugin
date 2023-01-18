package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;

import com.fasterxml.jackson.databind.JsonNode;

public class ChangeHandler {
	
	private ConvertHandler converter = ConvertHandler.getConverter();
	private ModelServerClient client;
	private String modelUri;
	private static final String OP_SET = "replace";
	private static final String OP_REMOVE = "remove";
	private static final String OP_ADD = "add";
	private static final String OP_UNKNOWN = "unknown";
	
	public ChangeHandler(Resource root, ModelServerClient client, String modelUri) {
		this.client = client;
		this.modelUri = modelUri;
		
		new ChangeRecorder(root) {
			public void notifyChanged(Notification notification) {
				super.notifyChanged(notification);
				String notificationClassName = notification.getClass().getSimpleName();

				if (notificationClassName.contains("ENotification") ) {
					handleModelChanges(notification);
				}
			}
		};
	}
	
	private void handleModelChanges(Notification notification) {
		System.out.println("notification : " +notification);
		
		String operation = this.getPatchOp(notification);
		String path = this.getPatchPath(notification, operation);
		
		Payload payload = new Payload();
		
		if (operation == OP_REMOVE || operation == OP_SET) {
			
			Patch patch = new Patch();
			
			patch.setOp(operation);
			patch.setPath(path);
			patch.setValue(notification.getNewStringValue());
			
			payload.setData(patch);
		} 
		
		if (operation == OP_ADD) {
			
			AddPatch patch = new AddPatch();
			patch.setOp(operation);
			patch.setPath(path);
			patch.setValue(this.getPatchValue(notification));
			
			
			payload.setData(patch);
		}
		
		
						
		String payloadJson = converter.toJson(payload).get();
		System.out.println("payload: " + payloadJson);
		client.update(modelUri, payloadJson);
	}
	
	private String getPatchOp(Notification notification) {
		String op = OP_UNKNOWN;
		int type = notification.getEventType();
		
		switch (type) {
			case 1:
				op = OP_SET;
				break;
			case 3:
				op = OP_ADD;
				break;
			case 4:
				op = OP_REMOVE;
				break;
		}
		
		return op;
		
	}
	
	private String getPatchPath(Notification notification, String op) {
		String path = "";
		
		EObject notifier = (EObject) notification.getNotifier();
		String uri = EcoreUtil.getURI(notifier).toString();
		
//		System.out.println("URI:" + uri);
		
		if(uri.split("#/").length > 1) {
			path = uri.split("#/")[1].replace("@", "").replace(".", "/");
		}
				
		JsonNode featureJson;
		String feature = "?";
			
		try {
			featureJson = converter.objectToJsonNode((EObject)notification.getFeature());
			feature = featureJson.get("name").asText();
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		System.out.println("path without feature: " + path);
		path = path + "/" + feature;
		
		System.out.println("path with feature: " + path);
		
		if(op == OP_REMOVE) {
			String position = Integer.toString(notification.getPosition());
			path = path + "/" + position;
		}
		
		return path;
	}
	
	private Value getPatchValue(Notification notification) {
		Value value = new Value();
		

		Object notifier = notification.getNewValue(); 
		String json = converter.toJson(notifier).get();
		System.out.println("add value: " + json);
		value.setType("testttttttt");
			
		return value;
	}

	class Value {
		private String $type;
		
		public Value() {
		};
		
		public String get$type() {
			return this.$type;
		}
		
		public void setType(String value) {
			this.$type = value;
		}
	}
		
	class StandardPatch extends Patch<String> {
	}
	
	class AddPatch extends Patch<Value> {
	}
	
	class Payload {
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
}
