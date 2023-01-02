/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.api;

import java.io.IOException;
import java.util.UUID;

import com.codealike.client.eclipse.internal.dto.ActivityInfo;
import com.codealike.client.eclipse.internal.dto.PluginSettingsInfo;
import com.codealike.client.eclipse.internal.dto.ProfileInfo;
import com.codealike.client.eclipse.internal.dto.SolutionContextInfo;
import com.codealike.client.eclipse.internal.dto.UserConfigurationInfo;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

/**
 * Api class to communicate with Codealike server.
 *
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class ApiClient {
	// Headers for API authentication
	private static final String X_EAUTH_CLIENT_HEADER = "X-Eauth-Client";
	private static final String X_EAUTH_TOKEN_HEADER = "X-Api-Token";
	private static final String X_EAUTH_IDENTITY_HEADER = "X-Api-Identity";

	// Number of API retries
	public static final int MAX_RETRIES = 5;

	private String identity;
	private String token;
	private ObjectMapper mapper;

	/**
	 * Create a new API client.
	 *
	 * @param identity the user identity
	 * @param token    the user token
	 * @return the created APIClient instance
	 */
	public static ApiClient tryCreateNew(String identity, String token) {
		return new ApiClient(identity, token);
	}

	/**
	 * Create a new API client.
	 *
	 * @return the created APIClient instance
	 */
	public static ApiClient tryCreateNew() {
		return new ApiClient();
	}

	/**
	 * Dispose unirest client.
	 */
	public static void Dispose() {
		try {
			Unirest.shutdown();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Create a new API client.
	 *
	 * @return the created APIClient instance
	 * @throws KeyManagementException if any error with token occurs
	 */
	protected ApiClient() {
		this.identity = "";
		this.token = "";

		mapper = new ObjectMapper();
		mapper.registerModule(new JodaModule());
	}

	/**
	 * API Client constructor.
	 *
	 * @param identity the user identity
	 * @param token    the user token
	 */
	protected ApiClient(String identity, String token) {
		this();

		if (identity != null && token != null) {
			this.identity = identity;
			this.token = token;
		}
	}

	/**
	 * Get the Eclipse plugin settings.
	 * 
	 * @return the {@link ApiResponse} instance with {@link PluginSettingsInfo}
	 *         information
	 */
	public static ApiResponse<PluginSettingsInfo> getPluginSettings() {
		ObjectMapper mapper = new ObjectMapper();

		try {
			HttpResponse<String> response = Unirest.get("https://codealike.com/api/v2/public/PluginsConfiguration")
					.header("accept", "application/json").header("Content-Type", "application/json").asString();

			if (response.getStatus() == 200) {
				String cleanJson = response.getBody().substring(1, response.getBody().length() - 1).replace("\\", "");
				PluginSettingsInfo responseObject = mapper.readValue(cleanJson, PluginSettingsInfo.class);
				if (responseObject != null) {
					return new ApiResponse<PluginSettingsInfo>(response.getStatus(), response.getStatusText(),
							responseObject);
				} else {
					return new ApiResponse<PluginSettingsInfo>(ApiResponse.Status.ClientError,
							"Problem parsing data from the server.");
				}
			} else {
				return new ApiResponse<PluginSettingsInfo>(response.getStatus(), response.getStatusText());
			}
		} catch (Exception e) {
			return new ApiResponse<PluginSettingsInfo>(ApiResponse.Status.ConnectionProblems);
		}
	}

	/**
	 * Get solution context information.
	 *
	 * @param projectId the current project id being tracker
	 * @return the {@link ApiResponse} instance with {@link SolutionContextInfo}
	 *         information
	 */
	public ApiResponse<SolutionContextInfo> getSolutionContext(UUID projectId) {
		return doGet(String.format("solution/%s", projectId.toString()), SolutionContextInfo.class);
	}

	/**
	 * Get project information.
	 *
	 * @param username the profile username
	 * @return the {@link ApiResponse} instance with {@link ProfileInfo} information
	 */
	public ApiResponse<ProfileInfo> getProfile(String username) {
		return doGet(String.format("account/%s/profile", username), ProfileInfo.class);
	}

	/**
	 * Get user configuration information.
	 *
	 * @param username the profile username
	 * @return the {@link ApiResponse} instance with {@link UserConfigurationInfo}
	 *         information
	 */
	public ApiResponse<UserConfigurationInfo> getUserConfiguration(String username) {
		return doGet(String.format("account/%s/config", username), UserConfigurationInfo.class);
	}

	/**
	 * Register project being tracked.
	 *
	 * @param projectId the project identifier to track
	 * @param name      the project name
	 * @return the {@link ApiResponse} instance
	 */
	public ApiResponse<String> registerProjectContext(UUID projectId, String name) {
		try {
			SolutionContextInfo solutionContext = new SolutionContextInfo(projectId, name);
			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String solutionAsJson = writer.writeValueAsString(solutionContext);

			return doPost("solution", String.class, solutionAsJson);
		} catch (JsonProcessingException e) {
			return new ApiResponse<String>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s", e.getMessage()));
		}
	}

	/**
	 * Post project activity information.
	 *
	 * @param info the activity information object
	 * @return the {@link ApiResponse} instance
	 */
	public ApiResponse<String> postActivityInfo(ActivityInfo info) {
		try {
			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String activityInfoAsJson = writer.writeValueAsString(info);

			return doPost("activity", String.class, activityInfoAsJson);
		} catch (JsonProcessingException e) {
			return new ApiResponse<String>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s", e.getMessage()));
		}
	}

	/**
	 * Do an account authentication using token.
	 *
	 * @return the {@link ApiResponse} instance
	 */
	public ApiResponse<String> tokenAuthenticate() {
		return doGet(String.format("account/%s/authorized", identity), String.class);
	}

	/**
	 * Private method to do an API GET.
	 */
	@SuppressWarnings("unchecked")
	private <T> ApiResponse<T> doGet(String route, Class<T> type) {
		try {
			HttpResponse<String> response = null;
			try {
				response = Unirest.get("https://codealike.com/api/v2/{route}").header("accept", "application/json")
						.header("Content-Type", "application/json").header(X_EAUTH_IDENTITY_HEADER, this.identity)
						.header(X_EAUTH_TOKEN_HEADER, this.token).header(X_EAUTH_CLIENT_HEADER, "eclipse")
						.routeParam("route", route).asString();
			} catch (Exception e) {
				return new ApiResponse<T>(ApiResponse.Status.ConnectionProblems);
			}

			if (response.getStatus() == 200) {
				T responseObject = null;
				if (type != String.class) {
					responseObject = mapper.readValue(response.getBody(), type);
				} else {
					responseObject = (T) "OK";
				}

				if (responseObject != null) {
					return new ApiResponse<T>(response.getStatus(), response.getStatusText(), responseObject);
				} else {
					return new ApiResponse<T>(ApiResponse.Status.ClientError, "Problem parsing data from the server.");
				}

			} else {
				return new ApiResponse<T>(response.getStatus(), response.getStatusText());
			}

		} catch (Exception e) {
			return new ApiResponse<T>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s", e.getMessage()));
		}
	}

	/**
	 * Private method to do an API POST.
	 */
	private <T> ApiResponse<T> doPost(String route, Class<T> type, String payload) {
		HttpResponse<String> response = null;
		try {
			response = Unirest.post("https://codealike.com/api/v2/{route}").header("accept", "application/json")
					.header("Content-Type", "application/json").header(X_EAUTH_IDENTITY_HEADER, this.identity)
					.header(X_EAUTH_TOKEN_HEADER, this.token).header(X_EAUTH_CLIENT_HEADER, "eclipse")
					.routeParam("route", route).body(payload).asString();

			return new ApiResponse<T>(response.getStatus(), response.getStatusText());
		} catch (Exception e) {
			return new ApiResponse<T>(ApiResponse.Status.ConnectionProblems);
		}
	}
}
