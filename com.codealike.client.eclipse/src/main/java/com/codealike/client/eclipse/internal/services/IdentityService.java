/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.services;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import com.codealike.client.eclipse.api.ApiClient;
import com.codealike.client.eclipse.api.ApiResponse;
import com.codealike.client.eclipse.internal.dto.ProfileInfo;
import com.codealike.client.eclipse.internal.dto.UserConfigurationInfo;
import com.codealike.client.eclipse.internal.model.Profile;
import com.codealike.client.eclipse.internal.model.TrackActivity;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.Configuration;
import com.codealike.client.eclipse.internal.utils.WorkbenchUtils;

/**
 * Identity service class.
 *
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class IdentityService extends BaseService {
	
	private static IdentityService _instance;
	private boolean isAuthenticated;
	private String identity;
	private String token;
	private Profile profile;
	private boolean credentialsStored;
	private TrackActivity trackActivities;
	
	public static IdentityService getInstance() {
		if (_instance == null) {
			_instance = new IdentityService();
		}
		
		return _instance;
	}
	
	public IdentityService() {
		this.identity = "";
		this.isAuthenticated = false;
		this.credentialsStored = false;
		this.token = "";
	}

	public boolean isAuthenticated() {
		return isAuthenticated;
	}

	public boolean login(String identity, String token, boolean storeCredentials, boolean rememberMe) {
		if (this.isAuthenticated) {
			WorkbenchUtils.addMessageToStatusBar("Codealike is connected.");
			
			publishEvent();
			return true;
		}

		WorkbenchUtils.addMessageToStatusBar("Codealike is connecting...");
		
		ApiClient apiClient = ApiClient.tryCreateNew(identity, token);
		ApiResponse<String> response = apiClient.tokenAuthenticate();
		
		if (response.success()) {
			WorkbenchUtils.addMessageToStatusBar("Codealike is connected.");
			
			this.identity = identity;
			this.token = token;
			if (storeCredentials) {
				if (rememberMe) {
					storeCredentials(identity, token);
				}
				else {
					removeStoredCredentials();
				}
			}
			
			ApiResponse<ProfileInfo> profileResponse = apiClient.getProfile(identity);
			if (profileResponse.success())
			{
				ProfileInfo profile = profileResponse.getObject();
				this.profile = new Profile(this.identity, profile.getFullName(), profile.getDisplayName(), 
							profile.getAddress(), profile.getState(), profile.getCountry(), profile.getAvatarUri(), profile.getEmail());
			}
			
			ApiResponse<UserConfigurationInfo> configResponse = apiClient.getUserConfiguration(identity);
			if (configResponse.success())
			{
				UserConfigurationInfo config = configResponse.getObject();
				this.trackActivities = config.getTrackActivities();
			}
			this.isAuthenticated = true;
			publishEvent();
			return true;
		}
		
		return false;
	}

	private void storeCredentials(String identity, String token) {
		// save user token to global configuration file
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		configuration.setUserToken(identity + "/" + token);
		configuration.saveCurrentGlobalSettings();
		
		// remove fallback ones also!
        ISecurePreferences secureStorage = SecurePreferencesFactory.getDefault();
        if (secureStorage.nodeExists("codealike")) {
        	ISecurePreferences node = secureStorage.node("codealike");
        	node.remove("identity");
        	node.remove("token");
        }
	}
	
	private void removeStoredCredentials() {
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		configuration.setUserToken(null);
		configuration.saveCurrentGlobalSettings();
		
		// remove fallback ones also!
        ISecurePreferences secureStorage = SecurePreferencesFactory.getDefault();
        if (secureStorage.nodeExists("codealike")) {
        	ISecurePreferences node = secureStorage.node("codealike");
        	node.remove("identity");
        	node.remove("token");
        }
        this.credentialsStored = false;
	}
	
	public boolean tryLoginWithStoredCredentials() {
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		String identity = "";
		String token = "";
		
		// if loaded configuration has no user token, try to fallback to previows store
		if (configuration.getUserToken() == null || configuration.getUserToken().isEmpty()) {
			// fallback information
	        ISecurePreferences secureStorage = SecurePreferencesFactory.getDefault();
	        if (secureStorage.nodeExists("codealike")) {
        		ISecurePreferences node = secureStorage.node("codealike");

        		try {
					identity = node.get("identity", "");
					token = node.get("token", "");
				} catch (StorageException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        		
        		// if information found by fallback mechanism
    			// lets save that configuration for future use
    			if (identity != "" && token != "") {
    				configuration.setUserToken(identity + "/" + token);
    				configuration.saveCurrentGlobalSettings();

    				// remove fallback ones also!
    	        	node.remove("identity");
    	        	node.remove("token");
    			}
	        }
		}
		else {
			String[] split = configuration.getUserToken().split("/");
			identity = split[0];
			token = split[1];
		}      
		
		if (identity != "" && token != "")
			return login(identity, token, false, false);
		
        return false;
	}

	public String getIdentity() {
		return identity;
	}

	public String getToken() {
		return token;
	}
	
	public Profile getProfile() {
		return profile;
	}
	
	public TrackActivity getTrackActivity() {
		return trackActivities;
	}

	public boolean isCredentialsStored() {
		return credentialsStored;
	}

	public void logOff() {
		WorkbenchUtils.addMessageToStatusBar("Codealike is disconnecting...");
		PluginContext.getInstance().getTrackingService().flushRecorder(this.identity, this.token);
		
		this.isAuthenticated = false;
		this.identity = null;
		this.token = null;
		removeStoredCredentials();
		
		publishEvent();
	}

}
