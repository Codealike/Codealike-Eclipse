package com.codealike.client.eclipse.internal.services;

import java.util.Observable;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.PlatformUI;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.codealike.client.eclipse.internal.model.TrackedProjectManager;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.tracking.StateTracker;
import com.codealike.client.eclipse.internal.tracking.ActivitiesRecorder.FlushResult;
import com.codealike.client.eclipse.internal.tracking.workspace.WorkspaceChangesListener;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.TrackingConsole;
import com.codealike.client.eclipse.internal.utils.WorkbenchUtils;
import com.google.common.collect.BiMap;

public class TrackingService extends Observable {
	private static TrackingService _instance;
	
	private TrackedProjectManager trackedProjectManager;
	private ScheduledExecutorService flushExecutor = null;
	private StateTracker tracker;
	private boolean isTracking;
	private WorkspaceChangesListener changesListener;
	private DateTime startWorkspaceDate;
	private PluginContext context;
	
	public static TrackingService getInstance() {
		if (_instance == null) {
			_instance = new TrackingService();
		}
		if (_instance.context == null) {
			_instance.context = PluginContext.getInstance();
		}
		
		return _instance;
	}
	
	public TrackingService() {
		this.trackedProjectManager = new TrackedProjectManager();
		this.tracker = new StateTracker(PlatformUI.getWorkbench().getDisplay());
		this.changesListener = new WorkspaceChangesListener();
		this.isTracking = false;
	}
	
	public void startTracking() {
		this.tracker.startTracking();
		
		startFlushExecutor();
		
		IProject[] currentProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < currentProjects.length; i++) {
			IProject project = currentProjects[i];
			startTracking(project);
		};
		
		//We need to start tracking unassigned project for states like "debugging" which does not belong to any project.
		startTrackingUnassignedProject();

		ResourcesPlugin.getWorkspace().addResourceChangeListener(this.changesListener, IResourceChangeEvent.POST_CHANGE 
																	| IResourceChangeEvent.PRE_DELETE | IResourceChangeEvent.PRE_CLOSE);
		
		this.isTracking = true;
		setChanged();
		notifyObservers();
	}
	
	private void startFlushExecutor() {
		if (this.flushExecutor != null)
			return;
		
		this.flushExecutor = Executors.newScheduledThreadPool(1);
		Runnable flushPeriodicTask = new Runnable() {
			
			@Override
			public void run() {
				try {
					TrackingConsole.getInstance().trackMessage("Flush tracking information executed");
					flushTrackingInformation();
				}
				catch(Exception e) {
					TrackingConsole.getInstance().trackMessage("Flush tracking information error " + e.getMessage());
				}
			}
		};
		
		int flushInterval = this.context.getConfiguration().getFlushInterval();
		this.flushExecutor.scheduleAtFixedRate(flushPeriodicTask, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
	}
	
	private void flushTrackingInformation() {
		WorkbenchUtils.addMessageToStatusBar("Codealike is sending activities...");
		FlushResult result = tracker.flush(context.getIdentityService().getIdentity(), context.getIdentityService().getToken());
		switch (result) {
		case Succeded:
			WorkbenchUtils.addMessageToStatusBar("Codealike sent activities");
			break;
		case Skip:
			WorkbenchUtils.addMessageToStatusBar("No data to be sent");
			break;
		case Offline:
			WorkbenchUtils.addMessageToStatusBar("Codealike is working in offline mode");
		case Report:
			WorkbenchUtils.addMessageToStatusBar("Codealike is storing corrupted entries for further inspection");
		}
	}
	
	public void stopTracking(boolean propagate) {
		this.tracker.stopTracking();
		if (this.flushExecutor != null) {
			this.flushExecutor.shutdownNow();
			this.flushExecutor = null;
		}
		
		this.trackedProjectManager.stopTracking();
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this.changesListener);
		
		this.isTracking = false;
		if (propagate) {
			setChanged();
			notifyObservers();
		}
	}
	
	public void enableTracking() {
		if (context.isAuthenticated()) {
			startTracking();
			WorkbenchUtils.addMessageToStatusBar("Codealike is tracking your projects");

		}
	}
	
	public void disableTracking() {
		stopTracking(true);
		WorkbenchUtils.addMessageToStatusBar("Codealike is not tracking your projects");
	}
	
	public synchronized void startTracking(IProject project) {
		if (!project.isOpen()) {
			return;
		}
		if (isTracked(project)) {
			return;
		}
		UUID projectId = PluginContext.getInstance().getOrCreateUUID(project);
		if (projectId != null && trackedProjectManager.trackProject(project, projectId)) {
			tracker.startTrackingProject(project, projectId, this.startWorkspaceDate);
		}
		else {
			LogManager.INSTANCE.logWarn(String.format("Could not track project %s. "
					+ "If you have a duplicated UUID in any of your \"com.codealike.client.eclipse.prefs\" please delete one of those to generate a new UUID for"
					+ "that project", project.getName()));
		}
	}
	
	private void startTrackingUnassignedProject() {
		try {
			PluginContext.getInstance().registerProjectContext(PluginContext.UNASSIGNED_PROJECT, "Unassigned");
		} catch (Exception e) {
			LogManager.INSTANCE.logWarn("Could not track unassigned project.");
		}
	}
	
	public boolean isTracked(IProject project) {
		return this.trackedProjectManager.isTracked(project);
	}
	
	public void stopTracking(IProject project) {
		this.trackedProjectManager.stopTrackingProject(project);
	}

	public TrackedProjectManager getTrackedProjectManager() {
		return this.trackedProjectManager;
	}
	
	public UUID getUUID(IProject project) {
		return this.trackedProjectManager.getTrackedProjectId(project);
	}
	
	public IProject getProject(UUID projectId) {
		return this.trackedProjectManager.getTrackedProject(projectId);
	}

	public BiMap<IProject, UUID> getTrackedProjects() {
		return this.trackedProjectManager.getTrackedProjects();
	}
	
	public void setBeforeOpenProjectDate() {
		this.startWorkspaceDate = DateTime.now();
	}
	
	public boolean isTracking() {
		return this.isTracking;
	}

	public void flushRecorder(final String identity, final String token) {
		if (this.isTracking) {
			this.flushExecutor.execute( new Runnable() {
				
				@Override
				public void run() {
					tracker.flush(identity, token);
				}
			});
		}
	}
	
}
