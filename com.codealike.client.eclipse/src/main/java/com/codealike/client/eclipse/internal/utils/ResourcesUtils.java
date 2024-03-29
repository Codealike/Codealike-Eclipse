/*
 * Copyright (c) 2022. All rights reserved to Torc LLC.
 */
package com.codealike.client.eclipse.internal.utils;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.ImageData;

import com.codealike.client.eclipse.CodealikeTrackerPlugin;

public class ResourcesUtils {

	public static ImageData loadImageData(String imagePath) throws MalformedURLException {
		final URL url = new URL("platform:/plugin/" + CodealikeTrackerPlugin.getDefault().getBundle().getSymbolicName()
				+ "/" + imagePath);

		final ImageDescriptor imgDesc = ImageDescriptor.createFromURL(url);
		return imgDesc.getImageData(100);
	}

}
