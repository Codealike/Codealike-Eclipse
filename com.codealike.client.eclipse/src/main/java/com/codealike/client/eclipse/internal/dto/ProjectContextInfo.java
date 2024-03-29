/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.dto;

import java.util.UUID;

/**
 * Project context information DTO class.
 * 
 * @author Daniel, pvmagacho
 * @version 1.5.0.2
 */
public class ProjectContextInfo {

	private UUID projectId;
	private String name;

	/**
	 * Default constructor.
	 */
	public ProjectContextInfo() {
	}

	/**
	 * Project context information class constructor
	 * 
	 * @param projectId   the project UUID
	 * @param projectName the project Name
	 */
	public ProjectContextInfo(UUID projectId, String project) {
		this.projectId = projectId;
		this.name = project;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setProjectId(UUID projectId) {
		this.projectId = projectId;
	}

}
