package com.codealike.client.eclipse;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.codealike.client.eclipse.api.ApiClient;
import com.codealike.client.eclipse.internal.services.IdentityService;
import com.codealike.client.eclipse.internal.services.ServiceListener;
import com.codealike.client.eclipse.internal.services.TrackingService;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.Configuration;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.WorkbenchUtils;

/**
 * The activator class controls the plug-in life cycle
 */
public class CodealikeTrackerPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.codealike.client.eclipse"; //$NON-NLS-1$

	// The shared instance
	private static CodealikeTrackerPlugin plugin;
	private PluginContext pluginContext;
	private ServiceListener loginObserver = new ServiceListener() {
		
		@Override
		public void onEvent() {
			TrackingService trackingService = pluginContext.getTrackingService();
			IdentityService identityService = pluginContext.getIdentityService();
			if (identityService.isAuthenticated()) {
				switch(identityService.getTrackActivity()) {
					case Always:
					{
						trackingService.enableTracking();
						break;
					}
					case AskEveryTime:
						WorkbenchUtils.addMessageToStatusBar("Codealike is not tracking your projects");
						break;
					case Never:
						WorkbenchUtils.addMessageToStatusBar("Codealike is not tracking your projects");
						break;
						
				}
			} else {
				trackingService.disableTracking();
			}
		}
	};
	
	/**
	 * The constructor
	 */
	public CodealikeTrackerPlugin() {
	}



	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		plugin = this;
		this.pluginContext = PluginContext.getInstance();
		
		try {
			pluginContext.initializeContext();
			super.start(context);

		    pluginContext.getTrackingService().setBeforeOpenProjectDate();
		    pluginContext.getIdentityService().addListener(loginObserver);
			if (!pluginContext.getIdentityService().tryLoginWithStoredCredentials()) {
				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						authenticate();
					}
				});
			}
			else {
				startTracker();
			}
		}
		catch (Exception e)
		{
			//ApiClient client = ApiClient.tryCreateNew();
			//client.logHealth(new HealthInfo(e, "Plugin could not start.", "eclipse", HealthInfoType.Error, pluginContext.getIdentityService().getIdentity()));
			LogManager.INSTANCE.logError(e, "Couldn't start plugin.");
		}
	}
	
	protected void startTracker() {
		pluginContext.getTrackingService().startTracking();
	}

	private void authenticate() {
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		String existingToken = configuration.getUserToken();
		
		InputDialog dialog = new InputDialog(null, "Codealike Authentication", "Codealike Token:", existingToken, null);
		int result = dialog.open();
		
		if (result == 0) {
			String token = dialog.getValue();
	        String[] split = token.split("/");
	        
	        if (split.length == 2) {
	            if(IdentityService.getInstance().login(split[0], split[1], true, true)) {
	                // nothing to do
	            }
	            else {
	        		MessageDialog.open(MessageDialog.ERROR, 
	        				PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
	        				"Codealike Authentication", "We couldn't authenticate you. Please verify your token and try again", SWT.NONE);
	            }
	        }
	        else {
	        	MessageDialog.open(MessageDialog.ERROR, 
        				PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
        				"Codealike Authentication", "We couldn't authenticate you. Please verify your token and try again", SWT.NONE);

	        }
		}
		
		//AuthenticationBrowserView view = new AuthenticationBrowserView();
		//view.showLogin(false);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		
		if (pluginContext != null && pluginContext.getTrackingService() != null) {
			pluginContext.getTrackingService().stopTracking(false);
		}
		
		// ensure we dispose Unirest resources
		ApiClient.Dispose();
		
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static CodealikeTrackerPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
}
