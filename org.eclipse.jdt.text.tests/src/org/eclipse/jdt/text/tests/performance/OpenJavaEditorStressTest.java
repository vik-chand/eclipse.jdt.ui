/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.text.tests.performance;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IFile;

import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

import org.eclipse.ui.PartInitException;

public class OpenJavaEditorStressTest extends TextPerformanceTestCase {

	private static final Class THIS= OpenJavaEditorStressTest.class;
	
	private static final String FILE= PerformanceTestSetup.PROJECT + "/Eclipse SWT/win32/org/eclipse/swt/graphics/TextLayout.java";
	
	private static final int NUMBER_OF_RUNS= 100;

	public static Test suite() {
		return new PerformanceTestSetup(new TestSuite(THIS));
	}

	public void testOpenJavaEditor1() throws PartInitException {
		setMeasuredRuns(NUMBER_OF_RUNS);
		IFile file= ResourceTestHelper.findFile(FILE);
		
		// make sure everything has been activated and loaded at least once
		EditorTestHelper.openInEditor(file, true);
		EditorTestHelper.closeAllEditors();
		
		Performance performance= Performance.getDefault();
		PerformanceMeter performanceMeter= performance.createPerformanceMeter(performance.getDefaultScenarioId(this));
		performanceMeter.start();
		try {
			for (int i= 0, n= getMeasuredRuns(); i < n; i++) {
				EditorTestHelper.openInEditor(file, true);
				EditorTestHelper.closeAllEditors();
				EditorTestHelper.runEventQueue();
			}
			performanceMeter.stop();
			performanceMeter.commit();
			performance.assertPerformanceInAbsoluteBand(performanceMeter, Dimension.USED_JAVA_HEAP, -20*1024*1024, +20*1024*1024);
		} finally {
			performanceMeter.dispose();
			EditorTestHelper.closeAllEditors();
		}
	}
}