package com.codealike.client.eclipse.api;

import java.io.IOException;
import java.net.ConnectException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

import com.codealike.client.eclipse.internal.dto.ActivityInfo;
import com.codealike.client.eclipse.internal.dto.HealthInfo;
import com.codealike.client.eclipse.internal.dto.PluginSettingsInfo;
import com.codealike.client.eclipse.internal.dto.ProfileInfo;
import com.codealike.client.eclipse.internal.dto.SolutionContextInfo;
import com.codealike.client.eclipse.internal.dto.UserConfigurationInfo;
import com.codealike.client.eclipse.internal.dto.Version;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequest;

public class ApiClient<Y> {

	private static final String X_EAUTH_CLIENT_HEADER = "X-Eauth-Client";
	private static final String X_EAUTH_TOKEN_HEADER = "X-Api-Token";
	public static final String X_EAUTH_IDENTITY_HEADER = "X-Api-Identity";
	public static final int MAX_RETRIES = 5;

	private String identity;
	private String token;
	private ObjectMapper mapper;

	public static ApiClient tryCreateNew(String identity, String token) {
		return new ApiClient(identity, token);
	}

	public static ApiClient tryCreateNew() {
		return new ApiClient();
	}

	protected ApiClient() {
		this.identity = "";
		this.token = "";
		
		mapper = new ObjectMapper();
		mapper.registerModule(new JodaModule());
	}

	protected ApiClient(String identity, String token) {
		this();
		
		if (identity != null && token != null) {
			this.identity = identity;
			this.token = token;
		}
	}

	/*
	public ApiResponse<Void> health() {
		try {
			WebTarget target = apiTarget.path("health");

			Invocation.Builder invocationBuilder = target
					.request(MediaType.APPLICATION_JSON);
			Response response = invocationBuilder.get();
			return new ApiResponse<Void>(response.getStatus(), response
					.getStatusInfo().getReasonPhrase());
		} catch (ProcessingException e) {
			if (e.getCause() != null
					&& e.getCause() instanceof ConnectException) {
				return new ApiResponse<Void>(ApiResponse.Status.ConnectionProblems);
			} else {
				return new ApiResponse<Void>(ApiResponse.Status.ClientError);
			}
		}
	}
	
	public ApiResponse<Void> logHealth(HealthInfo healthInfo) {
		try {
			WebTarget target = apiTarget.path("health");

			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String healthInfoLog = writer.writeValueAsString(healthInfo);
			
			Invocation.Builder invocationBuilder = target.request().accept(
					MediaType.APPLICATION_JSON);
			addHeaders(invocationBuilder);
			
			Response response = null;
			try {
				response = invocationBuilder.put(Entity.entity(healthInfoLog,
						MediaType.APPLICATION_JSON));
			} catch (Exception e) {
				return new ApiResponse<Void>(ApiResponse.Status.ConnectionProblems);
			}
			return new ApiResponse<Void>(response.getStatus(), response
					.getStatusInfo().getReasonPhrase());
		} catch (JsonProcessingException e) {
			return new ApiResponse<Void>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}
	}

	public ApiResponse<Version> version() {
		WebTarget target = apiTarget.path("version").queryParam("client", "Eclipse");
		return doGet(target, Version.class);
	}
	*/
	
