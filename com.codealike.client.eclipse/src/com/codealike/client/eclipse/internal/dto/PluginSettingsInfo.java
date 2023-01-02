/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pluggin settings information DTO class.
 * 
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class PluginSettingsInfo {
	@JsonProperty("idleCheckInterval")
	private int idleCheckInterval;
	@JsonProperty("idleMaxPeriod")
	private int idleMaxPeriod;
	@JsonProperty("flushInterval")
	private int flushInterval;
	@JsonProperty("overrideTrackingSettings")
	private Boolean overrideTrackingSettings;

	/**
	 * Default constructor
	 */
	public PluginSettingsInfo() {
		this.idleCheckInterval = 0;
		this.idleMaxPeriod = 0;
		this.flushInterval = 0;
		this.overrideTrackingSettings = true;
	}

	public int getIdleCheckInterval() {
		return idleCheckInterval;
	}

	@JsonProperty("idleCheckInterval")
	public void setIdleCheckInterval(int idleCheckInterval) {
		this.idleCheckInterval = idleCheckInterval;
	}

	public int getIdleMaxPeriod() {
		return idleMaxPeriod;
	}

	@JsonProperty("idleMaxPeriod")
	public void setIdleMaxPeriod(int idleMaxPeriod) {
		this.idleMaxPeriod = idleMaxPeriod;
	}

	public int getFlushInterval() {
		return flushInterval;
	}

	@JsonProperty("flushInterval")
	public void setFlushInterval(int flushInterval) {
		this.flushInterval = flushInterval;
	}

	public Boolean getOverrideTrackingSettings() {
		return overrideTrackingSettings;
	}

	@JsonProperty("overrideTrackingSettings")
	public void setOverrideTrackingSettings(Boolean overrideTrackingSettings) {
		this.overrideTrackingSettings = overrideTrackingSettings;
	}
}
