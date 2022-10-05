package nl.vu.cs.bumble.emfcollaborationplugin.handlers;



import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON;
import static org.eclipse.emfcloud.modelserver.common.ModelServerPathParametersV2.FORMAT_JSON_V2;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.emfcloud.modelserver.client.Response;
import org.eclipse.emfcloud.modelserver.common.APIVersion;
import org.eclipse.emfcloud.modelserver.jsonschema.Json;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import nl.vu.cs.bumble.emfcollaborationplugin.Activator;


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

	


	@SuppressWarnings("all")
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IEditorPart editor = window.getActivePage().getActiveEditor();
		System.out.println(editor);
		if(editor instanceof IEditingDomainProvider) {
			EObject rootElement = (EObject) ((IEditingDomainProvider) editor).getEditingDomain().getResourceSet().getResources().get(0).getContents().get(0);
		
			System.out.println(rootElement);
			
			String modelUri = "StateMachine.xmi";

			try {
				this.getModelFromModelInventory(modelUri);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			ChangeRecorder recorder = new ChangeRecorder(rootElement) {
				public void notifyChanged(Notification notification) {
					super.notifyChanged(notification);
					System.out.println(notification);
				}
			};
		}

			return null;
		
	};
	
	public void getModelFromModelInventory(String modelUri) throws IOException {
		ServerNotifications notify = new ServerNotifications();
		ModelServerClient client = Activator.getModelServerClient();
		Response<String> response = client.get(modelUri).join();
		System.out.println(Json.parse(response.body()).toPrettyString());
	}
	
}
