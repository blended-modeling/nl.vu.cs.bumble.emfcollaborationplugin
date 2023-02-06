package nl.vu.cs.bumble.emfcollaborationplugin.handlers;



import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON;
import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON_V2;
import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_XMI;
import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.PATHS_URI_FRAGMENTS;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.client.SubscriptionListener;
import org.eclipse.emfcloud.modelserver.client.SubscriptionOptions;
import org.eclipse.emfcloud.modelserver.client.v2.ModelServerClientV2;
import org.eclipse.emfcloud.modelserver.common.APIVersion;
import org.eclipse.emfcloud.modelserver.common.codecs.DefaultJsonCodec;
import org.eclipse.emfcloud.modelserver.common.codecs.EMFJsonConverter;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;
import org.eclipse.emfcloud.modelserver.example.client.ExampleEObjectSubscriptionListener;
import org.eclipse.emfcloud.modelserver.example.client.ExampleJsonStringSubscriptionListener;
import org.eclipse.emfcloud.modelserver.example.client.ExampleXMISubscriptionListener;
import org.eclipse.emfcloud.modelserver.example.util.PrintUtil;
import org.eclipse.emfcloud.modelserver.jsonpatch.JsonPatch;
import org.eclipse.emfcloud.modelserver.jsonpatch.Operation;
import org.eclipse.emfcloud.modelserver.jsonschema.Json;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.vu.cs.bumble.emfcollaborationplugin.Activator;
import nl.vu.cs.bumble.statemachine.Input;
import nl.vu.cs.bumble.statemachine.impl.StateMachineImpl;

public class EmfHandler extends AbstractHandler {
	
	private static final APIVersion API_VERSION = APIVersion.API_V2;
	private static final Integer STATUS_OK = 200;
	
	private ModelServerClient client = Activator.getModelServerClient();
	private ConvertHandler converter = ConvertHandler.getConverter();
	
	private String SERVER_ECORE_PATH = "";
	private String LOCAL_ECORE_PATH = "";
		
	@SuppressWarnings("all")
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IEditorPart editor = window.getActivePage().getActiveEditor();
		System.out.println(editor);
		if(editor instanceof IEditingDomainProvider) {
			EObject rootElement = this.getRootModel(editor);
			Resource resource = ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0);
			
//			System.out.println(rootElement.eContents());
//			((StateMachineImpl) rootElement).setName("random");
//			try {
////				((StateMachineImpl) rootElement).eResource().getContents().add(rootElement);
//				((StateMachineImpl) rootElement).eResource().save(Collections.EMPTY_MAP);
//			} catch (IOException e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
			
			try {
				this.setLocalEcorePath(rootElement);
			} catch (EncodingException e1) {
				e1.printStackTrace();
			}
			
			try {
				this.setServerEcorePath();
			} catch (IOException e1) {
				e1.printStackTrace();
			} catch (EncodingException e) {
				e.printStackTrace();
			}
			
			String modelUri = this.getNameOfModel(rootElement);
			System.out.println("model name: " + modelUri);
			
			
			if(this.isModelExistOnServer(modelUri)) {
				try {
					this.updateLocalModel(modelUri, rootElement);

				} catch (IOException e) {
					e.printStackTrace();
				}
				
			} else {
				try {
					this.addModelToModelInventory(modelUri, rootElement);
					
				} catch (EncodingException e) {
					e.printStackTrace();
				}
			}	
			
			ChangeFlag changeFlag = new ChangeFlag();
			
			ChangeHandler recorder = new ChangeHandler(resource, client, modelUri, LOCAL_ECORE_PATH, changeFlag);
		
//			SubscriptionListener listener = new ExampleXMISubscriptionListener(modelUri) {
//				public void onIncrementalUpdate(final EObject incrementalUpdate) {
//				      printResponse("Incremental <XmiEObject> update from model server received: " + incrementalUpdate.toString());
//
//				      try {
//						System.out.println("xmi patch: " + converter.objectToJsonNode(incrementalUpdate).toPrettyString());
//					} catch (EncodingException e) {
//						e.printStackTrace();
//					}
//				   }
//			};
			
			SubscriptionListener listener = new ExampleEObjectSubscriptionListener(modelUri, API_VERSION) {
				   public void onIncrementalUpdate(final JsonPatch patch) {
					      printResponse(
					         "Incremental <JsonPatch> update from model server received:\n" + PrintUtil.toPrettyString(patch));
					      if(changeFlag.getFlag()) {
					    	  executeJsonPatch(patch, editor);
					      }
					      changeFlag.setFlag(true);
					   }
			};
			           
