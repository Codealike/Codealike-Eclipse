/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.tracking;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.codealike.client.eclipse.internal.dto.ActivityType;
import com.codealike.client.eclipse.internal.model.ActivityEvent;
import com.codealike.client.eclipse.internal.model.ActivityState;
import com.codealike.client.eclipse.internal.model.BuildActivityEvent;
import com.codealike.client.eclipse.internal.model.CodeContext;
import com.codealike.client.eclipse.internal.model.IdleActivityState;
import com.codealike.client.eclipse.internal.model.NullActivityState;
import com.codealike.client.eclipse.internal.model.exception.NonExistingResourceException;
import com.codealike.client.eclipse.internal.services.TrackingService;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.tracking.ActivitiesRecorder.FlushResult;
import com.codealike.client.eclipse.internal.tracking.build.ResourceDeltaVisitor;
import com.codealike.client.eclipse.internal.tracking.code.ContextCreator;
import com.codealike.client.eclipse.internal.utils.EditorUtils;
import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.TrackingConsole;
import com.codealike.client.eclipse.internal.utils.WorkbenchUtils;

/**
 * Class to track state.
 *
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class StateTracker {

	private ActivitiesRecorder recorder;
	private ActivityState currentState;
	private ActivityState lastState;
	private final Display display;
	private Set<IDocument> registeredDocs;
	protected IResource currentCompilationUnit;
	private CodeContext lastCodeContext;
	private ActivityEvent lastEvent;
	private ContextCreator contextCreator;
	private ScheduledExecutorService idleDetectionExecutor;

	/**
	 * Listens to the build events. Since build is automatic by default, this will
	 * happen many times while working.
	 */
	private final IResourceChangeListener buildEventsListener = new IResourceChangeListener() {

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			switch (event.getType()) {
			case IResourceChangeEvent.PRE_BUILD: {

				try {
					TrackingService trackingService = PluginContext.getInstance().getTrackingService();

					ResourceDeltaVisitor deltaVisitor = new ResourceDeltaVisitor();
					event.getDelta().accept(deltaVisitor);
					for (IProject project : deltaVisitor.getAffectedProjects()) {
						if (!trackingService.isTracked(project)) {
							return;
						}
						UUID projectId = trackingService.getTrackedProjects().get(project);
						ActivityState buildState = ActivityState.createBuildState(projectId);
						recorder.recordState(buildState);

						CodeContext context = contextCreator.createCodeContext(project);
						ActivityEvent buildEvent = new BuildActivityEvent(projectId, ActivityType.BuildProject,
								context);
						recorder.recordEvent(buildEvent);

						currentState = buildState;
						lastEvent = buildEvent;
					}
				} catch (Exception e) {

				}

				break;
			}
			case IResourceChangeEvent.POST_BUILD: {

				try {
					TrackingService trackingService = PluginContext.getInstance().getTrackingService();

					ResourceDeltaVisitor deltaVisitor = new ResourceDeltaVisitor();
					event.getDelta().accept(deltaVisitor);
					for (IProject project : deltaVisitor.getAffectedProjects()) {
						if (!trackingService.isTracked(project)) {
							return;
						}

						List<IMarker> problems = EditorUtils.getCompilationErrors(project);
						ActivityEvent buildEvent = null;

						CodeContext context = contextCreator.createCodeContext(project);

						UUID projectId = trackingService.getTrackedProjects().get(project);
						if (problems != null && !problems.isEmpty()) {
							buildEvent = new BuildActivityEvent(projectId, ActivityType.BuildProjectFailed, context);
							recorder.recordEvent(buildEvent);
							buildEvent = new BuildActivityEvent(projectId, ActivityType.BuildSolutionFailed, context);
						} else {
							buildEvent = new BuildActivityEvent(projectId, ActivityType.BuildProjectSucceeded, context);
							recorder.recordEvent(buildEvent);
							buildEvent = new BuildActivityEvent(projectId, ActivityType.BuildSolutionSucceded, context);
						}

						recorder.recordEvent(buildEvent);
						lastEvent = buildEvent;
						ActivityState state = ActivityState.createNullState(projectId);
						recorder.recordState(state);
						currentState = state;
					}
				} catch (Exception e) {
					LogManager.INSTANCE.logError(e, "Problem recording post build event.");
				}

				break;
			}
			default: {
			}
				;
			}
		}
	};

	private final IDebugEventSetListener debugEventListener = new IDebugEventSetListener() {

		@Override
		public void handleDebugEvents(DebugEvent[] events) {
			for (DebugEvent event : events) {
				handleDebugEvent(event);
			}
		}
	};

	private synchronized void handleDebugEvent(DebugEvent event) {
		ActivityState state = ActivityState.NONE;
		UUID projectId = PluginContext.UNASSIGNED_PROJECT;

		if (event.getSource() instanceof IProcess) {
			if (event.getKind() == DebugEvent.TERMINATE && currentState.getType() == ActivityType.Debugging) {
				state = ActivityState.createNullState(projectId);
			}
		}
		if (event.getSource() instanceof IDebugTarget) {

			// Records the start time of this launch:
			if (event.getKind() == DebugEvent.CREATE && currentState.getType() != ActivityType.Debugging) {

				state = ActivityState.createDebugState(projectId);
			} else if (event.getKind() == DebugEvent.TERMINATE) {
				state = ActivityState.createNullState(projectId);
			}
		} else if (event.getSource() instanceof IThread) {
			if (event.getKind() == DebugEvent.SUSPEND && currentState.getType() != ActivityType.Debugging) {
				state = ActivityState.createDebugState(PluginContext.UNASSIGNED_PROJECT);
			} else if (event.getKind() == DebugEvent.RESUME) {
				state = ActivityState.createNullState(projectId);
			}
		}

		if (state != null && state != ActivityState.NONE) {
			currentState = state;
			recorder.recordState(state);
		}
	}

	private final IDocumentListener documentListener = new IDocumentListener() {

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// TODO: do we need to do something?
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			IEditorPart activeEditor = WorkbenchUtils.getActiveEditor();
			trackCodingEvents(activeEditor);
		}

	};

	private final ISelectionListener selectionListener = new ISelectionListener() {

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (part instanceof IEditorPart) {
				trackDocumentFocus(part);
			}
		}
	};

	private final IPartListener partsListener = new IPartListener() {

		@Override
		public void partActivated(final IWorkbenchPart part) {
			if (part instanceof IEditorPart) {
				trackDocumentFocus(part);
			}
		}

		@Override
		public void partBroughtToTop(IWorkbenchPart part) {
			// Do nothing.
		}

		@Override
		public void partClosed(IWorkbenchPart part) {
			if (part instanceof IEditorPart) {
				deregisterPart(part);
			}
		}

		@Override
		public void partDeactivated(IWorkbenchPart part) {
		}

		@Override
		public void partOpened(IWorkbenchPart part) {
			if (part instanceof IEditorPart) {
				registerPart(part);
			}
		}
	};

	private synchronized void trackDocumentFocus(final IWorkbenchPart part) {
		if (!(part instanceof IEditorPart)) {
			return;
		}

		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				try {
					TrackingService trackingService = PluginContext.getInstance().getTrackingService();
					IEditorPart editor = (IEditorPart) part;
					IProject activeProject = EditorUtils.getActiveProject(editor);

					if (activeProject == null || !trackingService.isTracked(activeProject)) {
						return;
					}

					UUID projectId = trackingService.getTrackedProjects().get(activeProject);

					if (currentState.getType() != ActivityType.Coding || currentState.getProjectId() != projectId) {

						if (currentState.getType() != ActivityType.Debugging) {
							currentState = ActivityState.createDesignState(projectId);
							recorder.recordState(currentState);
						}
					}

					IResource focusedResource = EditorUtils.getActiveResource(editor);
					if (focusedResource == null) {
						return;
					}
					trackNewSelection(editor, focusedResource, projectId);
				} catch (NonExistingResourceException e) {
					LogManager.INSTANCE.logError(e,
							"Problem recording document focus. Cannot find the active resource.");
				} catch (Exception e) {
					LogManager.INSTANCE.logError(e, "Problem recording document focus.");
				}
			}
		});
	}

	private void trackNewSelection(IEditorPart editor, IResource focusedResource, UUID projectId)
			throws JavaModelException {
		CodeContext currentCodeContext = contextCreator.createCodeContext(editor, projectId);

		if (!focusedResource.equals(currentCompilationUnit) || !currentCodeContext.equals(lastCodeContext)) {
			ActivityEvent event = new ActivityEvent(projectId, ActivityType.DocumentFocus, currentCodeContext);
			recorder.recordEvent(event);

			lastEvent = event;
			currentCompilationUnit = focusedResource;
			lastCodeContext = currentCodeContext;
		}
	}

	private synchronized void trackCodingEvents(final IWorkbenchPart part) {
		if (!(part instanceof IEditorPart)) {
			return;
		}

		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				try {
					TrackingService trackingService = PluginContext.getInstance().getTrackingService();
					ActivityEvent event = null;
					IEditorPart editor = (IEditorPart) part;
					IResource focusedResource = EditorUtils.getActiveResource(editor);
					if (focusedResource == null || !trackingService.isTracked(focusedResource.getProject())) {
						return;
					}
					UUID projectId = trackingService.getTrackedProjects().get(focusedResource.getProject());

					if (focusedResource.equals(currentCompilationUnit) && lastEvent != null
							&& lastEvent.getType() != ActivityType.DocumentEdit) {
						// Currently in design mode, so we need to save an editing event
						event = new ActivityEvent(projectId, ActivityType.DocumentEdit,
								contextCreator.createCodeContext(editor, projectId));
						recorder.recordEvent(event);
					}

					if (lastEvent != null) {
						lastEvent = event;
					}
				} catch (NonExistingResourceException e) {
					LogManager.INSTANCE.logError(e,
							"Problem recording document edit. Cannot find the active resource.");
				} catch (Exception e) {
					LogManager.INSTANCE.logError(e, "Problem recording document edit.");
				}
			}
		});
	}

	public void startTrackingProject(IProject project, UUID projectId, DateTime startWorkspaceDate) {
		ActivityEvent openSolutionEvent = new ActivityEvent(projectId, ActivityType.OpenSolution,
				contextCreator.createCodeContext(project));
		openSolutionEvent.setCreationTime(startWorkspaceDate);
		ActivityState systemState = ActivityState.createSystemState(projectId);
		systemState.setCreationTime(startWorkspaceDate);

		recorder.recordState(systemState);
		recorder.recordEvent(openSolutionEvent);
		ActivityState nullState = ActivityState.createNullState(projectId);
		recorder.recordState(nullState);

		currentState = nullState;
	}

	private void register(IWorkbenchWindow window) {
		window.getPartService().addPartListener(partsListener);
		window.getSelectionService().addPostSelectionListener(selectionListener);
		for (IWorkbenchPage page : window.getPages()) {
			for (IEditorReference ref : page.getEditorReferences()) {
				IEditorPart editor = ref.getEditor(false);
				registerPart(editor);
			}
		}
	}

	private void deregisterWindow(IWorkbenchWindow window) {
		window.getPartService().removePartListener(partsListener);
		window.getSelectionService().removePostSelectionListener(selectionListener);
		for (IWorkbenchPage page : window.getPages()) {
			for (IEditorReference ref : page.getEditorReferences()) {
				IEditorPart editor = ref.getEditor(false);
				deregisterPart(editor);
			}
		}
	}

	private void registerPart(final IWorkbenchPart part) {
		if (part instanceof IEditorPart) {
			IDocument doc = EditorUtils.getActiveDocument((IEditorPart) part);
			if (doc != null) {
				doc.addDocumentListener(documentListener);
				if (!registeredDocs.contains(doc)) {
					registeredDocs.add(doc);
				}
			}
		}
	}

	private void deregisterPart(IWorkbenchPart part) {
		if (part instanceof IEditorPart) {
			IDocument doc = EditorUtils.getActiveDocument((IEditorPart) part);
			if (doc != null) {
				registeredDocs.remove(doc);
			}
		}
	}

	/**
	 * A window listener listening to window focus.
	 */
	private final IWindowListener winListener = new IWindowListener() {

		@Override
		public void windowActivated(IWorkbenchWindow window) {
			ActivityState newState;
			if (lastState != null) {
				newState = lastState.recreate();
			} else {
				newState = ActivityState.createIdleState(PluginContext.UNASSIGNED_PROJECT);
			}
			lastState = currentState;
			currentState = newState;
			recorder.recordState(currentState);
			// trackDocumentFocus(window.getPartService().getActivePart());
		}

		@Override
		public void windowClosed(IWorkbenchWindow window) {
			deregisterWindow(window);
		}

		@Override
		public void windowDeactivated(IWorkbenchWindow window) {
			IdleActivityState idle = ActivityState.createIdleState(PluginContext.UNASSIGNED_PROJECT);
			recorder.recordState(idle);
			lastState = currentState;
			currentState = idle;
		}

		@Override
		public void windowOpened(IWorkbenchWindow window) {
			register(window);
			if (window.getWorkbench().getActiveWorkbenchWindow() == window) {
				trackDocumentFocus(window.getPartService().getActivePart());
			}
		}
	};

	public StateTracker(Display disp) {
		this.registeredDocs = new HashSet<IDocument>();
		this.contextCreator = PluginContext.getInstance().getContextCreator();
		display = disp;

		recorder = new ActivitiesRecorder(PluginContext.getInstance());
	}

	public void startTracking() {
		DebugPlugin debug = DebugPlugin.getDefault();
		debug.addDebugEventListener(debugEventListener);

		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.addWindowListener(winListener);
		for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
			register(window);
		}

		ResourcesPlugin.getWorkspace().addResourceChangeListener(buildEventsListener,
				IResourceChangeEvent.PRE_BUILD | IResourceChangeEvent.POST_BUILD);
		startIdleDetection();
	}

	private void startIdleDetection() {
		if (this.idleDetectionExecutor != null)
			return;

		display.syncExec(new Runnable() {

			@Override
			public void run() {
				display.addFilter(SWT.KeyDown, userActivityListener);
				display.addFilter(SWT.MouseDown, userActivityListener);
			}

		});

		this.idleDetectionExecutor = Executors.newScheduledThreadPool(1);
		Runnable idlePeriodicTask = new Runnable() {

			@Override
			public void run() {
				try {
					TrackingConsole.getInstance().trackMessage("Idle detection task executed");
					checkIdleStatus();
				} catch (Exception e) {
					TrackingConsole.getInstance().trackMessage("Idle detection task error " + e.getMessage());
				}
			}
		};

		int idleDetectionPeriod = PluginContext.getInstance().getConfiguration().getIdleCheckInterval();
		this.idleDetectionExecutor.scheduleAtFixedRate(idlePeriodicTask, idleDetectionPeriod, idleDetectionPeriod,
				TimeUnit.MILLISECONDS);
	}

	private void stopIdleDetection() {
		if (this.idleDetectionExecutor != null) {
			this.idleDetectionExecutor.shutdownNow();
			this.idleDetectionExecutor = null;
		}
	}

	private void checkIdleStatus() {
		synchronized (this) {

			if (currentState.getType() == ActivityType.Idle) {

				if (currentState instanceof NullActivityState) {
					currentState = ActivityState.createIdleState(PluginContext.UNASSIGNED_PROJECT);
					recorder.recordState(currentState);
				} else {
					return;
				}

			}
		}
	}

	protected Listener userActivityListener = new Listener() {

		// This listens to activity only inside the ide (probably is useful for activity
		// outside the editor)
		@Override
		public void handleEvent(Event event) {
			if (currentState instanceof IdleActivityState) {
				IdleActivityState state = (IdleActivityState) currentState;
				DateTime now = DateTime.now();
				Duration duration = new Duration(state.getLastActivity(), now);
				state.setLastActivity(now);

				long idleMaxPeriodInSeconds = PluginContext.getInstance().getConfiguration().getIdleMinInterval()
						/ 1000;
				Duration idleMinInterval = Duration.standardMinutes(idleMaxPeriodInSeconds / 60);
				if (duration.compareTo(idleMinInterval) < 0) {
					currentState = ActivityState.createIdleState(PluginContext.UNASSIGNED_PROJECT);
					recorder.recordState(currentState);
				}
			}
		}
	};

	public void stopTracking() {
		DebugPlugin debug = DebugPlugin.getDefault();
		debug.removeDebugEventListener(debugEventListener);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(buildEventsListener);

		stopIdleDetection();

		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.removeWindowListener(winListener);

		if (!display.isDisposed()) {
			display.syncExec(new Runnable() {

				@Override
				public void run() {
					display.removeFilter(SWT.KeyDown, userActivityListener);
					display.removeFilter(SWT.MouseDown, userActivityListener);
				}

			});
		}
	}

	public FlushResult flush(String identity, String token) {
		try {
			return this.recorder.flush(identity, token);
		} catch (Exception e) {
			LogManager.INSTANCE.logError(e, "Couldn't send data to the server.");
			return FlushResult.Report;
		}
	}

}
