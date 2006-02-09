/*******************************************************************************
 * Copyright (c) 2002, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.cheatsheets.views;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Properties;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.help.ui.internal.views.HelpTray;
import org.eclipse.help.ui.internal.views.IHelpPartPage;
import org.eclipse.help.ui.internal.views.ReusableHelpPart;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.cheatsheets.CheatSheetListener;
import org.eclipse.ui.cheatsheets.ICheatSheetEvent;
import org.eclipse.ui.cheatsheets.ICheatSheetViewer;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.internal.cheatsheets.CheatSheetPlugin;
import org.eclipse.ui.internal.cheatsheets.CheatSheetStopWatch;
import org.eclipse.ui.internal.cheatsheets.ICheatSheetResource;
import org.eclipse.ui.internal.cheatsheets.Messages;
import org.eclipse.ui.internal.cheatsheets.actions.IMenuContributor;
import org.eclipse.ui.internal.cheatsheets.composite.model.CompositeCheatSheetModel;
import org.eclipse.ui.internal.cheatsheets.composite.views.CompositeCheatSheetPage;
import org.eclipse.ui.internal.cheatsheets.data.CheatSheet;
import org.eclipse.ui.internal.cheatsheets.data.CheatSheetParser;
import org.eclipse.ui.internal.cheatsheets.data.CheatSheetSaveHelper;
import org.eclipse.ui.internal.cheatsheets.data.ICheatSheet;
import org.eclipse.ui.internal.cheatsheets.data.IParserTags;
import org.eclipse.ui.internal.cheatsheets.registry.CheatSheetElement;
import org.eclipse.ui.internal.cheatsheets.registry.CheatSheetRegistryReader;
import org.osgi.framework.Bundle;

public class CheatSheetViewer implements ICheatSheetViewer, IMenuContributor {

	//CS Elements
	private CheatSheetElement contentElement;
	private URL contentURL;
	private String currentID;
	private int currentItemNum;
	// Used to indicate if an invalid cheat sheet id was specified via setInput.
	private boolean invalidCheatSheetId = false;
	// Used to indicate if a null cheat sheet id was specified via setInput.
	private boolean nullCheatSheetId = false;

	private CheatSheetParser parser;
	private ICheatSheet model;
	private CheatSheetManager manager;
	private CheatSheetSaveHelper saveHelper;

	private CheatSheetExpandRestoreAction expandRestoreAction;
	private Action copyAction;

	//ITEMS
	private ViewItem currentItem;

	//Lists
	private ArrayList expandRestoreList = new ArrayList();
	private ArrayList viewItemList = new ArrayList();

	//Composites
	protected Composite control;

	private Cursor busyCursor;
	
	// The page currently displayed, may be a CheatSheetPage, CompositeCheatSheetPage
	// or ErrorPage
	private Page currentPage;  
	private Label howToBegin;
	private boolean inDialog;
	private Listener listener;
	
	private IPath savePath = null;
	private IPath nextSavePath = null;
	private String restorePath;

	/**
	 * The constructor.
	 * 
	 * @param inDialog whether or not this viewer will be placed in a modal dialog
	 */
	public CheatSheetViewer(boolean inDialog) {
		currentItemNum = -1;
		this.inDialog = inDialog;
		saveHelper = new CheatSheetSaveHelper();
	}

	/*package*/ void advanceIntroItem() {
		resetItemState();
		/* LP-item event */
		// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_DEACTIVATED, introItem);

		currentItemNum = 1;
		ViewItem nextItem = getViewItemAtIndex(currentItemNum);
		if (nextItem.item.isDynamic()) {
			nextItem.handleButtons();
		}
		nextItem.setAsCurrentActiveItem();
		/* LP-item event */
		// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_ACTIVATED, nextItem);
		collapseAllButCurrent(false);
		
		saveCurrentSheet();
	}

	/**
	 * Reset the state of all the items in this cheatsheet
	 */
	private void resetItemState() {
		IntroItem introItem = (IntroItem) getViewItemAtIndex(0);
		boolean isStarted = introItem.isCompleted();

		expandRestoreList = new ArrayList();
		if(expandRestoreAction != null)
			expandRestoreAction.setCollapsed(false);

		clearBackgrounds();
		clearIcons();
		collapseAllButtons();
		if(isStarted)
			initManager();

		for (Iterator iter = viewItemList.iterator(); iter.hasNext();) {
			ViewItem item = (ViewItem) iter.next();
			if (item instanceof CoreItem) {
				CoreItem c = (CoreItem) item;
				ArrayList l = c.getListOfSubItemCompositeHolders();
				if (l != null)
					for (int j = 0; j < l.size(); j++) {
						((SubItemCompositeHolder) l.get(j)).setSkipped(false);
						((SubItemCompositeHolder) l.get(j)).setCompleted(false);
					}
			}
		}

		if (isStarted)
			getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_RESTARTED);
		else
			getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_STARTED);

		isStarted = true;
		introItem.setAsNormalCollapsed();
		introItem.setComplete();
		introItem.setRestartImage();
	}

	/*package*/ 
	/*
	 * This function can do one of three things
	 * 1. If this item has a completion message which has not been displayed, display it
	 * 2. Otherwise if this is the final item return to the introduction
	 * 3. If neither condition 1 or 2 is satisfied move to the next item
	 */
	void advanceItem(ImageHyperlink link, boolean markAsCompleted) {
		currentItem = (ViewItem) link.getData();
		int indexNextItem = getIndexOfItem(currentItem) +1;
		boolean isFinalItem = indexNextItem >= viewItemList.size();
		
		if (markAsCompleted 
				&& currentItem.hasCompletionMessage() 
				&& !currentItem.isCompletionMessageExpanded()) {
			currentItem.setCompletionMessageExpanded(isFinalItem);
			currentItem.setComplete();
			if (isFinalItem) {
				getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_COMPLETED);
			}
			return;
		}

		if (indexNextItem < currentItemNum) {
			ViewItem vi = getViewItemAtIndex(currentItemNum);
			vi.setAsNormalNonCollapsed();
		}
		if (currentItem != null) {
			//set that item to it's original color.
			currentItem.setAsNormalCollapsed();
			//set that item as complete.
			if (markAsCompleted) {
				if (!currentItem.isCompleted()) {
				    currentItem.setComplete();
				}
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_COMPLETED, currentItem);
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_DEACTIVATED, currentItem);
			} else {
				currentItem.setSkipped();
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_SKIPPED, currentItem);
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_DEACTIVATED, currentItem);
			}
		}
		if (!isFinalItem) {
			ViewItem nextItem = getViewItemAtIndex(indexNextItem);
			currentItemNum = indexNextItem;
			if (nextItem != null) {
				//Handle lazy button instantiation here.
				if (nextItem.item.isDynamic()) {
					((CoreItem) nextItem).handleButtons();
				}
				nextItem.setAsCurrentActiveItem();
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_ACTIVATED, nextItem);
				currentItem = nextItem;
			}

			FormToolkit.ensureVisible(currentItem.getMainItemComposite());
		} else if (indexNextItem == viewItemList.size()) {
			saveCurrentSheet();
			showIntroItem();
			if (!currentItem.isCompletionMessageExpanded()) { // The event will already have been fired
			    getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_COMPLETED);
			}
		}

		saveCurrentSheet();
	}

	private void showIntroItem() {
		ViewItem item = getViewItemAtIndex(0);
		item.setExpanded();
		item.setBold(true);
		item.getMainItemComposite().setFocus();
	}

	/*package*/ void advanceSubItem(ImageHyperlink link, boolean markAsCompleted, int subItemIndex) {
		Label l = null;
		ArrayList list = null;
		SubItemCompositeHolder sich = null;
		CoreItem ciws = null;

		currentItem = (ViewItem) link.getData();

		if (currentItem instanceof CoreItem)
			ciws = (CoreItem) currentItem;

		if (ciws != null) {
			list = ciws.getListOfSubItemCompositeHolders();
			sich = (SubItemCompositeHolder) list.get(subItemIndex);
			l = sich.getIconLabel();
		}

		if (l != null) {
			if (markAsCompleted) {
				l.setImage(((ViewItem) ciws).getCompleteImage());
				sich.setCompleted(true);
				sich.setSkipped(false);
				/* LP-subitem event */
				// fireManagerSubItemEvent(ICheatSheetItemEvent.ITEM_COMPLETED, ciws, subItemID);
			} else {
				l.setImage(((ViewItem) ciws).getSkipImage());
				sich.setSkipped(true);
				sich.setCompleted(false);
				/* LP-subitem event */
				// fireManagerSubItemEvent(ICheatSheetItemEvent.ITEM_SKIPPED, ciws, subItemID);
			}
		}

		boolean allAttempted = checkAllAttempted(list);
		boolean anySkipped = checkContainsSkipped(list);

		if (allAttempted && !anySkipped) {
			advanceItem(link, true);
			return;
		} else if (allAttempted && anySkipped) {
			advanceItem(link, false);
			return;
		}

		FormToolkit.ensureVisible(currentItem.getMainItemComposite());
		saveCurrentSheet();
	}

	private boolean checkAllAttempted(ArrayList list) {
		for (int i = 0; i < list.size(); i++) {
			SubItemCompositeHolder s = (SubItemCompositeHolder) list.get(i);
			if (s.isCompleted() || s.isSkipped()) {
				continue;
			}
			return false;
		}
		return true;
	}

	private boolean checkContainsSkipped(ArrayList list) {
		for (int i = 0; i < list.size(); i++) {
			SubItemCompositeHolder s = (SubItemCompositeHolder) list.get(i);
			if (s.isSkipped()) {
				return true;
			}
		}
		return false;
	}

	private boolean checkSavedState() {
		try {
			Properties props = saveHelper.loadState(currentID, savePath);
	
			// There is a bug which causes the background of the buttons to
			// remain white, even though the color is set. So instead of calling
			// clearBackgrounds() only the following line should be needed. D'oh!
			// ((ViewItem) viewItemList.get(0)).setOriginalColor();
			clearBackgrounds();
	
			if (props == null) {
				getViewItemAtIndex(0).setAsCurrentActiveItem();
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_ACTIVATED, items[0]);
				return true;
			}
	
			boolean buttonIsDown = (Integer.parseInt((String) props.get(IParserTags.BUTTON)) == 0) ? false : true;
			int itemNum = Integer.parseInt((String) props.get(IParserTags.CURRENT));
			ArrayList completedStatesList = (ArrayList) props.get(IParserTags.COMPLETED);
			ArrayList expandedStatesList = (ArrayList) props.get(IParserTags.EXPANDED);
			expandRestoreList = (ArrayList) props.get(IParserTags.EXPANDRESTORE);
			String cid = (String) props.get(IParserTags.ID);
			Hashtable completedSubItems = (Hashtable) props.get(IParserTags.SUBITEMCOMPLETED);
			Hashtable skippedSubItems = (Hashtable) props.get(IParserTags.SUBITEMSKIPPED);
			Hashtable csmData = (Hashtable) props.get(IParserTags.MANAGERDATA);
	
			ArrayList completedSubItemsItemList = new ArrayList();
			ArrayList skippedSubItemsItemList = new ArrayList();
	
			Enumeration e = completedSubItems.keys();
			while (e.hasMoreElements())
				completedSubItemsItemList.add(e.nextElement());
	
			Enumeration e2 = skippedSubItems.keys();
			while (e2.hasMoreElements())
				skippedSubItemsItemList.add(e2.nextElement());
	
			if (cid != null)
				currentID = cid;
	
			getManager().setData(csmData);
	
			if (itemNum >= 0) {
				currentItemNum = itemNum;
				
				currentItem = getViewItemAtIndex(itemNum);
	
				CheatSheetStopWatch.startStopWatch("CheatSheetViewer.checkSavedState()"); //$NON-NLS-1$
				for (int i = 0; i < viewItemList.size(); i++) {
	
					ViewItem item = getViewItemAtIndex(i);
					if (i > 0 && item.item.isDynamic() && i <= currentItemNum) {
						 item.handleButtons();
						 item.setOriginalColor();
					}
	
					if (completedStatesList.contains(Integer.toString(i))) {
						item.setComplete();
						item.setRestartImage();
					} else {
						if (i < currentItemNum) {
							item.setSkipped();
						}
					}
					if (expandedStatesList.contains(Integer.toString(i))) {
						item.setExpanded();
					} else {
						item.setCollapsed();
					}
					if (i > currentItemNum) {
						item.setButtonsCollapsed();
						item.setCompletionMessageCollapsed();
					} else {
						item.setButtonsExpanded();
						if (i >currentItemNum || item.isCompleted()) {
						    item.setCompletionMessageExpanded(i + 1 >= viewItemList.size());
					    } else {
							item.setCompletionMessageCollapsed();
					    }
					}
					if (expandRestoreList.contains(Integer.toString(i))) {
						item.setCollapsed();
					}
					if (completedSubItemsItemList.contains(Integer.toString(i))) {
						String subItemNumbers = (String) completedSubItems.get(Integer.toString(i));
						StringTokenizer st = new StringTokenizer(subItemNumbers, ","); //$NON-NLS-1$
						if (item instanceof CoreItem) {
							CoreItem coreitemws = (CoreItem) item;
							ArrayList subItemCompositeHolders = coreitemws.getListOfSubItemCompositeHolders();
		                    if (subItemCompositeHolders != null) {
								while (st.hasMoreTokens()) {
									String token = st.nextToken();
									((SubItemCompositeHolder) subItemCompositeHolders.get(Integer.parseInt(token))).setCompleted(true);
									((SubItemCompositeHolder) subItemCompositeHolders.get(Integer.parseInt(token))).getIconLabel().setImage(item.getCompleteImage());
									ArrayList l = subItemCompositeHolders;
									SubItemCompositeHolder s = (SubItemCompositeHolder) l.get(Integer.parseInt(token));
									if (s != null && s.getStartButton() != null) {
										s.getStartButton().setImage(CheatSheetPlugin.getPlugin().getImage(ICheatSheetResource.CHEATSHEET_ITEM_BUTTON_RESTART));
										s.getStartButton().setToolTipText(Messages.RESTART_TASK_TOOLTIP);
									}
		
								}
		                    }
						}
					}
					if (skippedSubItemsItemList.contains(Integer.toString(i))) {
						String subItemNumbers = (String) skippedSubItems.get(Integer.toString(i));
						StringTokenizer st = new StringTokenizer(subItemNumbers, ","); //$NON-NLS-1$
						if (item instanceof CoreItem) {
							CoreItem coreitemws = (CoreItem) item;
							while (st.hasMoreTokens()) {
								String token = st.nextToken();
								((SubItemCompositeHolder) coreitemws.getListOfSubItemCompositeHolders().get(Integer.parseInt(token))).setSkipped(true);
								((SubItemCompositeHolder) coreitemws.getListOfSubItemCompositeHolders().get(Integer.parseInt(token))).getIconLabel().setImage(item.getSkipImage());
							}
						}
					}
					CheatSheetStopWatch.printLapTime("CheatSheetViewer.checkSavedState()", "Time in CheatSheetViewer.checkSavedState() after loop #"+i+": "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				CheatSheetStopWatch.printLapTime("CheatSheetViewer.checkSavedState()", "Time in CheatSheetViewer.checkSavedState() after loop: "); //$NON-NLS-1$ //$NON-NLS-2$
	
				if (buttonIsDown) {
					if(expandRestoreAction != null)
						expandRestoreAction.setCollapsed(true);
					saveCurrentSheet();
				}
				
				// If the last item is the current one and it is complete then
				// we should collapse the last item and set the focus on intro.
				// For all other cases, set the current item as the active item.
				if(viewItemList.size()-1 == itemNum && currentItem.isCompleted()) {
					currentItem.setCollapsed();
					getViewItemAtIndex(0).getMainItemComposite().setFocus();
					
					// The cheat sheet has been restored but is also completed so fire both events
					getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_RESTORED);
					getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_COMPLETED);
				} else {
					currentItem.setAsCurrentActiveItem();
	
					// If the intro item is completed, than the cheat sheet has been restored.
					if(getViewItemAtIndex(0).isCompleted())
						getManager().fireEvent(ICheatSheetEvent.CHEATSHEET_RESTORED);
				}
	
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_ACTIVATED, currentItem);
			} else {
				getViewItemAtIndex(0).setAsCurrentActiveItem();
				/* LP-item event */
				// fireManagerItemEvent(ICheatSheetItemEvent.ITEM_ACTIVATED, items[0]);
			}
	
			FormToolkit.ensureVisible(currentItem.getMainItemComposite());
			return true;
		} catch(Exception e) {
			// An exception while restoring the saved state data usually only occurs if
			// the cheat sheet has been modified since this previous execution. This most
			// often occurs during development of cheat sheets and as such an end user is
			// not as likely to encounter this.
			
			// Log the exception
			String stateFile = saveHelper.getStateFile(currentID).toOSString();
			String message = NLS.bind(Messages.ERROR_APPLYING_STATE_DATA_LOG, (new Object[] {stateFile, currentID}));
			IStatus status = new Status(IStatus.ERROR, ICheatSheetResource.CHEAT_SHEET_PLUGIN_ID, IStatus.OK, message, e);
			CheatSheetPlugin.getPlugin().getLog().log(status);

			// Set the currentID to null so it is not saved during internalDispose()
			currentID = null;

			internalDispose();

			// Reinitialize a few variables because there is no currentItem or currentPage now
			contentURL = null;
			currentItem = null;
			currentItemNum = -1;
			currentPage = null;
			expandRestoreList = new ArrayList();
			viewItemList = new ArrayList();
			
			// Create the errorpage to show the user
			createErrorPage(Messages.ERROR_APPLYING_STATE_DATA);

			return false;
		}
	}

	private void clearBackgrounds() {
		for (Iterator iter = viewItemList.iterator(); iter.hasNext();) {
			ViewItem item = (ViewItem) iter.next();
			item.setOriginalColor();
		}
	}

	private void clearIcons() {
		for (Iterator iter = viewItemList.iterator(); iter.hasNext();) {
			ViewItem item = (ViewItem) iter.next();
			item.setOriginalColor();
			if (item.isCompleted() || item.isExpanded() || item.isSkipped())
					item.setIncomplete();
		}
	}

	private void collapseAllButCurrent(boolean fromAction) {
		expandRestoreList = new ArrayList();
		try {
			ViewItem current = getViewItemAtIndex(currentItemNum);
			for (ListIterator iter = viewItemList.listIterator(viewItemList.size()); iter.hasPrevious();) {
				ViewItem item = (ViewItem) iter.previous();
				if (item != current && item.isExpanded()) {
					item.setCollapsed();
					if (fromAction)
						expandRestoreList.add(Integer.toString(getIndexOfItem(item)));
				}
			}
		} catch (Exception e) {
		}
	}

	private void collapseAllButtons() {
		for (Iterator iter = viewItemList.listIterator(1); iter.hasNext();) {
			ViewItem item = (ViewItem) iter.next();
			item.setButtonsCollapsed();
			item.setCompletionMessageCollapsed();
		}
	}

	private void createErrorPage(String message) {
		if(message != null) {
			currentPage = new ErrorPage(message);
		} else {
			currentPage = new ErrorPage();
		}
		currentPage.createPart(control);
		control.layout(true);
	}

	/**
	 * Creates the SWT controls for this workbench part.
	 * <p>
	 * Clients should not call this method (the workbench calls this method at
	 * appropriate times).
	 * </p>
	 * <p>
	 * For implementors this is a multi-step process:
	 * <ol>
	 *   <li>Create one or more controls within the parent.</li>
	 *   <li>Set the parent layout as needed.</li>
	 *   <li>Register any global actions with the <code>IActionService</code>.</li>
	 *   <li>Register any popup menus with the <code>IActionService</code>.</li>
	 *   <li>Register a selection provider with the <code>ISelectionService</code>
	 *     (optional). </li>
	 * </ol>
	 * </p>
	 *
	 * @param parent the parent control
	 */
	public void createPartControl(Composite parent) {
		control = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 0;
		layout.horizontalSpacing = 0;
		layout.numColumns = 1;
		control.setLayout(layout);

		control.addDisposeListener(new DisposeListener(){
			public void widgetDisposed(DisposeEvent e) {
				dispose();
			}
		});
		
		/*
		 * org.eclipse.help.ui is an optional dependency; only perform this
		 * step is this plugin is present.
		 */
		if (!inDialog && (Platform.getBundle("org.eclipse.help.ui") != null)) { //$NON-NLS-1$
			listener = new Listener() {
				public void handleEvent(Event event) {
					if (isTrayDialog(event.widget)) {
						dialogOpened((TrayDialog)((Shell)event.widget).getData());
					}
				}
			};
			Display.getCurrent().addFilter(SWT.Show, listener);
		}

		howToBegin = new Label(control, SWT.WRAP);
		howToBegin.setText(Messages.INITIAL_VIEW_DIRECTIONS);
		howToBegin.setLayoutData(new GridData(GridData.FILL_BOTH));

		Display display = parent.getDisplay();

		busyCursor = new Cursor(display, SWT.CURSOR_WAIT);

		if(contentElement != null) {
			initCheatSheetView();
		}
	}

	/**
	 * Called when any TrayDialog is opened. The viewer must react by disabling
	 * itself and moving the cheat sheet to the dialog's tray if the current item
	 * was flagged as one that opens a modal dialog.
	 * 
	 * @param dialog the dialog that was opened
	 */
	private void dialogOpened(TrayDialog dialog) {
		if (isInDialogItem()) {
			HelpTray tray = (HelpTray)dialog.getTray();
			if (tray == null) {
				tray = new HelpTray();
				dialog.openTray(tray);
			}
			ReusableHelpPart helpPart = tray.getHelpPart();
			IHelpPartPage page = helpPart.createPage(CheatSheetHelpPart.ID, null, null);
			page.setVerticalSpacing(0);
			page.setHorizontalMargin(0);
			helpPart.addPart(CheatSheetHelpPart.ID, new CheatSheetHelpPart(helpPart.getForm().getForm().getBody(), helpPart.getForm().getToolkit(), page.getToolBarManager(), contentElement, savePath));
			page.addPart(CheatSheetHelpPart.ID, true);
			helpPart.addPage(page);
			helpPart.showPage(CheatSheetHelpPart.ID);
			
			/*
			 * Disable the viewer until the tray is closed, then show it again.
			 */
			control.setVisible(false);
			Display.getCurrent().removeFilter(SWT.Show, listener);

			helpPart.getControl().addListener(SWT.Dispose, new Listener() {
				public void handleEvent(Event event) {
					control.setVisible(true);
					Display.getCurrent().addFilter(SWT.Show, listener);
					checkSavedState();
				}
			});
		}
	}

	/**
	 * Disposes of this cheat sheet viewer.
	 */
	private void dispose() {
		if (listener != null) {
			Display.getCurrent().removeFilter(SWT.Show, listener);
		}
		internalDispose();
		if (busyCursor != null)
			busyCursor.dispose();
	}

	/*
	 * Returns the cheat sheet being viewed. 
	 */
	public ICheatSheet getCheatSheet() {
		return model;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.cheatsheets.ICheatSheetViewer#getCheatSheetID()
	 */
	public String getCheatSheetID() {
		if(getContent() != null) {
			return getContent().getID();
		}
		
		return null;
	}

	/**
	 * Returns the current content.
	 *
	 * @return CheatSheetElement
	 */
	/*package*/ CheatSheetElement getContent() {
		return contentElement;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.cheatsheets.ICheatSheetViewer#getControl()
	 */
	public Control getControl() {
		return control;
	}

	private int getIndexOfItem(ViewItem item) {
		int index = viewItemList.indexOf(item);
		if(index != -1) {
			return index;
		}
		return 0;
	}

	/*package*/ CheatSheetManager getManager() {
		if (manager == null) {
			getNewManager();
		}
		return manager;
	}

	private CheatSheetManager getNewManager(){
		manager = new CheatSheetManager(contentElement);
		return manager;
	}
	
	private CheatSheetManager initManager(){
		CheatSheetManager csManager = getManager();
		csManager.setData(new Hashtable());
		return csManager;
	}

	private ViewItem getViewItemAtIndex(int index) {
		if (viewItemList != null && !viewItemList.isEmpty()) {
			return (ViewItem) viewItemList.get(index);
		}
		return null;
	}
	
	/**
	 * Returns whether or not this viewer contains the given Control, which
	 * is currently in focus.
	 * 
	 * @param control the Control currently in focus
	 * @return whether this viewer contains the given Control or not
	 */
	public boolean hasFocusControl(Control control) {
		return (control == this.control) || (currentPage.getControl() == control);
	}
	
	private void initCheatSheetView() {
		CheatSheetStopWatch.startStopWatch("CheatSheetViewer.initCheatSheetView()"); //$NON-NLS-1$
		//Re-initialize list to store items collapsed by expand/restore action on c.s. toolbar.
		expandRestoreList = new ArrayList();

		// re set that action to turned off.
		if(expandRestoreAction != null)
			expandRestoreAction.setCollapsed(false);

		//reset current item to be null; next item too.
		currentItem = null;
		currentItemNum = 0;
		viewItemList = new ArrayList();

		// Reset the page variable
		currentPage = null;
		
		if(howToBegin != null) {
			howToBegin.dispose();
			howToBegin = null;
		}
		
		// read our contents, if there are problems reading the file an error page should be created.
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() before readFile() call: "); //$NON-NLS-1$ //$NON-NLS-2$
		boolean parsedOK = readFile();
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after readFile() call: "); //$NON-NLS-1$ //$NON-NLS-2$
		if(!parsedOK){
			// If a null cheat sheet id was specified, return leaving the cheat sheet empty.
			if(nullCheatSheetId) {
				return;
			}

			// Exception thrown during parsing.
			// Something is wrong with the Cheat sheet content file at the xml level.
			if(invalidCheatSheetId) {
				createErrorPage(Messages.ERROR_CHEATSHEET_DOESNOT_EXIST);
			} else {
				createErrorPage(null);
			}
			return;
		}
		
		control.setRedraw(false);
		if (model instanceof CheatSheet) {	
		    currentPage = new CheatSheetPage((CheatSheet)model, viewItemList, this);
		} else if (model instanceof CompositeCheatSheetModel) {
			CompositeCheatSheetModel compositeCheatSheetModel = ((CompositeCheatSheetModel)model);
			compositeCheatSheetModel.setId(currentID);
			currentPage = new CompositeCheatSheetPage(compositeCheatSheetModel);
			compositeCheatSheetModel.setCheatSheetManager(initManager());
	    }
	    CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after CheatSheetPage() call: "); //$NON-NLS-1$ //$NON-NLS-2$
	    currentPage.createPart(control);
	    CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after CheatSheetPage.createPart() call: "); //$NON-NLS-1$ //$NON-NLS-2$

	    if (model instanceof CheatSheet) {	
			initManager().fireEvent(ICheatSheetEvent.CHEATSHEET_OPENED);
			CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after fireEvent() call: "); //$NON-NLS-1$ //$NON-NLS-2$
	
			if(!checkSavedState()) {
				// An error occurred when apply the saved state data.
				control.setRedraw(true);
				control.layout();
				return;
			}
	    }
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after checkSavedState() call: "); //$NON-NLS-1$ //$NON-NLS-2$

		currentPage.initialized();
		control.setRedraw(true);
		control.layout();
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() after layout() call: "); //$NON-NLS-1$ //$NON-NLS-2$

		if (currentItem != null && !currentItem.isCompleted())
			currentItem.getMainItemComposite().setFocus();
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.initCheatSheetView()", "Time in CheatSheetViewer.initCheatSheetView() at end of method: "); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void internalDispose() {
		if(manager != null)
			manager.fireEvent(ICheatSheetEvent.CHEATSHEET_CLOSED);
       
		saveCurrentSheet();

		for (Iterator iter = viewItemList.iterator(); iter.hasNext();) {
			ViewItem item = (ViewItem) iter.next();
			item.dispose();
		}

		if(currentPage != null) {
			currentPage.dispose();
		}
	}

	/**
	 * Returns whether or not the currently active item requires opening a
	 * modal dialog.
	 * 
	 * @return whether the current item opens a modal dialog
	 */
	private boolean isInDialogItem() {
		ViewItem item = getViewItemAtIndex(currentItemNum);
		if (item != null) {
			return item.getItem().isDialog();
		}
		return false;
	}
	
	/**
	 * Returns whether or not this cheat sheet viewer is inside a modal
	 * dialog.
	 * 
	 * @return whether this viewer is inside a modal dialog
	 */
	public boolean isInDialogMode() {
		return inDialog;
	}
	
	/**
	 * Returns whether the given widget is a TrayDialog.
	 * 
	 * @param widget the widget to check
	 * @return whether or not the widget is a TrayDialog
	 */
	private boolean isTrayDialog(Widget widget) {
		return (widget instanceof Shell && ((Shell)widget).getData() instanceof TrayDialog);
	}

	/**
	* Read the contents of the welcome page
	*/
	private boolean readFile() {
		if(parser == null)
			parser = new CheatSheetParser();
		model = parser.parse(contentURL);
		return model == null ? false : true;
	}

	private void restoreExpandStates() {
		try {
			for (int i = 0; i < expandRestoreList.size(); i++) {
				int index = Integer.parseInt(((String) expandRestoreList.get(i)));
				ViewItem item = getViewItemAtIndex(index);
				if (!item.isExpanded()) {
					item.setExpanded();
				}
			}
			expandRestoreList = null;
		} catch (Exception e) {
		}
	}
	
	/*package*/ void runPerformExecutable(ImageHyperlink link) {
		link.setCursor(busyCursor);
		currentItem = (ViewItem) link.getData();
		CoreItem coreItem = (CoreItem) currentItem;

		if (coreItem != null) {
			IStatus status = coreItem.runExecutable(getManager());
			if ( status.isOK() && !coreItem.hasConfirm()) {					
				coreItem.setRestartImage();
				//set that item as complete.
				advanceItem(link, true);
				saveCurrentSheet();
			}
			if ( status.getSeverity() == IStatus.ERROR) {
				CheatSheetPlugin.getPlugin().getLog().log(status);
			    org.eclipse.jface.dialogs.ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), null, null, status);								
			}
		}

		link.setCursor(null);
	}

	/*package*/ void runSubItemPerformExecutable(ImageHyperlink link, int subItemIndex) {
		CoreItem coreItem = null;
		link.setCursor(busyCursor);
		currentItem = (ViewItem) link.getData();
		coreItem = (CoreItem) currentItem;

		try {
			if (coreItem != null) {
				if (coreItem.runSubItemExecutable(getManager(), subItemIndex) == ViewItem.VIEWITEM_ADVANCE && !coreItem.hasConfirm(subItemIndex)) {
					ArrayList l = coreItem.getListOfSubItemCompositeHolders();
					SubItemCompositeHolder s = (SubItemCompositeHolder) l.get(subItemIndex);
					s.getStartButton().setImage(CheatSheetPlugin.getPlugin().getImage(ICheatSheetResource.CHEATSHEET_ITEM_BUTTON_RESTART));
					s.getStartButton().setToolTipText(Messages.RESTART_TASK_TOOLTIP);
					advanceSubItem(link, true, subItemIndex);
					saveCurrentSheet();
				}
			}
		} catch (RuntimeException e) {
			IStatus status = new Status(IStatus.ERROR, ICheatSheetResource.CHEAT_SHEET_PLUGIN_ID, IStatus.OK, Messages.ERROR_RUNNING_ACTION, e);
			CheatSheetPlugin.getPlugin().getLog().log(status);
			org.eclipse.jface.dialogs.ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), null, null, status);
		} finally {
			link.setCursor(null);
		}
	}

	/*package*/ void saveCurrentSheet() {
		if(currentID != null) {
			if (currentPage instanceof CheatSheetPage) {
				boolean expandRestoreActionState = false;
				if(expandRestoreAction != null)
					expandRestoreActionState = expandRestoreAction.isCollapsed();			
				saveHelper.saveState(currentItemNum, viewItemList, expandRestoreActionState, expandRestoreList, currentID, restorePath, getManager());
			} else if (currentPage instanceof CompositeCheatSheetPage) {
				((CompositeCheatSheetPage)currentPage).saveState();
			}
		}
	}

	/*package*/ void setContent(CheatSheetElement element) {
		CheatSheetStopWatch.startStopWatch("CheatSheetViewer.setContent(CheatSheetElement element)"); //$NON-NLS-1$

		if (element != null && element.equals(contentElement))
			return;
		
		// Cleanup previous contents
		internalDispose();

		// Set the current content to new content
		contentElement = element;

		currentID = null;
		contentURL = null;
		if (element != null) {
			currentID = element.getID();
			restorePath = element.getRestorePath();
	
			Bundle bundle = null;
			if(element != null && element.getConfigurationElement() != null)
				try{
					String pluginId = element.getConfigurationElement().getNamespace();
					bundle = Platform.getBundle(pluginId);
				} catch (Exception e) {
					// do nothing
				}
			if (bundle != null) {
				contentURL = Platform.find(bundle, new Path(element.getContentFile()));
			}
	
			if (contentURL == null) {
				try {
					contentURL = new URL(element.getContentFile());
				} catch (MalformedURLException mue) {
				}
			}
		}

		CheatSheetStopWatch.printLapTime("CheatSheetViewer.setContent(CheatSheetElement element)", "Time in CheatSheetViewer.setContent() before initCheatSheetView() call: "); //$NON-NLS-1$ //$NON-NLS-2$
		// Initialize the view with the new contents
		if (control != null) {
			initSavePath();
			initCheatSheetView();
		}
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.setContent(CheatSheetElement element)", "Time in CheatSheetViewer.setContent() after initCheatSheetView() call: "); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/*package*/ void setExpandRestoreAction(CheatSheetExpandRestoreAction action) {
		expandRestoreAction = action;
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		//need this to have current item selected. (Assumes that when you reactivate the view you will work with current item.)
		if (currentItem != null)
			currentItem.getMainItemComposite().setFocus();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.cheatsheets.ICheatSheetViewer#setInput(java.lang.String)
	 */
	public void setInput(String id) {
		CheatSheetStopWatch.startStopWatch("CheatSheetViewer.setInput(String id)"); //$NON-NLS-1$

		CheatSheetElement element = null;

		if(id == null) {
			nullCheatSheetId = true;
		} else {
			nullCheatSheetId = false;

			element = CheatSheetRegistryReader.getInstance().findCheatSheet(id);
			if(element == null) {
				String message = NLS.bind(Messages.ERROR_INVALID_CHEATSHEET_ID, (new Object[] {id}));
				IStatus status = new Status(IStatus.ERROR, ICheatSheetResource.CHEAT_SHEET_PLUGIN_ID, IStatus.OK, message, null);
				CheatSheetPlugin.getPlugin().getLog().log(status);
				invalidCheatSheetId = true;
			} else {
				invalidCheatSheetId = false;
			}
		}

		CheatSheetStopWatch.printLapTime("CheatSheetViewer.setInput(String id)", "Time in CheatSheetViewer.setInput(String id) before setContent() call: "); //$NON-NLS-1$ //$NON-NLS-2$
		setContent(element);
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.setInput(String id)", "Time in CheatSheetViewer.setInput(String id) after setContent() call: "); //$NON-NLS-1$ //$NON-NLS-2$

		// Update most recently used cheat sheets list.
		CheatSheetPlugin.getPlugin().getCheatSheetHistory().add(element);
		CheatSheetStopWatch.printLapTime("CheatSheetViewer.setInput(String id)", "Time in CheatSheetViewer.setInput(String id) after getCheatSheetHistory() call: "); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.cheatsheets.ICheatSheetViewer#setInput(java.lang.String, java.lang.String, java.net.URL)
	 */
	public void setInput(String id, String name, URL url) {
		if (id == null || name == null || url == null) {
			throw new IllegalArgumentException();
		}
		CheatSheetElement element = new CheatSheetElement(name);
		element.setID(id);
		element.setContentFile(url.toString());

		nullCheatSheetId = false;
		invalidCheatSheetId = false;
		setContent(element);
	}
	
    /*package*/ void toggleExpandRestore() {
		if(expandRestoreAction == null)
			return;

		if (expandRestoreAction.isCollapsed()) {
			restoreExpandStates();
			expandRestoreAction.setCollapsed(false);
			saveCurrentSheet();
		} else {
			collapseAllButCurrent(true);
			expandRestoreAction.setCollapsed(true);
			saveCurrentSheet();
		}

	}

	public Action getCopyAction() {
		return copyAction;
	}

	public void setCopyAction(Action copyAction) {
		this.copyAction = copyAction;
	}
	
	public void copy() {
		if (currentItem!=null)
			currentItem.copy();
	}
	
	/**
	 * Sets the location to be used for saving the state of the 
	 * cheatsheet which is currently displayed. 
	 * @param path The path of a directory on the file system or null
	 * to cause the default location to be used
	 */
	public void setSavePath(IPath path) {
		saveHelper.setSavePath(path, currentID, true);
		saveCurrentSheet();
	}

	/**
	 * Sets the location where the state will be stored for the next cheatsheet
	 * which is launched. Does not affect the state location for the currently
	 * displayed cheatsheet.
	 * @param savePath
	 */
	public void setNextSavePath(IPath savePath) {
	    nextSavePath = savePath;		
	}
	
	private void initSavePath() {
		savePath = nextSavePath;
		nextSavePath = null;
	}

	public void addListener(CheatSheetListener listener) {
		getManager().addListener(listener);		
	}

	public int contributeToViewMenu(Menu menu, int index) {
		if (currentPage instanceof IMenuContributor) {
			return ((IMenuContributor)currentPage).contributeToViewMenu(menu, index);
		}
		return index;
	}
	
	public void restart() {
		resetItemState();
		currentItemNum = 0;
		collapseAllButCurrent(false);
		showIntroItem();		
	}

}