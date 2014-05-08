package com.codealike.client.eclipse.internal.dto;

import com.codealike.client.eclipse.internal.model.TrackActivity;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UserConfigurationInfo {

    private TrackActivity trackActivities;
    
	public TrackActivity getTrackActivities() {
		return trackActivities;
	}

	@JsonProperty("TrackActivities")
	public void setTrackActivities(TrackActivity trackActivities) {
		this.trackActivities = trackActivities;
	}
	
}
