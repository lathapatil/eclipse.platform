/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.help.internal.webapp.data;

import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Platform;
import org.eclipse.help.internal.base.HelpBasePlugin;

/**
 * Utility class for parsing the CSS preferences
 */

public class CssUtil {
	
	private final static String cssLink1 = "<link rel=\"stylesheet\" href=\"../content/PLUGINS_ROOT"; //$NON-NLS-1$
	private static final String cssLink2 = "\" type=\"text/css\"></link>\n"; //$NON-NLS-1$
	
	private static String replaceParameters(String input) {
		final String OS = "${os}"; //$NON-NLS-1$
		int index = input.indexOf(OS);
		if (index < 0) {
			return input;
		}
		String result = input.substring(0, index) + Platform.getOS() + input.substring(index + OS.length());
		return replaceParameters(result); 
	}
	
	/**
	 * @param filenames 
	 * @return
	 */
	public static String[] getCssFilenames(String filenames ) {
		if (filenames  == null) {
			return new String[0];
		}
		StringTokenizer tok = new StringTokenizer(filenames , ","); //$NON-NLS-1$
		String[] result = new String[tok.countTokens()];
		for (int i = 0; tok.hasMoreTokens(); i++) {
			result[i] = replaceParameters(tok.nextToken().trim());
		}
		return result;
	}
	
	public static void addCssFiles(final String preference, List list) {
		String topicCssPath = Platform.getPreferencesService().getString(HelpBasePlugin.PLUGIN_ID, preference, "", null);  //$NON-NLS-1$
		String[] cssFiles = CssUtil.getCssFilenames(topicCssPath);
		for (int i = 0; i < cssFiles.length; i++) {
			list.add(cssFiles[i]);
		}
	}
	
	public static String createCssIncludes(List cssFiles) {
		StringBuffer script = new StringBuffer();
		for (Iterator iter = cssFiles.iterator(); iter.hasNext();) {
			String cssPath = (String) iter.next();
			script.append(cssLink1);
			script.append(cssPath);
			script.append(cssLink2);
		}
		return script.toString();
	}

}
