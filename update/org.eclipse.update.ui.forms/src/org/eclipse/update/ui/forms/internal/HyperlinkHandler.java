package org.eclipse.update.ui.forms.internal;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.util.Enumeration;
import java.util.Hashtable;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public class HyperlinkHandler
	extends HyperlinkSettings
	implements MouseListener, MouseTrackListener, SelectionListener, PaintListener {
	private Hashtable hyperlinkListeners;
	private Control lastActivated;
	private Control lastEntered;

	public HyperlinkHandler() {
		hyperlinkListeners = new Hashtable();
	}

	public Control getLastLink() {
		return lastActivated;
	}

	public void mouseDoubleClick(MouseEvent e) {
	}

	public void mouseDown(MouseEvent e) {
		if (e.button == 1)
			return;
		lastActivated = (Control) e.widget;
	}
	public void mouseEnter(MouseEvent e) {
		Control control = (Control) e.widget;
		linkEntered(control);
	}
	public void mouseExit(MouseEvent e) {
		Control control = (Control) e.widget;
		linkExited(control);
	}
	public void mouseHover(MouseEvent e) {
	}

	public void mouseUp(MouseEvent e) {
		if (e.button != 1)
			return;
		Control linkControl = (Control) e.widget;
		Point size = linkControl.getSize();
		// Filter out mouse up events outside
		// the link. This can happen when mouse is
		// clicked, dragged outside the link, then
		// released.
		if (e.x < 0) return;
		if (e.y < 0) return;
		if (e.x >= size.x) return;
		if (e.y >= size.y) return;
		linkActivated(linkControl);
	}

	public void widgetDefaultSelected(SelectionEvent e) {
		Control link = (Control) e.widget;
		linkActivated(link);
	}

	public void widgetSelected(SelectionEvent e) {
		Control link = (Control) e.widget;
		SelectableFormLabel l = (SelectableFormLabel) link;
		if (l.getSelection())
			linkEntered(link);
		else
			linkExited(link);
	}

	private void linkActivated(Control link) {
		IHyperlinkListener action = (IHyperlinkListener) hyperlinkListeners.get(link);
		if (action != null) {
			link.setCursor(busyCursor);
			action.linkActivated(link);
			if (!link.isDisposed())
				link.setCursor(isHyperlinkCursorUsed() ? hyperlinkCursor : null);
		}
	}

	private void linkEntered(Control link) {
		if (lastEntered != null && lastEntered instanceof SelectableFormLabel) {
			SelectableFormLabel fl = (SelectableFormLabel) lastEntered;
			linkExited(fl);
		}
		if (isHyperlinkCursorUsed())
			link.setCursor(hyperlinkCursor);
		if (activeBackground != null)
			link.setBackground(activeBackground);
		if (activeForeground != null)
			link.setForeground(activeForeground);
		if (hyperlinkUnderlineMode == UNDERLINE_ROLLOVER)
			underline(link, true);

		IHyperlinkListener action = (IHyperlinkListener) hyperlinkListeners.get(link);
		if (action != null)
			action.linkEntered(link);
		lastEntered = link;
	}

	private void linkExited(Control link) {
		if (isHyperlinkCursorUsed())
			link.setCursor(null);
		if (hyperlinkUnderlineMode == UNDERLINE_ROLLOVER)
			underline(link, false);
		if (background != null)
			link.setBackground(background);
		if (foreground != null)
			link.setForeground(foreground);
		IHyperlinkListener action = (IHyperlinkListener) hyperlinkListeners.get(link);
		if (action != null)
			action.linkExited(link);
		if (lastEntered == link)
			lastEntered = null;
	}

	public void paintControl(PaintEvent e) {
		Control label = (Control) e.widget;
		if (hyperlinkUnderlineMode == UNDERLINE_ALWAYS)
			HyperlinkHandler.underline(label, true);
	}

	public void registerHyperlink(Control control, IHyperlinkListener listener) {
		if (background != null)
			control.setBackground(background);
		if (foreground != null)
			control.setForeground(foreground);
		control.addMouseListener(this);
		control.addMouseTrackListener(this);

		if (hyperlinkUnderlineMode == UNDERLINE_ALWAYS && control instanceof Label)
			control.addPaintListener(this);
		if (control instanceof SelectableFormLabel) {
			SelectableFormLabel sl = (SelectableFormLabel) control;
			sl.addSelectionListener(this);
			if (hyperlinkUnderlineMode == UNDERLINE_ALWAYS)
				sl.setUnderlined(true);
		}
		hyperlinkListeners.put(control, listener);
		removeDisposedLinks();
	}
	private void removeDisposedLinks() {
		for (Enumeration keys = hyperlinkListeners.keys(); keys.hasMoreElements();) {
			Control control = (Control) keys.nextElement();
			if (control.isDisposed()) {
				hyperlinkListeners.remove(control);
			}
		}
	}

	public void reset() {
		hyperlinkListeners.clear();
	}

	public static void underline(Control control, boolean inside) {
		if (control instanceof SelectableFormLabel) {
			SelectableFormLabel l = (SelectableFormLabel) control;
			l.setUnderlined(inside);
			l.redraw();
			return;
		}
		if (!(control instanceof Label))
			return;
		Composite parent = control.getParent();
		Rectangle bounds = control.getBounds();
		GC gc = new GC(parent);
		Color color = inside ? control.getForeground() : control.getBackground();
		gc.setForeground(color);
		int y = bounds.y + bounds.height;
		gc.drawLine(bounds.x, y, bounds.x + bounds.width, y);
		gc.dispose();
	}

}