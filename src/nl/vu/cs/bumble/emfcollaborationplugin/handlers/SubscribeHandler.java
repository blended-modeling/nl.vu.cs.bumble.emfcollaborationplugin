package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.client.SubscriptionListener;
import org.eclipse.emfcloud.modelserver.common.APIVersion;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;
import org.eclipse.emfcloud.modelserver.example.client.ExampleEObjectSubscriptionListener;
import org.eclipse.emfcloud.modelserver.example.util.PrintUtil;
import org.eclipse.emfcloud.modelserver.jsonpatch.JsonPatch;
import org.eclipse.emfcloud.modelserver.jsonpatch.Operation;
import org.eclipse.ui.IEditorPart;

import com.fasterxml.jackson.databind.JsonNode;

public class SubscribeHandler {
	private static final APIVersion API_VERSION = APIVersion.API_V2;
	private String SERVER_ECORE_PATH;
	private String LOCAL_ECORE_PATH;
	private ConvertHandler converter = ConvertHandler.getConverter();
	private SubscriptionListener listener;

	public SubscribeHandler(String modelUri, IEditorPart editor,
			String SERVER_ECORE_PATH,
			String LOCAL_ECORE_PATH,
			LocalChangeListenerSwitch localListenerSwitch, 
			SubscribeListenerSwitch subscribeListenerSwitch ) {
		
		this.listener = new ExampleEObjectSubscriptionListener(modelUri, API_VERSION) {
			    
			   public void onIncrementalUpdate(final JsonPatch patch) {			   
				   
				   // Make sure not to miss any incoming patches
//				   if(!localListenerSwitch.isActivated() && !subscribeListenerSwitch.isActivated()) {
//					   localListenerSwitch.switchOn();
//					   subscribeListenerSwitch.switchOn();
//				   }
				   
				   if(localListenerSwitch.isActivated()) {
				      localListenerSwitch.switchOff();
				      subscribeListenerSwitch.switchOff();
				      printResponse(
						         "Incremental <JsonPatch> update from model server received:\n" + PrintUtil.toPrettyString(patch));	
				      
				   	  try {
				   		executeJsonPatch(patch, editor);
				   	  } catch(Exception e) {
				   		e.printStackTrace();
				   	  }
				     
				      subscribeListenerSwitch.switchOn();
				      localListenerSwitch.switchOn();
				      
				   } else {
				      localListenerSwitch.switchOn();
				   }
			   }
		};       
	}
	
	public SubscriptionListener getListener() {
		return listener;
	}
	
