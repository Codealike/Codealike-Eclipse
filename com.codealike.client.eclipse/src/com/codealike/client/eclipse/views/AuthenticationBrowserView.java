package com.codealike.client.eclipse.views;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.codealike.client.eclipse.api.ApiClient;
import com.codealike.client.eclipse.api.ApiResponse;
import com.codealike.client.eclipse.internal.services.IdentityService;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.URIUtils;

public class AuthenticationBrowserView {
	
	protected static final String DENIED_URI = "/Account/ExternalLoginDenied";
	protected static final String FAILED_URI = "/Account/ExternalLoginFailure";
	protected static final String UNAUTHORIZED_URI = "/Error/Authorization";
	protected static final Object SIGNING_IN_OR_OUT_URI = "/Account/DesktopExternalLoginOut";
	private Shell browserShell;
	private Browser browser;
	private PluginContext context;
	private LocationListener locationListener = new LocationListener() {
		
		@Override
		public void changing(LocationEvent event) {
			// nothing to do here
		}
		
		@Override
		public void changed(LocationEvent event) {
			IdentityService identityService = context.getIdentityService();
			if (event.location!=null) {
				adjustBrowserSize(event.location);
			}
			try {
				URI uri = new URI(event.location);
				String absolutePath = uri.getPath();
				List<NameValuePair> keys = URLEncodedUtils.parse(new URI(event.location), "UTF-8");
				Map<String, String> keysAsMap = URIUtils.convertQueryParameters(keys);
				if (keysAsMap.containsKey("token") && keysAsMap.containsKey("username")) {
					String identity = keysAsMap.get("username");
					String token = keysAsMap.get("token");
					boolean rememberMe = Boolean.parseBoolean(keysAsMap.get("rememberme"));
					try {
						if (!identityService.isAuthenticated()) {
							identityService.login(identity, token, true, rememberMe);
						}
					}
					finally {
						close();
					}
				}
				else if (absolutePath.equals(DENIED_URI)) {
					//nothing for the moment
				}
				else if (absolutePath.equals(UNAUTHORIZED_URI)) {
					LogManager.INSTANCE.logError("Client was not authorized by the server while trying to Sign in or Register.");
					if (identityService.isAuthenticated()) {
						identityService.logOff();
						close();
					}
					else {
						close();
					}
				}
				else if (absolutePath.equals(FAILED_URI)) {
					LogManager.INSTANCE.logError("Server experienced error while trying to Sign in or Register.");
					showUnexpectedError();
					close();
				}
				else if (absolutePath.equals(SIGNING_IN_OR_OUT_URI)) {
					close();
					identityService.logOff();
				}
					
			} catch (URISyntaxException e) {
			}
		}
	};
	
	private void showUnexpectedError() {
		String title = "Houston... I have the feeling we messed up the specs.";
		String text = "If the problem continues, radio us for assistance.";
		ErrorDialogView dialog = new ErrorDialogView(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title, text, "Roger that.", "images/LunarCat.png");
		dialog.open();
	}
	
	public AuthenticationBrowserView() {
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
		public void run() {
			context = PluginContext.getInstance();
			browserShell = new Shell (SWT.SHELL_TRIM & (~SWT.RESIZE));
			browserShell.setSize(657, 580);
			browserShell.setLayout(new FillLayout());
			browserShell.setText("Codealike");
			try {
				browser = new Browser(browserShell, SWT.NONE);
				browser.addLocationListener(locationListener);
			} catch (SWTError e) {
				LogManager.INSTANCE.logError(e, "Could not instantiate Browser.");
				return;
			}
		}});
	}
	
	protected void close() {
		browserShell.dispose();
	}

	protected void adjustBrowserSize(String location) {
		 //Defaults
        int width = 657;
        int height = 580;

        if (location.contains("linkedin.com"))
        {
        	height = 700;
        }
        else if (location.contains("github.com"))
        {
        	height = 750;
        	width = 1050;
        }
        else if (location.contains("twitter.com"))
        {
        	height = 800;
            width= 1050;
        }
        else if (location.contains("facebook.com"))
        {
        	height = 750;
            width = 1050;
        }
        this.browserShell.setSize(width, height);
        this.browserShell.layout();
	}

	public void showLogin(boolean showError) {
		show(context.getProperty("codealike.server.url")+"/Account/LoginDesktop", showError);
	}
	
	public void showRegister(boolean showError) {
		show(context.getProperty("codealike.server.url")+"/Account/RegisterDesktop", showError);
	}
	
	public void showLogOff(boolean showError) {
		show(context.getProperty("codealike.server.url")+"/Account/LogOffDesktop", showError);
	}
	
	public void show(String url, boolean showError) {
		/*try {
			ApiClient client = ApiClient.tryCreateNew();
			ApiResponse<Void> response = client.health();
			if (response.connectionTimeout() || response.error()) {
				if (showError) {
					LogManager.INSTANCE.logError(String.format("Couldn't access remote desktop (Status code=%s)", response.getStatus()));
					showUnexpectedError();
				}
				return;
			}
			
		}
		catch(KeyManagementException e) {
			LogManager.INSTANCE.logError(e, "Could not access remote server. There was a problem with SSL configuration.");
			return;
		}
		
	    if (!context.checkVersion()) {
	    	return;
	    }
		
		if (browserShell != null) {
			browserShell.open();
			browser.setUrl(url);
		}*/
	}
	
	

}
