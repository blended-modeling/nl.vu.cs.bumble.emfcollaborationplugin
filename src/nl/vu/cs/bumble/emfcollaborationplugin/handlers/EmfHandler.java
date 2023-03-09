package nl.vu.cs.bumble.emfcollaborationplugin.handlers;



import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON_V2;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;

import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.fasterxml.jackson.databind.JsonNode;

import nl.vu.cs.bumble.emfcollaborationplugin.Activator;


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
						
			String modelUri = this.getNameOfModel(rootElement);
			
			this.syncModel(modelUri, rootElement);
						
			LocalChangeListenerSwitch localListenerSwitch = LocalChangeListenerSwitch.getInstance();
			SubscribeListenerSwitch subscribeListenerSwitch = SubscribeListenerSwitch.getInstance();
			
			LocalChangeHandler recorder = new LocalChangeHandler(resource, client, modelUri, LOCAL_ECORE_PATH, localListenerSwitch, subscribeListenerSwitch);		
			SubscribeHandler subsriber = new SubscribeHandler(modelUri, editor, SERVER_ECORE_PATH, LOCAL_ECORE_PATH,localListenerSwitch, subscribeListenerSwitch);
			      
			client.subscribe(modelUri, subsriber.getListener());						
		}
		return null;
	};
	
	private void syncModel(String modelUri, EObject rootElement) {
		
		if(this.isModelExistOnServer(modelUri)) {
			this.updateLocalModel(modelUri, rootElement);
		} else {
			this.addModelToModelInventory(modelUri, rootElement);
		}
		
		setLocalEcorePath(rootElement);
		setServerEcorePath(modelUri);
	}
			
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
	
	private void setServerEcorePath(String modelUri) {
		
		String path = "";
		String response;
		
		response = this.getModelFromModelInventory(modelUri);
		
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
