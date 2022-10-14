package nl.vu.cs.bumble.emfcollaborationplugin.handlers;



import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON;
import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON_V2;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.client.v2.ModelServerClientV2;
import org.eclipse.emfcloud.modelserver.common.APIVersion;
import org.eclipse.emfcloud.modelserver.common.codecs.DefaultJsonCodec;
import org.eclipse.emfcloud.modelserver.common.codecs.EMFJsonConverter;
import org.eclipse.emfcloud.modelserver.common.codecs.EncodingException;
import org.eclipse.emfcloud.modelserver.jsonschema.Json;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import nl.vu.cs.bumble.emfcollaborationplugin.Activator;
import nl.vu.cs.bumble.statemachine.impl.StateMachineImpl;

public class EmfHandler extends AbstractHandler {

	private static final String CMD_QUIT = "quit";
	private static final String CMD_HELP = "help";
	private static final String CMD_PING = "ping";
	private static final String CMD_REDO = "redo";
	private static final String CMD_UNDO = "undo";
	
	private static final String CMD_UPDATE_TASKS = "update-tasks";
	private static final String CMD_GET = "get";
	private static final String CMD_GET_ALL = "getAll";
	private static final String CMD_UNSUBSCRIBE = "unsubscribe";
	private static final String CMD_SUBSCRIBE = "subscribe";
	private static final String CMD_ECORE = "ecore";

	private static final String STATEMACHINE_ECORE = "statemachine.ecore";
	private static final String STATEMACHINE_XMI = "TrafficSignals.xmi";
	
	private static final Integer STATUS_OK = 200;
	
	private ModelServerClient client = Activator.getModelServerClient();
	private ConvertHandler converter = ConvertHandler.getConverter();
	
	private static final String SERVER_ECORE_PATH = "file:/Users/yunabell/Desktop/ivano_project/emf/emfcloud-modelserver-master/examples/org.eclipse.emfcloud.modelserver.example/.temp/workspace/statemachine.ecore";
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
					
			System.out.println(rootElement);
			
			try {
				this.setEcorePath(rootElement);
			} catch (EncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			String modelUri = this.getNameOfModel(rootElement)+".xmi";
			
			
			if(this.isModelExistOnServer(modelUri)) {
				try {
					this.updateLocalModel(modelUri, rootElement);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					this.addModelToModelInventory(modelUri, rootElement);
				} catch (EncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
						
//			try {
//				this.getModelFromModelInventory("statemachine.ecore");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
						
			ChangeRecorder recorder = new ChangeRecorder(rootElement) {
				public void notifyChanged(Notification notification) {
					super.notifyChanged(notification);
					System.out.println(notification);
//					try {
//						updateServer(modelUri, rootElement);
//					} catch (EncodingException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
					testNotification(notification);
				}
			};
		}

			return null;
		
	};
	
	private void addModelToModelInventory(String modelUri, EObject model) throws EncodingException {
		String payload = this.convertEClassTypeToWorkspace(model);
		Response<String> response = client.create(modelUri, payload).join();
		System.out.println("response: "+response.getMessage());
	}
	
	private Boolean isModelExistOnServer(String modelUri) {
		Response<String> response = client.get(modelUri).join();
		return response.getStatusCode().equals(STATUS_OK);
	}
	
	private void updateServer(String modelUri, EObject model) throws EncodingException {
		System.out.println("update server called");
		String payload = this.convertEClassTypeToWorkspace(model);

		Response<String> response = client.update(modelUri, payload).join();
		System.out.println("response: "+response.getMessage());
	}
	
	private void updateLocalModel(String modelUri, EObject model) throws IOException {
		String response = this.getModelFromModelInventory(modelUri);
		String converted = this.convertEClassTypeToLocal(response);
		EObject updatedModel = client.decode(converted, FORMAT_JSON_V2).get();
		
//		EStructuralFeature feature = model.eContainingFeature();
//		System.out.println("feature : "+feature);
		
		try {
		 model.eResource().getContents().set(0, updatedModel);		
		 updatedModel.eResource().save(Collections.EMPTY_MAP);
		 
//		 model.eSet(feature, updatedModel);
//		 
//		 System.out.println("e set called");
//		 model.eResource().save(Collections.EMPTY_MAP);
		
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public String getModelFromModelInventory(String modelUri) throws IOException {
		Response<String> response = client.get(modelUri).join();
		String result = response.body();
		
		return result;
	}
	
	public void testNotification(Notification note) {
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
	
	private void setEcorePath(EObject model) throws EncodingException {
		JsonNode jsonRoot = converter.objectToJsonNode(model);
        String path = jsonRoot.get("eClass").asText().split("#//")[0];
        
        this.LOCAL_ECORE_PATH = path;
	}
	
	private String convertEClassTypeToWorkspace(EObject model) throws EncodingException {
		String json = client.encode(model, FORMAT_JSON_V2);
		String converted = json.replace(LOCAL_ECORE_PATH, SERVER_ECORE_PATH);
		
		return converted;
	}
	
	private String convertEClassTypeToLocal(String model) {
		String converted = model.replace(SERVER_ECORE_PATH, LOCAL_ECORE_PATH);
		
		return converted;
	}
	
	private String getNameOfModel(EObject model) {
		String name = model.eClass().getName();
		return name;
	}
	
	private EObject getRootModel(IEditorPart editor) {
		return (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
	}
	
}