			client.subscribe(modelUri, listener);						
		}
			return null;
		
	};
	
	private void executeJsonPatch(JsonPatch patches, IEditorPart editor) {
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
	
	@SuppressWarnings("unchecked")
	private void applyAddPatch(EObject model, Operation patch) {
		
		
		String path = patch.getPath();		
		String[] paths = path.split("/");
		
		
		paths = Arrays.copyOfRange(paths, 0, paths.length-1);
		
		String objName = "";
		
		try {
		JsonNode patchJson = converter.objectToJsonNode(patch);

		JsonNode valueNode = patchJson.get("value");
		
		objName = valueNode.get("value").get("eClass").asText();
		
		} catch (EncodingException e) {
		e.printStackTrace();
		}
		
		objName = this.convertEClassTypeToLocal(objName);
		
		// TODO: can be incorrect 
		objName = objName.split("#//")[1];
		
		System.out.println("obj name : " + objName);
		
		EObject testObj = model.eContents().get(1);
		
		System.out.println("test obj: " + testObj);
				
		
		System.out.println("class name: " + testObj.eClass().getInstanceClassName());
		
		EPackage testPackage = model.eClass().getEPackage();
		
		EClassifier classif = testPackage.getEClassifier(objName);
		
		System.out.println("classifier: " + classif);
		
//		EList<EClassifier> classifs = testPackage.getEClassifiers();
//		
//		
//		for(EClassifier cls : classifs) {
//			System.out.println("clas: " + cls);
//		}
		
		EObject newObj = null;
		
		if (classif != null && classif instanceof EClass) {

			  newObj = EcoreUtil.create((EClass)classif);
			  System.out.println("new obj: " + newObj);
		}
//		EClass eClass =
//	    EObject newObj = EcoreUtil.create(eClass);
		
		EList<EStructuralFeature> allEStructFeats = model.eClass().getEAllStructuralFeatures();
		
//		for(EStructuralFeature esf : allEStructFeats)
//		{
//			System.out.println("feature ID: " + esf.getFeatureID());
//			System.out.println("feature Name: " + esf.getName());
//		    System.out.println("object: " + model.eGet(esf));
//		    if(esf.getName() == "input") {
//			    EList<EObject> list =(EList<EObject>)model.eGet(esf);
//			    list.add(copyObj);
//		    }
//		}
		
		
		EStructuralFeature feature = model.eClass().getEStructuralFeature("input");
		
		System.out.println("feature get input: " + feature);
		
		EList<EObject> list =(EList<EObject>)model.eGet(feature);
		list.add(newObj);
		
//		System.out.println( "Feature: " + testObj.eClass().getEStructuralFeature("name"));
//		
//		Object list =testObj.eGet(testObj.eClass().getEStructuralFeature("name"));
//		list.add(copyObj);
		
//		JsonNode jsonRoot;
//		try {
//			jsonRoot = converter.objectToJsonNode(model);
//			System.out.println("model in json: " + jsonRoot.toPrettyString());
//			
//			JsonNode testLocation = jsonRoot.path("input");
//			ObjectNode addedNode = ((ObjectNode) testLocation).putObject("input");
//			
//			System.out.println("model in json after: " + jsonRoot.toPrettyString());
//		} catch (EncodingException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		
//		EClass eClass =
//		
//		EObject newObj = EcoreUtil.create(eClass);
//		

//		EList<EObject> contents = model.eContents();
//		contents.add(newObj);

		if(paths.length == 1) {
			
		}
//		String rootClassName = paths[1];
//		int rootPosition = Integer.parseInt(paths[2]);
//		String newValue = "";
//		

//		
//		EObject objToPatch = model.eContents().get(0);
//		
//		EList<EObject> contents = model.eContents();
		
//		int counter = 0;
//				
//		for(int i = 0; i < contents.size(); i++) {
//			EObject rootNode = model.eContents().get(i);
//			String objClassName = rootNode.eContainmentFeature().getName();
//			
//			if( objClassName.equals(rootClassName)) {				
//				if(counter == rootPosition) {
//					objToPatch = rootNode;
//					break;
//				} else {
//					counter++;
//				}
//			}
//		}
//		
//		// /input/0/baseconcept/1/baseconcept/- -> /baseconcept/1/
//		paths = Arrays.copyOfRange(paths, 3, paths.length-1);
//		
//		
//		while(paths.length > 0) {
//			int subNodePosition = Integer.parseInt(paths[1]);
//			
//			// /baseconcept/1/baseconcept/0  -> /baseconcept/0
//			paths = Arrays.copyOfRange(paths, 2, paths.length);
//			objToPatch = objToPatch.eContents().get(subNodePosition);
//		}

		
//		System.out.println("test : "+objToPatch);
//		System.out.println("test 2: "+objToPatch.eContainingFeature());
		
//		objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(featureName), newValue);
//		System.out.println("test 3: "+ objToPatch);
	}
	
	private void applyRemovePatch(EObject model, Operation patch) {
		String path = patch.getPath();		
		String[] paths = path.split("/");
		
		String rootClassName = paths[1];
		int rootPosition = Integer.parseInt(paths[2]);
		
		EObject objToPatch = model.eContents().get(0);
		
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
		
		
		while(paths.length > 0) {
			int subNodePosition = Integer.parseInt(paths[1]);
			
			paths = Arrays.copyOfRange(paths, 2, paths.length);
			objToPatch = objToPatch.eContents().get(subNodePosition);
		}
		
		EcoreUtil.delete(objToPatch);
		
	}
	
	private void applyReplacePatch(EObject model, Operation patch) {
		String path = patch.getPath();		
		String[] paths = path.split("/");
				
		String rootClassName = paths[1];
		int rootPosition = Integer.parseInt(paths[2]);
		String featureName = paths[paths.length - 1];
		String newValue = "";
		
		try {
			JsonNode patchJson = converter.objectToJsonNode(patch);
			JsonNode valueNode = patchJson.get("value");
			
			String valueType = valueNode.get("eClass").asText();
			newValue = valueNode.get("value").asText();
			
			System.out.println("new value type: " + valueType);
			System.out.println("new value: " + newValue);
			
		} catch (EncodingException e) {
			e.printStackTrace();
		}
		
		EObject objToPatch = model.eContents().get(0);
		
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
		
		// /input/0/baseconcept/1/baseconcept/0/name -> /baseconcept/1/baseconcept/0
		paths = Arrays.copyOfRange(paths, 3, paths.length-1);
		
		
		while(paths.length > 0) {
			int subNodePosition = Integer.parseInt(paths[1]);
			
			// /baseconcept/1/baseconcept/0  -> /baseconcept/0
			paths = Arrays.copyOfRange(paths, 2, paths.length);
			objToPatch = objToPatch.eContents().get(subNodePosition);
		}

		
//		System.out.println("test : "+objToPatch);
//		System.out.println("test 2: "+objToPatch.eContainingFeature());
		System.out.println("featureName: "+ featureName);
		objToPatch.eSet(objToPatch.eClass().getEStructuralFeature(featureName), newValue);
//		System.out.println("test 3: "+ objToPatch);
	}
			
	private void addModelToModelInventory(String modelUri, EObject model) throws EncodingException {
		String payload = this.convertEClassTypeToWorkspace(model);
		Response<String> response = client.create(modelUri, payload).join();
	}
	
	private Boolean isModelExistOnServer(String modelUri) {		
		Response<String> response = client.get(modelUri).join();
		return response.getStatusCode().equals(STATUS_OK);
	}
		
	private void updateLocalModel(String modelUri, EObject model) throws IOException {
		String response = this.getModelFromModelInventory(modelUri);
		String converted = this.convertEClassTypeToLocal(response);
		EObject updatedModel = client.decode(converted, FORMAT_JSON_V2).get();
		
		
		try {
		 model.eResource().getContents().set(0, updatedModel);		
		 updatedModel.eResource().save(Collections.EMPTY_MAP);
		 
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public String getModelFromModelInventory(String modelUri) throws IOException {
		Response<String> response = client.get(modelUri).join();
		String result = response.body();
		
		return result;
	}
	
	private void setLocalEcorePath(EObject model) throws EncodingException {
		JsonNode jsonRoot = converter.objectToJsonNode(model);
        String path = jsonRoot.get("eClass").asText().split("#//")[0];
        
        this.LOCAL_ECORE_PATH = path;
	}
	
	private void setServerEcorePath() throws IOException, EncodingException {
		String path = "";
		String response = this.getModelFromModelInventory("EcoreURI.xmi");
		
		EObject obj = client.decode(response, FORMAT_JSON_V2).get();
		JsonNode json = converter.objectToJsonNode(obj);
		String raw = json.get("eClass").toString().split("#//")[0];
		path = raw.substring(1);
		
		this.SERVER_ECORE_PATH = path;
	}
	
	private String convertEClassTypeToWorkspace(EObject model) throws EncodingException {
		String json = client.encode(model, FORMAT_JSON_V2);
		
//		System.out.println("local: " + LOCAL_ECORE_PATH);
//		System.out.println("server: " + SERVER_ECORE_PATH);
		
		String converted = json.replace(LOCAL_ECORE_PATH, SERVER_ECORE_PATH);
//		String converted = json; 
		
		return converted;
	}
	
	private String convertEClassTypeToLocal(String model) {
		String converted = model.replace(SERVER_ECORE_PATH, LOCAL_ECORE_PATH);
		
//		System.out.println("local: " + LOCAL_ECORE_PATH);
//		System.out.println("server: " + SERVER_ECORE_PATH);
		
		return converted;
//		return model;
	}
	
	private String getNameOfModel(EObject model) {
		String name = model.eResource().getURI().lastSegment();
//		String name = "TrafficSignals.statemachine";
		return name;
	}
	
	private EObject getRootModel(IEditorPart editor) {
		return (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
	}
	
}
