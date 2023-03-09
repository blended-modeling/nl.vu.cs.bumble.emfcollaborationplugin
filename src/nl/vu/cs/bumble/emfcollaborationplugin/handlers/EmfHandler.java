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
import java.util.Date;
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
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
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
import org.eclipse.emfcloud.modelserver.client.JsonToEObjectSubscriptionListener;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.client.SubscriptionListener;
import org.eclipse.emfcloud.modelserver.client.SubscriptionOptions;
import org.eclipse.emfcloud.modelserver.client.XmiToEObjectSubscriptionListener;
import org.eclipse.emfcloud.modelserver.client.v2.ModelServerClientV2;
import org.eclipse.emfcloud.modelserver.command.CCommandExecutionResult;
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
						
			this.setLocalEcorePath(rootElement);
			this.setServerEcorePath();
			
			String modelUri = this.getNameOfModel(rootElement);
			
			if(this.isModelExistOnServer(modelUri)) {
				this.updateLocalModel(modelUri, rootElement);
			} else {
				this.addModelToModelInventory(modelUri, rootElement);
			}	
			
			LocalChangeListenerSwitch localListenerSwitch = LocalChangeListenerSwitch.getInstance();
			SubscribeListenerSwitch subscribeListenerSwitch = SubscribeListenerSwitch.getInstance();
			
			LocalChangeHandler recorder = new LocalChangeHandler(resource, client, modelUri, LOCAL_ECORE_PATH, localListenerSwitch, subscribeListenerSwitch);		
			SubscribeHandler subsriber = new SubscribeHandler(modelUri, editor, SERVER_ECORE_PATH, LOCAL_ECORE_PATH,localListenerSwitch, subscribeListenerSwitch);
			      
			client.subscribe(modelUri, subsriber.getListener());						
		}
			return null;
	};
			
	private void addModelToModelInventory(String modelUri, EObject model) {
		String payload = this.convertEClassTypeToWorkspace(model);
		Response<String> response = client.create(modelUri, payload).join();
	}
	
	private Boolean isModelExistOnServer(String modelUri) {		
		Response<String> response = client.get(modelUri).join();
		return response.getStatusCode().equals(STATUS_OK);
	}
		
	private void updateLocalModel(String modelUri, EObject model) {
		
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
	
	public String getModelFromModelInventory(String modelUri) {
		Response<String> response = client.get(modelUri).join();
		String result = response.body();
		
		return result;
	}
	
	private void setLocalEcorePath(EObject model) {
		String path = "";
		JsonNode jsonRoot;
		try {
			jsonRoot = converter.objectToJsonNode(model);
			path = jsonRoot.get("eClass").asText().split("#//")[0];
		} catch (EncodingException e) {
			e.printStackTrace();
		}

        this.LOCAL_ECORE_PATH = path;
	}
	
	private void setServerEcorePath() {
		String path = "";
		String response;
		response = this.getModelFromModelInventory("TrafficSignals.statemachine");
		
		EObject obj = client.decode(response, FORMAT_JSON_V2).get();
		JsonNode json;
		try {
			json = converter.objectToJsonNode(obj);
			
			String raw = json.get("eClass").toString().split("#//")[0];
			path = raw.substring(1);
		} catch (EncodingException e) {
			e.printStackTrace();
		}
			
		this.SERVER_ECORE_PATH = path;
	}
	
	private String convertEClassTypeToWorkspace(EObject model) {
		String json = client.encode(model, FORMAT_JSON_V2);
		String converted = json.replace(LOCAL_ECORE_PATH, SERVER_ECORE_PATH);
		
		return converted;
	}
	
	private String convertEClassTypeToLocal(String model) {
		String converted = model.replace(SERVER_ECORE_PATH, LOCAL_ECORE_PATH);	
		return converted;
	}
	
	private String getNameOfModel(EObject model) {
		String name = model.eResource().getURI().lastSegment();
		return name;
	}
	
	private EObject getRootModel(IEditorPart editor) {
		return (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
	}
	
}
