/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.tracking;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.Period;

import com.codealike.client.eclipse.api.ApiClient;
import com.codealike.client.eclipse.api.ApiResponse;
import com.codealike.client.eclipse.api.ApiResponse.Status;
import com.codealike.client.eclipse.internal.dto.ActivityInfo;
import com.codealike.client.eclipse.internal.dto.ActivityType;
import com.codealike.client.eclipse.internal.model.ActivityEvent;
import com.codealike.client.eclipse.internal.model.ActivityState;
import com.codealike.client.eclipse.internal.model.NullActivityState;
import com.codealike.client.eclipse.internal.model.StructuralCodeContext;
import com.codealike.client.eclipse.internal.processing.ActivityInfoProcessor;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.TrackingConsole;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Activity recorder class.
 *
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class ActivitiesRecorder {

	private TreeMap<DateTime, List<ActivityState>> states;
	private Map<UUID, List<ActivityEvent>> events;
	private ActivityEvent lastEvent;
	private DateTime lastStateDate;
	private PluginContext context;

	public ActivitiesRecorder(PluginContext context) {
		this.states = new TreeMap<DateTime, List<ActivityState>>(DateTimeComparator.getInstance());
		this.events = new HashMap<UUID, List<ActivityEvent>>();
		this.context = context;
	}

	public synchronized ActivityState recordState(ActivityState state) {
		ActivityState lastStateOfAllStates = null;
		List<ActivityState> lastStates = null;
		DateTime currentDate = state.getCreationTime();

		if (lastStateDate != null && !lastStateDate.equals(currentDate)) {
			lastStates = states.get(lastStateDate);
		}

		if (states.get(currentDate) == null) {
			states.put(currentDate, new LinkedList<ActivityState>());
			lastStateDate = currentDate;
		}
		states.get(currentDate).add(state);

		if (lastStates != null && !lastStates.isEmpty()) {
			lastStateOfAllStates = lastStates.get(lastStates.size() - 1);

			for (ActivityState lastState : lastStates) {
				if (lastState != null && lastState.getDuration().equals(Period.ZERO)) {
					lastState.setDuration(new Period(lastState.getCreationTime(), currentDate));

					if (!(lastState instanceof NullActivityState)) {
						TrackingConsole.getInstance().trackState(lastState);
					}
				}
			}
		}

		// If the current event cannot span multiple states then we set its duration to
		// finish now.
		if (lastStateDate != null && lastEvent != null && !lastEvent.canSpan()) {
			// Duration = State.EndTime - Event.StartTime;
			lastEvent.setDuration(new Period(lastEvent.getCreationTime(), currentDate));

			if (lastEvent.getType() != ActivityType.Event) {
				recordEvent(new ActivityEvent(lastEvent.getProjectId(), ActivityType.Event,
						StructuralCodeContext.createNullContext()));
			}
		}

		return lastStateOfAllStates;
	}

	private ActivityEvent getLastEvent(UUID projectId) {
		ActivityEvent lastEvent = null;
		List<ActivityEvent> projectEvents = getProjectEvents(projectId);

		if (projectEvents != null && !projectEvents.isEmpty()) {
			lastEvent = projectEvents.get(projectEvents.size() - 1);
		}

		return lastEvent;
	}

	public synchronized ActivityState recordStates(List<? extends ActivityState> states) {
		ActivityState lastState = null;
		for (ActivityState activityState : states) {
			lastState = recordState(activityState);
		}
		return lastState;
	}

	private List<ActivityEvent> getProjectEvents(UUID projectId) {
		if (events.get(projectId) == null) {
			events.put(projectId, new LinkedList<ActivityEvent>());
		}
		return events.get(projectId);
	}

	public synchronized ActivityEvent recordEvent(ActivityEvent event) {
		ActivityEvent lastEventForThisProject = null;
		List<ActivityEvent> projectEvents = getProjectEvents(event.getProjectId());

		lastEventForThisProject = getLastEvent(event.getProjectId());

		projectEvents.add(event);

		TrackingConsole.getInstance().trackEvent(event);

		lastEvent = event;

		return lastEventForThisProject;
	}

	public FlushResult flush(String username, String token) throws UnknownHostException {
		TreeMap<DateTime, List<ActivityState>> statesToSend = null;
		TreeMap<DateTime, List<ActivityEvent>> eventsToSend = null;

		ActivityState lastState;
		synchronized (this) {
			lastState = this.recordStates(ActivityState.createNullState());

			statesToSend = this.states;
			eventsToSend = flattenEvents(this.events);

			this.states = new TreeMap<DateTime, List<ActivityState>>(DateTimeComparator.getInstance());
			this.events = new HashMap<UUID, List<ActivityEvent>>();
		}

		if (lastState != null && !(lastState instanceof NullActivityState)) {
			this.recordState(lastState.recreate());
		}

		ActivityInfoProcessor processor = new ActivityInfoProcessor(statesToSend, eventsToSend);

		if (!processor.isValid()) {
			return FlushResult.Skip;
		}

		List<ActivityInfo> activityInfoList = processor.getSerializableEntities(context.getMachineName(),
				context.getInstanceValue(), context.getIdeName(), context.getPluginVersion());

		if (!processor.isActivityValid(activityInfoList)) {
			return FlushResult.Skip;
		}
		FlushResult result = FlushResult.Succeded;
		for (ActivityInfo info : activityInfoList) {
			if (!info.isValid()) {
				continue;
			}
			File cacheFolder = context.getConfiguration().getCachePath();
			if (cacheFolder == null) {
				LogManager.INSTANCE.logError("Could not access cache folder. It might not be created.");
				continue;
			}
			FlushResult intermediateResult = trySendEntries(info, username, token);
			if (intermediateResult == FlushResult.Succeded) {
				for (final File fileEntry : cacheFolder.listFiles()) {
					trySendEntriesOnFile(fileEntry.getName(), username, token);
				}

				if (context.getConfiguration().getTrackSent()) {
					// String filename = String.format("%s\\%s%s", cacheFolder.getAbsolutePath(),
					// info.getBatchId(), ".sent");
					File historyFile = context.getConfiguration().getHistoryFile();

					FileOutputStream stream = null;
					try {
						stream = new FileOutputStream(historyFile);
						ObjectWriter writer = context.getJsonWriter();
						String json = writer.writeValueAsString(info);
						stream.write(json.getBytes(Charset.forName("UTF-8")));
					} catch (Exception e) {
						LogManager.INSTANCE.logError(e, "There was a problem trying to store activity data locally.");
					} finally {
						if (stream != null) {
							try {
								stream.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			} else {
				// String filename = String.format("%s\\%s%s", cacheFolder.getAbsolutePath(),
				// info.getBatchId(), activityLogExtension);
				File cacheFile = context.getConfiguration().getCacheFile();

				FileOutputStream stream = null;
				try {
					stream = new FileOutputStream(cacheFile);
					ObjectWriter writer = context.getJsonWriter();
					String json = writer.writeValueAsString(info);
					stream.write(json.getBytes(Charset.forName("UTF-8")));
				} catch (Exception e) {
					LogManager.INSTANCE.logError(e, "There was a problem trying to store activity data locally.");
				} finally {
					if (stream != null) {
						try {
							stream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}

			if (intermediateResult != FlushResult.Succeded && intermediateResult != FlushResult.Skip) {
				result = intermediateResult;
			}
		}
		return result;
	}

	private void trySendEntriesOnFile(String fileName, String username, String token) {
		try {
			FlushResult result = FlushResult.Skip;
			File fileEntry = new File(context.getConfiguration().getCachePath(), fileName);
			try {
				ActivityInfo activityInfo = context.getJsonMapper().readValue(new FileInputStream(fileEntry),
						ActivityInfo.class);
				ApiClient client = ApiClient.tryCreateNew(username, token);

				ApiResponse<String> response = client.postActivityInfo(activityInfo);
				if (response.success()) {
					result = FlushResult.Succeded;
				}

				if (response.conflict() || response.getStatus() == Status.BadRequest || response.error()
						|| response.notFound()) {
					result = FlushResult.Report;
				}
			} catch (IOException e) {
				LogManager.INSTANCE.logError(e,
						"There was a problem trying to send offline activity data to the server.");
				result = FlushResult.Report;
			}

			finally {
				switch (result) {
				case Succeded: {
					fileEntry.renameTo(new File(context.getConfiguration().getHistoryPath(), fileName));
				}
				case Report: {
					fileEntry.renameTo(new File(context.getConfiguration().getHistoryPath(), fileName + ".error"));
					break;
				}
				case Skip: {
					break;
				}
				case Offline: {
					break;
				}
				default:
					break;
				}
			}
		} catch (Throwable t) {
			LogManager.INSTANCE.logError(t, "There was a problem trying to send offline activity data to the server.");
		}
	}

	private FlushResult trySendEntries(ActivityInfo info, String username, String token) {
		try {
			ApiClient client = ApiClient.tryCreateNew(username, token);

			ApiResponse<String> response = client.postActivityInfo(info);
			if (!response.success()) {
				LogManager.INSTANCE.logWarn(String.format(
						"There was a problem trying to send activity data to the server (Status: %s). "
								+ "Data will be stored offline until it can be sent.",
						response.getStatus().toString()));
				if (response.conflict() || response.getStatus() == Status.BadRequest || response.error()
						|| response.notFound()) {
					return FlushResult.Report;
				} else
					return FlushResult.Offline;
			}
			return FlushResult.Succeded;
		} catch (Throwable t) {
			LogManager.INSTANCE.logError(t, "There was a problem trying to send activity data to the server.");
			return FlushResult.Report;
		}
	}

	private TreeMap<DateTime, List<ActivityEvent>> flattenEvents(Map<UUID, List<ActivityEvent>> events) {
		TreeMap<DateTime, List<ActivityEvent>> eventsAsMap = new TreeMap<DateTime, List<ActivityEvent>>(
				DateTimeComparator.getInstance());
		List<ActivityEvent> flatActivityEvents = new ArrayList<ActivityEvent>();

		for (UUID project : events.keySet()) {
			List<ActivityEvent> eventsForProject = events.get(project);
			flatActivityEvents.addAll(eventsForProject);
		}

		for (ActivityEvent event : flatActivityEvents) {
			DateTime date = event.getCreationTime();
			if (eventsAsMap.get(date) == null) {
				eventsAsMap.put(date, new LinkedList<ActivityEvent>());
			}
			eventsAsMap.get(date).add(event);
		}

		return eventsAsMap;
	}

	public enum FlushResult {
		Offline, Succeded, Report, Skip
	}
}
