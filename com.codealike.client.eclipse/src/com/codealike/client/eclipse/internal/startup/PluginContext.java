package com.codealike.client.eclipse.internal.startup;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.UUID;

import org.eclipse.core.internal.resources.ProjectPreferences;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import com.codealike.client.eclipse.api.ApiClient;
import com.codealike.client.eclipse.api.ApiResponse;
import com.codealike.client.eclipse.internal.dto.PluginSettingsInfo;
import com.codealike.client.eclipse.internal.dto.SolutionContextInfo;
import com.codealike.client.eclipse.internal.dto.Version;
import com.codealike.client.eclipse.internal.model.ProjectSettings;
import com.codealike.client.eclipse.internal.serialization.JodaPeriodModule;
import com.codealike.client.eclipse.internal.services.IdentityService;
import com.codealike.client.eclipse.internal.services.TrackingService;
import com.codealike.client.eclipse.internal.tracking.code.ContextCreator;
import com.codealike.client.eclipse.internal.utils.Configuration;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@SuppressWarnings("restriction")
public class PluginContext {

	public static final String VERSION = "0.4.5";
	private static final String PLUGIN_PREFERENCES_QUALIFIER = "com.codealike.client.eclipse";
	private static PluginContext _instance;
	
	private String ideName;
	private Version protocolVersion;
	private ObjectWriter jsonWriter;
	private ObjectMapper jsonMapper;
	private ContextCreator contextCreator;

	private DateTimeFormatter dateTimeFormatter;
	private DateTimeFormatter dateTimeParser;
	private IdentityService identityService;
	private TrackingService trackingService;
	private String instanceValue;
	
	private String machineName;

	private Configuration configuration;
	
	public static final UUID UNASSIGNED_PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000001");
	
	public static PluginContext getInstance() {
			if (_instance == null)
			{
				_instance = new PluginContext();
			}
		return _instance;
	}
	
	public PluginContext() {
		DateTimeZone.setDefault(DateTimeZone.UTC);

		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JodaPeriodModule());
		mapper.setSerializationInclusion(Include.NON_NULL);
		this.jsonWriter = mapper.writer().withDefaultPrettyPrinter();
		this.jsonMapper = mapper;
		this.contextCreator = new ContextCreator();
		this.dateTimeParser = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
		this.dateTimeFormatter = new DateTimeFormatterBuilder().appendYear(4, 4).appendLiteral("-").
				appendMonthOfYear(2).appendLiteral("-").appendDayOfMonth(2).
				appendLiteral("T").appendHourOfDay(2).appendLiteral(":").
				appendMinuteOfHour(2).appendLiteral(":").appendSecondOfMinute(2).
				appendLiteral(".").appendMillisOfSecond(3).appendLiteral("Z").toFormatter();
		this.identityService = IdentityService.getInstance();
		this.instanceValue = String.valueOf(new Random(DateTime.now().getMillis()).nextInt(Integer.MAX_VALUE) + 1);
		this.protocolVersion = new Version(0, 9);
		this.ideName = "eclipse";
		this.machineName = findLocalHostNameOr("unknown");

		// initialize configuration with required parameters
		this.configuration = new Configuration(this.ideName, VERSION, this.instanceValue);
		this.configuration.loadGlobalSettings();

		// try to load plugin settings from server
		ApiResponse<PluginSettingsInfo> pluginSettings = ApiClient.getPluginSettings();
		if (pluginSettings.success()) {
			this.configuration.loadPluginSettings(pluginSettings.getObject());
		}
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

	public String getIdeName() {
		return this.ideName;
	}

	public String getPluginVersion() {
		return VERSION;
	}

	public String getMachineName() {
		return machineName;
	}
	
