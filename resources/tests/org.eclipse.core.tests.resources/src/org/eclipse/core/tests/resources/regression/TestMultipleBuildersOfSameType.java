/*******************************************************************************
 * Copyright (c) 2004, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM - Initial API and implementation
 *     Alexander Kurtakov <akurtako@redhat.com> - Bug 459343
 *******************************************************************************/
package org.eclipse.core.tests.resources.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestPluginConstants.PI_RESOURCES_TESTS;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;
import static org.eclipse.core.tests.resources.ResourceTestUtil.setAutoBuilding;
import static org.eclipse.core.tests.resources.ResourceTestUtil.updateProjectDescription;

import java.io.ByteArrayInputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.harness.session.SessionTestExtension;
import org.eclipse.core.tests.internal.builders.SortBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests that multiple builders of the same type can be installed on a single
 * project, and that their deltas are correctly serialized and associated with
 * the correct builder in the next session.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestMultipleBuildersOfSameType {

	@RegisterExtension
	static SessionTestExtension sessionTestExtension = SessionTestExtension.forPlugin(PI_RESOURCES_TESTS)
			.withCustomization(SessionTestExtension.createCustomWorkspace()).create();

	//various resource handles
	private IProject project1;
	private IFolder sorted1, unsorted1;
	private IFile unsortedFile1;

	@BeforeEach
	public void setUp() throws Exception {
		IWorkspaceRoot root = getWorkspace().getRoot();
		project1 = root.getProject("Project1");
		unsorted1 = project1.getFolder(SortBuilder.UNSORTED_FOLDER);
		sorted1 = project1.getFolder(SortBuilder.SORTED_FOLDER);
		unsortedFile1 = unsorted1.getFile("File1");
	}

	/**
	 * Create projects, setup a builder, and do an initial build.
	 */
	@Test
	@Order(1)
	public void test1() throws CoreException {
		IResource[] resources = {project1, unsorted1, sorted1, unsortedFile1};
		createInWorkspace(resources);

		// give unsorted files some initial content
		unsortedFile1.setContents(new ByteArrayInputStream(new byte[] { 1, 4, 3 }), true, true, null);

		setAutoBuilding(false);

		// configure builder for project1
		updateProjectDescription(project1).addingCommand(SortBuilder.BUILDER_NAME).withTestBuilderId("Project1Build1")
				.andCommand(SortBuilder.BUILDER_NAME).withTestBuilderId("Project2Build1").apply();

		// initial build -- created sortedFile1
		getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, createTestMonitor());

		getWorkspace().save(true, createTestMonitor());
	}

	/**
	 * Do another build immediately after restart.  Builder1 should be invoked because it cares
	 * about changes made by Builder2 during the last build phase.
	 */
	@Test
	@Order(2)
	public void test2() throws CoreException {
		getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, createTestMonitor());
		//Only builder1 should have been built
		SortBuilder[] builders = SortBuilder.allInstances();
		assertThat(builders).hasSize(2).satisfiesExactly(first -> {
			assertThat(first.wasBuilt()).isTrue();
			assertThat(first.wasIncrementalBuild()).isTrue();
		}, second -> assertThat(second.wasAutoBuild()).isFalse());
	}

}
