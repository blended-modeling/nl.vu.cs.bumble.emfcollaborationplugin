package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON_V2;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;
import org.eclipse.ui.IEditorPart;

import com.fasterxml.jackson.databind.JsonNode;

import nl.vu.cs.bumble.emfcollaborationplugin.Activator;

public class ModelHandler {
	
	private static final Integer STATUS_OK = 200;
	
	private IEditorPart editor;
	private Resource resource;
	
	private EObject model;
	private String modelUri;
	private String SERVER_ECORE_PATH = "";
	private String LOCAL_ECORE_PATH = "";
	
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN = "\033[1;32m";
	
	private ConvertHandler converter = ConvertHandler.getConverter();
	private ModelServerClient client;

	
	
	public ModelHandler(IEditorPart editor, ModelServerClient client) {
		this.editor = editor;
		this.client = client;
		
		model = this.getRootModel();
		resource = ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0);
		modelUri = this.getNameOfModel();
		
		this.initModel();
	}
	
	
	
	public EObject getModel() {
		return this.model;
	}
	
	public String getModelUri() {
		return this.modelUri;
	}
	
	public Resource getResource() {
		return this.resource;
	}
	
	public String getLocalPath() {
		return this.LOCAL_ECORE_PATH;
	}
	
	private void initModel() {
		
		if(this.isModelExistOnServer()) {
			this.updateLocalModel();
		} else {
			this.addModelToModelInventory();
		}
		
		setLocalEcorePath();
		setServerEcorePath();
		
		System.out.println(ANSI_GREEN + "Model Synchronized" + ANSI_RESET);
	}
			
	private void addModelToModelInventory() {
		
		String payload = this.convertEClassTypeToWorkspace();
		Response<String> response = client.create(modelUri, payload).join();
	}
	
	private String getModelFromModelInventory() {
		
		Response<String> response = client.get(modelUri).join();
		String result = response.body();
		
		return result;
	}
	
	private Boolean isModelExistOnServer() {		
		
		Response<String> response = client.get(modelUri).join();
		return response.getStatusCode().equals(STATUS_OK);
	}
		
	private void updateLocalModel() {
		
		String response = this.getModelFromModelInventory();
		String converted = this.convertEClassTypeToLocal(response);
		EObject updatedModel = client.decode(converted, FORMAT_JSON_V2).get();
		
		
		try {
		 model.eResource().getContents().set(0, updatedModel);		
		 updatedModel.eResource().save(Collections.EMPTY_MAP);
		 
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	private void setLocalEcorePath() {
		
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
		
		response = this.getModelFromModelInventory();
		
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
	
	private String convertEClassTypeToWorkspace() {
		
		String json = client.encode(model, FORMAT_JSON_V2);
		String converted = json.replace(LOCAL_ECORE_PATH, SERVER_ECORE_PATH);
		
		return converted;
	}
	
	private String convertEClassTypeToLocal(String repsonseModel) {
		
		String converted = repsonseModel.replace(SERVER_ECORE_PATH, LOCAL_ECORE_PATH);	
		return converted;
	}
	
	private String getNameOfModel() {
		
		String name = model.eResource().getURI().lastSegment();
		return name;
	}
	
	private EObject getRootModel() {
		
		return (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
	}

}