	public String getHomeFolder() {
		String localFolder=null;
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			localFolder = System.getenv("APPDATA");
		}
		else {
			localFolder = System.getProperty("user.home");
		}
		if (localFolder == null) {
			localFolder = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
		}
		return localFolder+File.separator;
	}

	public void initializeContext() throws IOException {
		this.trackingService = TrackingService.getInstance();
	}

	private UUID tryGetLegacySolutionIdV1(IProject project) {
		UUID solutionId = null;

		try {
			String solutionIdString = null;
			ProjectPreferences projectNode = getProjectPreferences(project);
			if (projectNode != null) {
				//if projectId is not created yet, try to create a unique new one and register it.
				solutionIdString = projectNode.get("solutionId", null);
				if (solutionIdString == null) {
					solutionId = tryCreateUniqueId();
					if (!registerProjectContext(solutionId, project.getName()) ) {
						return null;
					}
				}
				else {
					solutionId = UUID.fromString(solutionIdString);
				}
			}
		} catch (Exception e) {
			String projectName = project != null ? project.getName() : "";
        	LogManager.INSTANCE.logError(e, "Could not create UUID for project "+projectName);
		}

		return solutionId;
	}
	
	public UUID getOrCreateUUID(IProject project) {
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		UUID solutionId = null;
		
		// try first to load codealike.json file from project folder
		ProjectSettings projectSettings = configuration.loadProjectSettings(project.getLocation().toString());
		
		if (projectSettings.getProjectId() == null) {
			// if configuration was not found in the expected place
			// let's try to load configuration from older plugin versions
			solutionId = tryGetLegacySolutionIdV1(project);
			
			if (solutionId != null) {
				// if solution id was found by other method than
				// loading project settings file from project folder
				// we have to save a new project settings with
				// generated information
				projectSettings.setProjectId(solutionId);
				projectSettings.setProjectName(project.getName());

				// and save the file for future uses
				configuration.saveProjectSettings(project.getLocation().toString(), projectSettings);
			}
			else {
				// if we reached this branch
				// it means not only no configuration was found
				// but also we were not able to register a new
				// configuration in server.
				// log was saved by internal method
				// nothing else to do here
			}
		}
		
		return projectSettings.getProjectId();
	}

	private ProjectPreferences getProjectPreferences(IProject project) {
		ProjectScope projectScope = new ProjectScope(project);
		//Get user preferences file
		ProjectPreferences projectNode = (ProjectPreferences) projectScope.getNode(PLUGIN_PREFERENCES_QUALIFIER);
		return projectNode;
	}
	
	private String findLocalHostNameOr(String defaultName) {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) { //see: http://stackoverflow.com/a/40702767/1117552
			return defaultName;
		}
	}
	
	private UUID tryCreateUniqueId() {
		UUID solutionId = UUID.randomUUID();
		ApiClient client = ApiClient.tryCreateNew(this.identityService.getIdentity(), this.identityService.getToken());
		ApiResponse<SolutionContextInfo> response = client.getSolutionContext(solutionId);
		if (response.connectionTimeout()) {
			LogManager.INSTANCE.logInfo("Communication problems running in offline mode.");
			return solutionId;
		}
		int numberOfRetries = 0;
		while (response.conflict() || (response.error() && numberOfRetries < ApiClient.MAX_RETRIES)) {
			solutionId = UUID.randomUUID();
			response = client.getSolutionContext(solutionId);
		}
		
		return solutionId;
	}
	
	public boolean registerProjectContext(UUID solutionId, String projectName) throws Exception {
		ApiClient client = ApiClient.tryCreateNew(this.identityService.getIdentity(), this.identityService.getToken());

		ApiResponse<SolutionContextInfo> solutionInfoResponse = client.getSolutionContext(solutionId);
		if (solutionInfoResponse.notFound()) {
			ApiResponse<String> response = client.registerProjectContext(solutionId, projectName);
			if (!response.success()) {
				LogManager.INSTANCE.logError("Problem registering solution.");
			}
			else {
				return true;
			}
		}
		else if (solutionInfoResponse.success()) {
			return true;
		}
		else if (solutionInfoResponse.connectionTimeout()) {
			LogManager.INSTANCE.logInfo("Communication problems running in offline mode.");
		}
		return false;
	}
	
	public ObjectWriter getJsonWriter() {
		return this.jsonWriter;
	}
	
	public ObjectMapper getJsonMapper() {
		return this.jsonMapper;
	}

	public ContextCreator getContextCreator() {
		return this.contextCreator;
	}

	public DateTimeFormatter getDateTimeFormatter() {
		return this.dateTimeFormatter;
	}

	public DateTimeFormatter getDateTimeParser() {
		return this.dateTimeParser;
	}

	public IdentityService getIdentityService() {
		return identityService;
	}

	public boolean isAuthenticated() {
		return this.identityService.isAuthenticated();
	}

	public TrackingService getTrackingService() {
		return trackingService;
	}

	public String getInstanceValue() {
		return instanceValue;
	}

	public Version getProtocolVersion() {
		return protocolVersion;
	}
}