	private void executeJsonPatch(JsonPatch patches, IEditorPart editor) throws Exception {		
		
		EObject model = getRootModel(editor);
		
		for(int i = 0; i < patches.getPatch().size(); i++) {
			Operation patch = patches.getPatch().get(i);
			String op = patch.getOp().toString();
			
			if(op == "replace") {
				this.applyReplacePatch(model, patch);
			}
			if(op == "remove") {
				this.applyRemovePatch(model, patch);
			}
			if(op == "add") {
				this.applyAddPatch(model, patch);
			}
		}
		
		try {
			model.eResource().getContents().add(model);
			model.eResource().save(Collections.EMPTY_MAP);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
	}
	
	private void applyReplacePatch(EObject model, Operation patch) {
		
		String path = patch.getPath();		
		String[] paths = path.split("/");
				
		String featureName = paths[paths.length - 1];
		
		if (!featureName.equals("$ref")) {
			EObject objToPatch = this.findObjToPatch(model, paths, 1);	
			try {
				replaceFeatureValue(objToPatch, featureName, patch);
			} catch (EncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (featureName.equals("$ref")) {
			replaceReferenceValue(model, patch, paths);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void applyRemovePatch(EObject model, Operation patch) {
		try {
			JsonNode patchJson = converter.objectToJsonNode(patch);
			System.out.println("to patch json: "+patchJson.toPrettyString());
			
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		String path = patch.getPath();		
		String[] paths = path.split("/");
		
		if(!pathIncludesTargetIndex(paths)) {
			path = path+ "/0";
			paths = path.split("/");
		}
		
		EObject objToPatch = model;
		
		// FIXME: Remove EAttribute does not working properly
		try {
			objToPatch = this.findObjToPatch(model, paths, 0);
			EcoreUtil.delete(objToPatch);
		} catch(Exception e) {
			applyReplacePatch(model, patch);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void applyAddPatch(EObject model, Operation patch) {
		
		String path = patch.getPath();	
		
		if(!path.contains("-")) {
			path = path + "/-";
		}
		
		String[] paths = path.split("/");
		
		EObject objToPatch = model;
		String featureName = "";
		
		
		if (!paths[2].equals("-")) {
			objToPatch = this.findObjToPatch(model, paths, 2);
		}
		
		// path: /input/-
		featureName = paths[paths.length - 2];
				
		EStructuralFeature feature = objToPatch.eClass().getEStructuralFeature(featureName);
				
		String featureType = "EReference";
		
		try {
			JsonNode featureJson = converter.objectToJsonNode(feature);
			featureType = featureJson.get("eClass").asText();
			featureType = featureType.split("#//")[1];
		} catch (EncodingException e) {
			e.printStackTrace();
		}
				
		if (featureType.equals("EReference")) {
			EList<EObject> list =(EList<EObject>)objToPatch.eGet(feature);
			
			EObject newObj = this.createNewObject(model, patch);
			
			// FIXME: if EReference is an existing Object, it does not work right now.
			if(list == null) {
				objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(featureName), newObj);
			} else {
				list.add(newObj);
			}
		}
		
		if (featureType.equals("EAttribute")) {
			try {
				replaceFeatureValue(objToPatch, featureName, patch);
			} catch (EncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private EObject createNewObject(EObject model, Operation patch) {
		
		String className = "";
		
		// FIXME: sometimes the patch is converted to a JsonNode without value
		try {
			JsonNode patchJson = converter.objectToJsonNode(patch);
//			System.out.println("to patch json: "+patchJson.toPrettyString());
			JsonNode valueNode = patchJson.get("value");
			className = valueNode.get("value").get("eClass").asText();
		
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
//		className = this.convertEClassTypeToLocal(className);
		
		// FIXME: can be incorrect 
		className = className.split("#//")[1];
		
		System.out.println("class name : " + className);
		
		EPackage modelPackage = model.eClass().getEPackage();		
		EClassifier classif = modelPackage.getEClassifier(className);
		System.out.println("classifier: " + classif);
		
		EFactory modelFactory = modelPackage.getEFactoryInstance();
		
		EObject newObj = null;
		
		if (classif != null && classif instanceof EClass) {
			  newObj = modelFactory.create((EClass) classif);
		}
		
		return newObj;
	}
	
	private EObject findObjToPatch(EObject model, String[] paths, int len) {	
		
		EObject objToPatch = model.eContents().get(0);
		String rootClassName = paths[1];
		int rootPosition = Integer.parseInt(paths[2]);
		
		EList<EObject> contents = model.eContents();
		
		int counter = 0;
				
		for(int i = 0; i < contents.size(); i++) {
			EObject rootNode = model.eContents().get(i);
			String objClassName = rootNode.eContainmentFeature().getName();
			
			if( objClassName.equals(rootClassName)) {				
				if(counter == rootPosition) {
					objToPatch = rootNode;
					break;
				} else {
					counter++;
				}
			}
		}
		
		paths = Arrays.copyOfRange(paths, 3, paths.length);

		while(paths.length > len) {
			int subNodePosition = Integer.parseInt(paths[1]);
			
			paths = Arrays.copyOfRange(paths, 2, paths.length);
			objToPatch = objToPatch.eContents().get(subNodePosition);
		}
		
		return objToPatch;
	}
		
	
	
	private boolean pathIncludesTargetIndex(String[] paths) {
		// path: /input => paths.length = 2
		// path: /input/0 => paths.length = 3
		return paths.length % 2 == 1;
	}
	

	
	private void replaceFeatureValue(EObject objToPatch, String featureName, Operation patch) throws EncodingException {
		
		String newValue = "";
		
		try {
			JsonNode patchJson = converter.objectToJsonNode(patch);
			JsonNode valueNode = patchJson.get("value");
			if(valueNode != null) {
				newValue = valueNode.get("value").asText();
			} 
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		EStructuralFeature f =  objToPatch.eClass().getEStructuralFeature(featureName);
		if (f != null) {
			objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(featureName), newValue);
		}
	}
	
	/**
	 * Example Patch
	 * op: replace
	 * path: /transitions/0/input/$ref
	 * value: //@inputs.0
	 * 
	 * refName = "input"
	 * featureName = "inputs"
	 **/
	@SuppressWarnings("unchecked")
	private void replaceReferenceValue(EObject model, Operation patch, String[] paths ) {
		
		JsonNode patchJson = null;
		try {
			patchJson = converter.objectToJsonNode(patch);			
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		String value = patchJson.get("value").get("value").asText();
		
		paths = Arrays.copyOfRange(paths, 0, paths.length - 1);
		String refName = paths[paths.length - 1];
		
		EObject objToPatch = this.findObjToPatch(model, paths, 1);
		
		// The following value is the same as an empty value
		// value: $command.exec.res#//@changeDescription/@objectsToAttach.0
		if(!value.contains("$command.exec.res")) {
			
			String featureName = value.split("[.]")[0].split("@")[1];
			int position = Integer.parseInt(value.split("[.]")[1]);
			
			//FIXME: Only work for one layer model.
			EStructuralFeature feature = model.eClass().getEStructuralFeature(featureName);
			EList<EObject> list =(EList<EObject>)model.eGet(feature);
			objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(refName), list.get(position));
			
		} else {
			objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(refName), null);
		} 
	}
	
	private EObject getRootModel(IEditorPart editor) {
		
		return (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
	}
}
