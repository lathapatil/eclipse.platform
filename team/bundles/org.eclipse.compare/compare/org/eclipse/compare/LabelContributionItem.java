/*******************************************************************************
 * Copyright (c) ETAS GmbH 2023, all rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     ETAS GmbH - initial API and implementation
 *******************************************************************************/

package org.eclipse.compare;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;


/**
 * @since 3.10
 *
 *        A contribution item which delegates to a label on the tool bar.
 *
 */
public class LabelContributionItem extends ControlContribution {

	private Label toolbarLabel;
	private String labelName;

	/**
	 * @param id
	 * @param name
	 */
	public LabelContributionItem(String id, String name) {
		super(id);
		this.labelName = name;
	}

	@Override
	protected Control createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.LEFT);
		this.toolbarLabel = new Label(composite, SWT.LEFT);

		GridLayout compositeLayout = new GridLayout(1, false);
		compositeLayout.marginTop = -3;
		compositeLayout.marginBottom = -3;
		composite.setLayout(compositeLayout);
		GridData labelLayout = new GridData(SWT.LEFT, SWT.BOTTOM, true, true);

		this.toolbarLabel.setLayoutData(labelLayout);
		this.toolbarLabel.setText(this.labelName);

		return composite;
	}

	public Label getToolbarLabel() {
		return toolbarLabel;
	}
}