	public static ApiResponse<PluginSettingsInfo> getPluginSettings() {
		ObjectMapper mapper = new ObjectMapper();

		try {
			HttpResponse<String> response = Unirest.get("https://codealike.com/api/v2/public/PluginsConfiguration")
					  .header("accept", "application/json")
					  .header("Content-Type", "application/json")
					  .asString();
			
			if (response.getStatus() == 200) {
				String cleanJson = response.getBody().substring(1, response.getBody().length()-1).replace("\\", "");
				PluginSettingsInfo responseObject = mapper.readValue(cleanJson, PluginSettingsInfo.class);
				if (responseObject != null) {
					return new ApiResponse<PluginSettingsInfo>(
							response.getStatus(), response.getStatusText(), responseObject);
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

	public ApiResponse<SolutionContextInfo> getSolutionContext(UUID projectId) {
		return doGet(String.format("solution/%s", projectId.toString()), SolutionContextInfo.class);
	}
	
	private <T> ApiResponse<T> doGet(String route, Class<T> type) {
		try {
			HttpResponse<String> response = null;
			try {
				response = Unirest.get("https://codealike.com/api/v2/{route}")
						  .header("accept", "application/json")
						  .header("Content-Type", "application/json")
						  .header(X_EAUTH_IDENTITY_HEADER, this.identity)
						  .header(X_EAUTH_TOKEN_HEADER, this.token)
						  .header(X_EAUTH_CLIENT_HEADER, "eclipse")
						  .routeParam("route", route)
						  .asString();
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
					return new ApiResponse<T>(
							response.getStatus(), response.getStatusText(), responseObject);
				} else {
					return new ApiResponse<T>(ApiResponse.Status.ClientError,
							"Problem parsing data from the server.");
				}

			} else {
				return new ApiResponse<T>(response.getStatus(), response.getStatusText());
			}

		} catch (Exception e) {
			return new ApiResponse<T>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}
	}
	
	private <T> ApiResponse<T> doPost(String route, Class<T> type, String payload)
	{
		try {
			HttpResponse<String> response = null;
			try {
				response = Unirest.post("https://codealike.com/api/v2/{route}")
						  .header("accept", "application/json")
						  .header("Content-Type", "application/json")
						  .header(X_EAUTH_IDENTITY_HEADER, this.identity)
						  .header(X_EAUTH_TOKEN_HEADER, this.token)
						  .header(X_EAUTH_CLIENT_HEADER, "eclipse")
						  .routeParam("route", route)
						  .body(payload)
						  .asString();
				
				return new ApiResponse<T>(response.getStatus(), response.getStatusText());
			} catch (Exception e) {
				return new ApiResponse<T>(ApiResponse.Status.ConnectionProblems);
			}
		} catch (Exception e) {
			return new ApiResponse<T>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}
	}
	
	public ApiResponse<ProfileInfo> getProfile(String username) {
		return doGet(String.format("account/%s/profile", username), ProfileInfo.class);
	}
	
	public ApiResponse<UserConfigurationInfo> getUserConfiguration(String username) {
		return doGet(String.format("account/%s/config", username), UserConfigurationInfo.class);
	}

	public ApiResponse<String> registerProjectContext(UUID projectId, String name) {
		try {
			SolutionContextInfo solutionContext = new SolutionContextInfo(projectId, name);
	
			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String solutionAsJson = writer.writeValueAsString(solutionContext);
			
			return doPost("solution", String.class, solutionAsJson);
		} catch (JsonProcessingException e) {
			return new ApiResponse<String>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}
		
			//WebTarget target = apiTarget.path("solution");

			//ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			//String solutionAsJson = writer.writeValueAsString(solutionContext);
			//Invocation.Builder invocationBuilder = target.request().accept(
			//		MediaType.APPLICATION_JSON);
			//addHeaders(invocationBuilder);

			/*Response response = null;
			try {
				response = invocationBuilder.post(Entity.entity(solutionAsJson,
						MediaType.APPLICATION_JSON));
			} catch (Exception e) {
				return new ApiResponse<Void>(ApiResponse.Status.ConnectionProblems);
			}
			return new ApiResponse<Void>(response.getStatus(), response
					.getStatusInfo().getReasonPhrase());
		} catch (JsonProcessingException e) {
			return new ApiResponse<Void>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}*/
	}

	public ApiResponse<String> postActivityInfo(ActivityInfo info) {
		try {
			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String activityInfoAsJson = writer.writeValueAsString(info);
			return doPost("activity", String.class, activityInfoAsJson);
		} catch (JsonProcessingException e) {
			return new ApiResponse<String>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}
		/*try {
			WebTarget target = apiTarget.path("activity");

			ObjectWriter writer = PluginContext.getInstance().getJsonWriter();
			String activityInfoAsJson = writer.writeValueAsString(info);
			Invocation.Builder invocationBuilder = target.request().accept(
					MediaType.APPLICATION_JSON);
			addHeaders(invocationBuilder);

			Response response = null;
			try {
				response = invocationBuilder.post(Entity.entity(
						activityInfoAsJson, MediaType.APPLICATION_JSON));
			} catch (Exception e) {
				return new ApiResponse<Void>(ApiResponse.Status.ConnectionProblems);
			}
			return new ApiResponse<Void>(response.getStatus(), response
					.getStatusInfo().getReasonPhrase());
		} catch (JsonProcessingException e) {
			return new ApiResponse<Void>(ApiResponse.Status.ClientError,
					String.format("Problem parsing data from the server. %s",
							e.getMessage()));
		}*/
	}

	public ApiResponse<String> tokenAuthenticate() {
		return doGet(String.format("account/%s/authorized", identity), String.class);
	}
	
	public static void Dispose() {
		try {
			Unirest.shutdown();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
