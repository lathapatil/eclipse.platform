/*******************************************************************************
 *  Copyright (c) 2003, 2012 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.resources.session;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestPluginConstants.PI_RESOURCES_TESTS;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Properties;
import org.eclipse.core.internal.resources.TestingSupport;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.harness.session.SessionTestExtension;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * This is an internal test that makes sure the workspace master table does not
 * contain any stale entries after restart
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestMasterTableCleanup {
	private static final String CLOSE_OPEN = "CloseOpen";
	private static final String CLOSE_DELETE = "CloseDelete";

	@RegisterExtension
	static SessionTestExtension sessionTestExtension = SessionTestExtension.forPlugin(PI_RESOURCES_TESTS)
			.withCustomization(SessionTestExtension.createCustomWorkspace()).create();

	/**
	 * Setup. Two scenarios with stale entries were: 1) Project that was closed and
	 * then opened 2) Project that was closed and then deleted
	 */
	@Test
	@Order(1)
	public void test1() throws CoreException {
		IProject closeOpen = getWorkspace().getRoot().getProject(CLOSE_OPEN);
		closeOpen.create(null);
		closeOpen.open(null);
		closeOpen.close(null);
		closeOpen.open(null);

		IProject closeDelete = getWorkspace().getRoot().getProject(CLOSE_DELETE);
		closeDelete.create(null);
		closeDelete.open(null);
		closeDelete.close(null);
		closeDelete.delete(IResource.ALWAYS_DELETE_PROJECT_CONTENT, null);

		getWorkspace().save(true, null);
	}

	/**
	 * Verify. Ensure safe table does not contain stale entries.
	 */
	@Test
	@Order(2)
	public void test2() {
		Properties masterTable = TestingSupport.getMasterTable();
		//ensure master table does not contain entries for stale projects
		IProject closeOpen = getWorkspace().getRoot().getProject(CLOSE_OPEN);
		IProject closeDelete = getWorkspace().getRoot().getProject(CLOSE_DELETE);
		assertFalse(masterTable.containsKey(closeOpen.getFullPath().append(".tree").toString()));
		assertFalse(masterTable.containsKey(closeDelete.getFullPath().append(".tree").toString()));
	}

}
