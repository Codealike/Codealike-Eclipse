package com.codealike.client.eclipse;

import java.util.Observable;
import java.util.Observer;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.codealike.client.eclipse.internal.services.IdentityService;
import com.codealike.client.eclipse.internal.services.TrackingService;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.WorkbenchUtils;
import com.codealike.client.eclipse.views.AuthenticationBrowserView;

/**
 * The activator class controls the plug-in life cycle
 */
public class CodealikeTrackerPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.codealike.client.eclipse"; //$NON-NLS-1$

	// The shared instance
	private static CodealikeTrackerPlugin plugin;
	private PluginContext pluginContext;
	private Observer loginObserver = new Observer() {
		
		@Override
		public void update(Observable o, Object arg1) {
			
			if (o == pluginContext.getIdentityService()) {
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
				}
				else {
					trackingService.disableTracking();
				}
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
		super.start(context);
		plugin = this;
		this.pluginContext = PluginContext.getInstance();
	    pluginContext.initializeContext();
	    
	    if (!pluginContext.checkVersion()) {
	    	throw new Exception();
	    }
	    
	    pluginContext.getTrackingService().setBeforeOpenProjectDate();
	    pluginContext.getIdentityService().addObserver(loginObserver);
		try {
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
			LogManager.INSTANCE.logError(e, "Couldn't start plugin.");
		}
	}
	
	protected void startTracker() {
		pluginContext.getTrackingService().startTracking();
	}

	private void authenticate() {

		AuthenticationBrowserView view = new AuthenticationBrowserView();
		view.showLogin(false);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		
		if (pluginContext != null) {
			pluginContext.getTrackingService().stopTracking(false);
		}
		
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
