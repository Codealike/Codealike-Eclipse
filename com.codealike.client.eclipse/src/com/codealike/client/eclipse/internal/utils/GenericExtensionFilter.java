package com.codealike.client.eclipse.internal.utils;

import java.io.File;
import java.io.FilenameFilter;

	public class GenericExtensionFilter implements FilenameFilter {

		private String ext;

		public GenericExtensionFilter(String ext) {
			this.ext = ext;
		}

		public boolean accept(File dir, String name) {
			return (name.endsWith(ext));
		}
	}
