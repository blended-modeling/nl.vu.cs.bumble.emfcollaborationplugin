package nl.vu.cs.bumble.emfcollaborationplugin;

import org.eclipse.emfcloud.modelserver.client.ModelServerClient;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import nl.vu.cs.bumble.emfcollaborationplugin.handlers.StateMachineConfiguration;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "nl.vu.cs.bumble.emfcollaborationplugin"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;
	
	private static ModelServerClient modelServerClient;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		modelServerClient = new ModelServerClient("http://localhost:8081/api/v2/",
	            new StateMachineConfiguration());
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}
	
	public static ModelServerClient getModelServerClient() {
		return modelServerClient;
	}

}
