/*
 * @(#)AbstractApplicationAction.java  1.0  June 15, 2006
 *
 * Copyright (c) 1996-2006 by the original authors of JHotDraw
 * and all its contributors ("JHotDraw.org")
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * JHotDraw.org ("Confidential Information"). You shall not disclose
 * such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with
 * JHotDraw.org.
 */

package org.jhotdraw.app.action;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.jhotdraw.app.Application;
/**
 * An Action that acts on an <code>Application</code> object.
 * If the Application object is disabled, the AbstractApplicationAction is disabled
 * as well.
 *
 * @author Werner Randelshofer.
 * @version 1.0 June 15, 2006 Created.
 * @see org.jhotdraw.app.Application
 */
public abstract class AbstractApplicationAction extends AbstractAction {
    private Application app;
    
    private PropertyChangeListener applicationListener;
    
    /** Creates a new instance. */
    public AbstractApplicationAction(Application app) {
        this.app = app;
        installApplicationListeners(app);
        updateApplicationEnabled();
    }
    
    /**
     * Installs listeners on the application object.
     */
    protected void installApplicationListeners(Application app) {
        if (applicationListener == null) {
            applicationListener = createApplicationListener();
        }
        app.addPropertyChangeListener(applicationListener);
    }
    
    /**
     * Installs listeners on the application object.
     */
    protected void uninstallApplicationListeners(Application app) {
        app.removePropertyChangeListener(applicationListener);
    }
    
    private PropertyChangeListener createApplicationListener() {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName() == "enabled") { // Strings get interned
                    updateApplicationEnabled();
                }
            }
        };
    }
    
    public Application getApplication() {
        return app;
    }
    
    /**
     * Updates the enabled state of this action depending on the new enabled
     * state of the application.
     */
    protected void updateApplicationEnabled() {
        firePropertyChange("enabled",
                Boolean.valueOf(! isEnabled()),
                Boolean.valueOf(isEnabled())
                );
    }
    
    /**
     * Returns true if the action is enabled.
     * The enabled state of the action depends on the state that has been set
     * using setEnabled() and on the enabled state of the application.
     *
     * @return true if the action is enabled, false otherwise
     * @see Action#isEnabled
     */
    @Override public boolean isEnabled() {
        return app.isEnabled() && enabled;
    }
    
    /**
     * Enables or disables the action. The enabled state of the action
     * depends on the value that is set here and on the enabled state of
     * the application.
     *
     * @param newValue  true to enable the action, false to
     *                  disable it
     * @see Action#setEnabled
     */
    @Override public void setEnabled(boolean newValue) {
        boolean oldValue = this.enabled;
        this.enabled = newValue;
        
        firePropertyChange("enabled",
                Boolean.valueOf(oldValue && app.isEnabled()),
                Boolean.valueOf(newValue && app.isEnabled())
                );
    }
}
