package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;

import com.fasterxml.jackson.databind.JsonNode;

public class LocalChangeHandler {
	
	private ConvertHandler converter = ConvertHandler.getConverter();
	private ModelServerClient client;
	private String modelUri;
	private String LOCAL_ECORE_PATH;
	private EObject model;
	
	private static SubscribeListenerSwitch subscribeListenerSwitch;
	
	private static final Integer STATUS_OK = 200;
	private static final String OP_SET = "replace";
	private static final String OP_REMOVE = "remove";
	private static final String OP_ADD = "add";
	private static final String OP_UNKNOWN = "unknown";
	
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BOLD = "\033[1;30m";
	
	
	public LocalChangeHandler(ModelHandler model, ModelServerClient client,  
			LocalChangeListenerSwitch localListenerSwitch, 
			SubscribeListenerSwitch subscribeListenerSwitch) {
		this.client = client;
		this.modelUri = model.getModelUri();
		this.LOCAL_ECORE_PATH = model.getLocalPath();
//		this.model = root.getContents().get(0);
		this.model = model.getModel();
		LocalChangeHandler.subscribeListenerSwitch = subscribeListenerSwitch;
		
		new ChangeRecorder(model.getResource()) {
			
			public void notifyChanged(Notification notification) {
				
				super.notifyChanged(notification);
				String notificationClassName = notification.getClass().getSimpleName();

				if (notificationClassName.contains("ENotification") ) {
					if(localListenerSwitch.isActivated()) {
						localListenerSwitch.switchOff();
						
						// FIXME: This is not secure. 
						// Improper patch will cause the close of subscriber switch forever 
						subscribeListenerSwitch.switchOff();
						
						handleModelChanges(notification);

					} else if (subscribeListenerSwitch.isActivated()){
						localListenerSwitch.switchOn();
					}
				}
			}
		};
	}
	
	private void handleModelChanges(Notification notification) {
		
		System.out.println("notification : " +notification);
		JsonNode notifierJson = null;
		try {
			notifierJson = converter.objectToJsonNode((EObject)notification.getNotifier());
//			System.out.println("note: " + notifierJson.toPrettyString());
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		String operation = this.getPatchOp(notification);
		String path = this.getPatchPath(notification, operation);
		
		Payload payload = new Payload();
		
		if (operation == OP_REMOVE) {
			PatchRemove patch = new PatchRemove();
			
			patch.setOp(operation);
			patch.setPath(path);

			payload.setData(patch);
		}
		
		if (operation == OP_ADD) {
			Patch patch = new Patch();
			
			patch.setOp(operation);
			patch.setPath(path);
			patch.setValue(this.getPatchValue(notification));
			
			payload.setData(patch);
		}
		
		if (operation == OP_SET) {
			JsonNode newFeature = null;
			String featureType = "";
			
			Patch patch = new Patch();
			
			patch.setOp(operation);
			patch.setPath(path);
			
			try {
				newFeature = converter.objectToJsonNode((EObject)notification.getFeature());
				featureType = newFeature.get("eClass").asText().split("#//")[1];
				
			} catch (EncodingException e) {
				e.printStackTrace();
			}
			
			if (featureType.equals("EAttribute")) {
				patch.setValue(notification.getNewStringValue());
			}
			
			if (featureType.equals("EReference")) {
				String featureName = newFeature.get("name").asText();
				String ref = modelUri + "#";
				
				if(notification.getNewStringValue() != null) {
					ref = ref + notifierJson.get(featureName).get("$ref").asText();
				}
				
				System.out.println("ref : " + ref);

				Value refValue = new Value();
				refValue.setRef(ref);
				patch.setValue(refValue);
			}
			payload.setData(patch);
		} 
		

		String payloadJson = converter.toJson(payload).get();
		System.out.println(ANSI_BOLD + "Patch SEND: " + payloadJson + ANSI_RESET);
		
		Response<String> response = client.update(modelUri, payloadJson).join();
		System.out.println(ANSI_BOLD+ "RESPONSE STATUS: " + response.getStatusCode()+ ANSI_RESET);
		
//		if(!response.getStatusCode().equals(STATUS_OK) ) {
//			System.out.println("response not ok code: " + response.getStatusCode());
//			subscribeListenerSwitch.switchOn();
//		}
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
		
		path = path + "/" + feature;
		
		if(op == OP_REMOVE) {
			String position = Integer.toString(notification.getPosition());
			path = path + "/" + position;
		}
		
		/**
		 * To add object at specific position, the position index has to be attached.
		 * But if the newly added object is the last element in a particular feature,
		 * the server only accept "-" instead of position index or simply without position.
		 * Therefore, a counting on the existing number of elements is needed.
		 * NOTICE: Only root model feature structure is get, if the meta-model allows adding child object,
		 * this implementation fetch the wrong objects to count.
		 * */
		if(op == OP_ADD) {
			
			EStructuralFeature featureStructure = model.eClass().getEStructuralFeature(feature);
			@SuppressWarnings("unchecked")
			EList<EObject> list =(EList<EObject>)model.eGet(featureStructure);
			
			if(list.size() > notification.getPosition()) {
				String position = Integer.toString(notification.getPosition());
				path = path + "/" + position;
			}
		}
		
		return path;
	}
	
	// FIXME: Need to check if exists any situations require both type and ref
	private Value getPatchValue(Notification notification) {
		
		Value value = new Value();
		
		JsonNode featureJson = null;
		JsonNode newValueJson = null;
		String feature = "";
		String valueType = "";
		
		try {
			newValueJson = converter.objectToJsonNode((EObject)notification.getNewValue());
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		if(newValueJson.has("eClass")) {
			valueType = newValueJson.get("eClass").asText();
		} else {
			try {
				featureJson = converter.objectToJsonNode((EObject)notification.getFeature());
				System.out.println("feature JSON: " + featureJson.toPrettyString());
				feature = featureJson.get("eType").get("$ref").asText();
				
			} catch (EncodingException e) {
				e.printStackTrace();
			}
			
			valueType = LOCAL_ECORE_PATH + "#" + feature;
		}
				
		value.setType(valueType);
			
		return value;
	}
}
