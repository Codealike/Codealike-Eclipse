package com.codealike.client.eclipse.internal.services;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseService {
	private final List<ServiceListener> listeners = new ArrayList<>();
	
	public void addListener(ServiceListener listener) {
		listeners.add(listener);
	}

	// Use proper access modifier
	protected void publishEvent() {
		System.out.println(this + " is goint to publish to " + listeners + " listeners.");
		for (ServiceListener listener : listeners) {
			listener.onEvent();
		}
	}

}
