/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.e4.core.contexts;

import org.eclipse.e4.core.di.IDisposable;

/**
 * A context is used to isolate application code from its dependencies on an application framework
 * or container. This helps avoid building in dependencies on a specific framework that inhibit
 * reuse of the application code. Fundamentally a context supplies values (either data objects or
 * services), and allows values to be set.
 * <p>
 * While a context appears superficially to be a Map, it may in fact compute values for requested
 * keys dynamically rather than simply retrieving a stored value.
 * </p>
 * <p>
 * Contexts may have a parent context, and may delegate lookup of a value to their parent. Whether a
 * value is computed or stored in this context or a parent context is an implementation detail that
 * clients need not be concerned with. The content of parent contexts cannot be modified by a child
 * context.
 * </p>
 * <p>
 * Like maps, values are stored in the context based on keys. Two types of keys can be used: strings 
 * and classes. When classes are used to access objects in the context, keys are calculated based on 
 * the class name, so the value stored for the class {@link java.lang.String} can be retrieved 
 * using the key value of "java.lang.String". 
 * </p>
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface IEclipseContext extends IDisposable {

	/**
	 * Returns whether this context or a parent has a value stored for the given name.
	 * @param name the name being queried
	 * @return <code>true</code> if this context has a value for the given name, and
	 *         <code>false</code> otherwise.
	 */
	public boolean containsKey(String name);

	/**
	 * Returns whether this context or a parent has a value stored for the given class.
	 * @param clazz the class being queried
	 * @return <code>true</code> if this context has a value for the given class, and
	 *         <code>false</code> otherwise.
	 * @see #containsKey(String)
	 */
	public boolean containsKey(Class<?> clazz);

	/**
	 * Returns the context value associated with the given name. Returns <code>null</code> if no
	 * such value is defined or computable by this context, or if the assigned value is
	 * <code>null</code>.
	 * <p>
	 * If the value associated with this name is an {@link ContextFunction}, this method will
	 * evaluate {@link ContextFunction#compute(IEclipseContext)}.
	 * </p>
	 * @param name the name of the value to return
	 * @return an object corresponding to the given name, or <code>null</code>
	 */
	public Object get(String name);

	/**
	 * Returns the context value associated with the given class.
	 * @param clazz the class that needs to be found in the context
	 * @return an object corresponding to the given class, or <code>null</code>
	 * @see #get(String)
	 */
	public <T> T get(Class<T> clazz);

	/**
	 * Returns the context value associated with the given name in this context, or <code>null</code> if 
	 * no such value is defined in this context.
	 * <p>
	 * This method does not search for the value on other elements on the context tree.
	 * </p>
	 * <p>
	 * If the value associated with this name is an {@link ContextFunction}, this method will
	 * evaluate {@link ContextFunction#compute(IEclipseContext)}.
	 * </p>
	 * @param name the name of the value to return
	 * @return an object corresponding to the given name, or <code>null</code>
	 */
	public Object getLocal(String name);

	/**
	 * Returns the context value associated with the given class in this context, or <code>null</code> if 
	 * no such value is defined in this context.
	 * <p>
	 * This method does not search for the value on other elements on the context tree.
	 * </p>
	 * @param clazz The class of the value to return
	 * @return An object corresponding to the given class, or <code>null</code>
	 * @see #getLocal(String)
	 */
	public <T> T getLocal(Class<T> clazz);

	/**
	 * Removes the given name and any corresponding value from this context.
	 * <p>
	 * Removal can never affect a parent context, so it is possible that a subsequent call to
	 * {@link #get(String)} with the same name will return a non-null result, due to a value being
	 * stored in a parent context.
	 * </p>
	 * @param name the name to remove
	 */
	public void remove(String name);

	/**
	 * Removes the value for the given class from this context.
	 * @param clazz The class to remove
	 * @see #remove(String)
	 */
	public void remove(Class<?> clazz);

	/**
	 * Executes a runnable within this context. If the runnable accesses any values in this context
	 * during its execution, the runnable will be executed again after any of those values change.
	 * Only the context values accessed during the most recent invocation of the runnable are
	 * tracked.
	 * <p>
	 * This method allows a client to keep some external state synchronized with one or more values
	 * in this context. For this synchronization to work correctly, the runnable should perform its
	 * synchronization purely as a function of values in the context. If the runnable relies on
	 * mutable values that are external to the context, it will not be updated correctly when that
	 * external state changes.
	 * </p>
	 * <p>
	 * The runnable is not guaranteed to be executed synchronously with the context change that
	 * triggers its execution. Thus the context state at the time of the runnable's execution may
	 * not match the context state at the time of the relevant context change.
	 * </p>
	 * <p>
	 * The runnable does not need to be explicitly unregistered from this context when it is no
	 * longer interested in tracking changes. If a subsequent invocation of this runnable does not
	 * access any values in this context, it is automatically unregistered from change tracking on
	 * this context. Thus a provided runnable should be implemented to return immediately when
	 * change tracking is no longer needed.
	 * </p>
	 * 
	 * @param runnable
	 *            The runnable to execute and register for change tracking
	 * @see RunAndTrack
	 */
	public void runAndTrack(final RunAndTrack runnable);

	/**
	 * Sets a value to be associated with a given name in this context. The value may be an
	 * arbitrary object, or it may be an {@link ContextFunction}. In the case of a function,
	 * subsequent invocations of {@link #get(String)} with the same name will invoke
	 * {@link ContextFunction#compute(IEclipseContext)} to obtain the value. The value
	 * may be <code>null</code>.
	 * <p>
	 * Removal can never affect a parent context, so it is possible that a subsequent call to
	 * {@link #get(String)} with the same name will return a non-null result, due to a value being
	 * stored in a parent context.
	 * </p>
	 * @param name the name to store a value for
	 * @param value the value to be stored, or a {@link ContextFunction} that can return 
	 * the stored value.
	 */
	public void set(String name, Object value);

	/**
	 * Sets a value to be associated with a given class in this context. 
	 * @param clazz The class to store a value for
	 * @param value The value to be stored
	 * @see #set(String, Object)
	 */
	public <T> void set(Class<T> clazz, T value);

	/**
	 * Modifies the value to be associated with the given name.
	 * <p>
	 * The value has to be declared as modifiable by the original context before this method can be
	 * used. If the variable with this name has not been declared as modifiable, an
	 * {@link IllegalArgumentException} will be thrown.
	 * </p>
	 * <p>
	 * The value is modified in the context in which it has been previously set. If none of the
	 * contexts on the parent chain have a value set for the name, the value will be set in this
	 * context.
	 * </p>
	 * @param name the name to store a value for
	 * @param value the value to be stored, or a {@link ContextFunction} that can return the stored value.
	 * @throws IllegalArgumentException if the variable has not been declared as modifiable
	 */
	public void modify(String name, Object value);

	/**
	 * Modifies the value to be associated with the given class.
	 * @param clazz The class to store a value for
	 * @param value The value to be stored
	 * @throws IllegalArgumentException if the variable has not been declared as modifiable
	 * @see #modify(String, Object)
	 */
	public <T> void modify(Class<T> clazz, T value);

	/**
	 * Declares the named value as modifiable by descendants of this context. If the value does not
	 * exist in this context, a <code>null</code> value added for the name.
	 * @param name the name to be declared as modifiable by descendants
	 */
	public void declareModifiable(String name);

	/**
	 * Declares the value for the class as modifiable by descendants of this context. 
	 * If the value does not exist in this context, a <code>null</code> value added for the class.
	 * @param clazz the class to be declared as modifiable by descendants
	 * @see #declareModifiable(String)
	 */
	public void declareModifiable(Class<?> clazz);

	/**
	 * Process waiting updates for listeners that support batch notifications.
	 */
	public void processWaiting();

	/**
	 * Creates a new context using this context as a parent.
	 * @return a new child context
	 */
	public IEclipseContext createChild();

	/**
	 * Creates a new named context using this context as a parent.
	 * @param name the name to identify this context
	 * @return a new child context
	 */
	public IEclipseContext createChild(String name);

	/**
	 * Returns parent context, or <code>null</code> if there is no parent context.
	 * @return the parent context, or <code>null</code>
	 */
	public IEclipseContext getParent();

	/**
	 * Sets parent context. Pass in <code>null</code> to indicate that this context has
	 * no parent.
	 * @param parentContext the new parent context, or <code>null</code> to indicate
	 * that this context has no parent
	 */
	public void setParent(IEclipseContext parentContext);

	/**
	 * Marks this context as active.
	 * @param activateBranch <code>true</code> to also activate parent contexts
	 */
	public void activate(boolean activateBranch);

	/**
	 * Marks this context as inactive.
	 */
	public void deactivate();

	/**
	 * Returns leaf active child for this context. May return the context itself
	 * if there are no active children.
	 * @return active context
	 */
	public IEclipseContext getActive();
}
