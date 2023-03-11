package nl.vu.cs.bumble.emfcollaborationplugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emfcloud.modelserver.client.ModelServerClient;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import nl.vu.cs.bumble.emfcollaborationplugin.Activator;


public class EmfHandler extends AbstractHandler {

	private static final Integer STATUS_OK = 200;	
	private ModelServerClient client = Activator.getModelServerClient();
		
	@SuppressWarnings("all")
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IEditorPart editor = window.getActivePage().getActiveEditor();
		System.out.println(editor);
		
		if(editor instanceof IEditingDomainProvider) {
			
			ModelHandler model = new ModelHandler(editor, client);
						
			LocalChangeListenerSwitch localListenerSwitch = LocalChangeListenerSwitch.getInstance();
			SubscribeListenerSwitch subscribeListenerSwitch = SubscribeListenerSwitch.getInstance();
			
			LocalChangeHandler recorder = new LocalChangeHandler(model, client, localListenerSwitch, subscribeListenerSwitch);		
			SubscribeHandler subsriber = new SubscribeHandler(model, editor, localListenerSwitch, subscribeListenerSwitch);
			      
			client.subscribe(model.getModelUri(), subsriber.getListener());						
		}
		return null;
	};
}
