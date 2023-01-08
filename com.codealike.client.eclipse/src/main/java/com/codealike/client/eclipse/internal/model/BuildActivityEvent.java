package com.codealike.client.eclipse.internal.model;

import java.util.UUID;

import com.codealike.client.eclipse.internal.dto.ActivityType;

public class BuildActivityEvent extends ActivityEvent {

	public BuildActivityEvent(UUID projectId, ActivityType type, CodeContext context) {
		super(projectId, type, context);
	}
	
	@Override
	public boolean isMarker() {
		return this.type != ActivityType.BuildProject;
	}
	
	@Override
	public boolean isBuildEvent() {
		return true;
	}

}
