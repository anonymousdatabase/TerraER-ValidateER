/*
 * @(#)DefaultDrawingView.java  3.5  2007-04-13
 *
 * Copyright (c) 1996-2007 by the original authors of JHotDraw
 * and all its contributors ("JHotDraw.org")
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * JHotDraw.org ("Confidential Information"). You shall not disclose
 * such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with
 * JHotDraw.org.
 */

package org.jhotdraw.draw;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JViewport;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.jhotdraw.app.EditableComponent;
import org.jhotdraw.app.action.ConnectionRecommendationAction;
import org.jhotdraw.geom.Dimension2DDouble;
import org.jhotdraw.util.ResourceBundleUtil;
import org.jhotdraw.util.ReversedList;
/**
 * The DefaultDrawingView is suited for viewing drawings with a small number
 * of Figures.
 *
 * FIXME - DefaultDrawingView should not implement DrawingListener and
 * HandleListener. It should use internal classes for this.
 *
 *
 * @author Werner Randelshofer
 * @version 3.5 2007-04-13 Implement clipboard functions using TransferHandler.
 * <br>3.4 2007-04-09 Visualizes the canvas size of a Drawing by a filled
 * white rectangle on the background.
 * <br>3.3 2007-01-23 Only repaint handles on focus gained/lost.
 * <br>3.2 2006-12-26 Rewrote storage and clipboard support.
 * <br>3.1 2006-12-17 Added printing support.
 * <br>3.0.2 2006-07-03 Constrainer must be a bound property.
 * <br>3.0.1 2006-06-11 Draw handles when this DrawingView is the focused
 * drawing view of the DrawingEditor.
 * <br>3.0 2006-02-17 Reworked to support multiple drawing views in a
 * DrawingEditor.
 * <br>2.0 2006-01-14 Changed to support double precision coordinates.
 * <br>1.0 2003-12-01 Derived from JHotDraw 5.4b1.
 */
public class DefaultDrawingView
        extends JComponent
        implements DrawingView, DrawingListener, HandleListener, EditableComponent {
    /**
     * Set this to true to turn on debugging output on System.out.
     */
    private final static boolean DEBUG = false;
    
    private Drawing drawing;
    private Set<Figure> dirtyFigures = new HashSet<Figure>();
    private Set<Figure> selectedFigures = new HashSet<Figure>();
    private int rainbow = 0;
    private LinkedList<Handle> selectionHandles = new LinkedList<Handle>();
    
    private Handle secondaryHandleOwner;
    private LinkedList<Handle> secondaryHandles = new LinkedList<Handle>();
    private boolean handlesAreValid = true;
    private Dimension cachedPreferredSize;
    private double scaleFactor = 1;
    private Point2D.Double translate = new Point2D.Double(0,0);
    private int detailLevel;
    private DrawingEditor editor;
    private Constrainer constrainer = new GridConstrainer(1,1);
    private JLabel emptyDrawingLabel;
    private FigureListener handleInvalidator = new FigureAdapter() {
        @Override public void figureHandlesChanged(FigureEvent e) {
            invalidateHandles();
        }
    };
    private Rectangle2D.Double cachedDrawingArea;
    
    /** Creates new instance. */
    public DefaultDrawingView() {
        initComponents();
        setToolTipText("dummy"); // Set a dummy tool tip text to turn tooltips on
        setFocusable(true);
        addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                repaintHandles();
            }
            public void focusLost(FocusEvent e) {
                repaintHandles();
            }
        });
        setTransferHandler(new DefaultDrawingViewTransferHandler());
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        buttonGroup1 = new javax.swing.ButtonGroup();

        setLayout(null);

        setBackground(new java.awt.Color(255, 255, 255));
    }//GEN-END:initComponents
    
    public Drawing getDrawing() {
        return drawing;
    }
    
    public String getToolTipText(MouseEvent evt) {
        Handle handle = findHandle(evt.getPoint());
        if (handle != null) {
            return handle.getToolTipText(evt.getPoint());
        }
        Figure figure = findFigure(evt.getPoint());
        if (figure != null) {
        	//tooltip to recomendation system
//        	if (figure instanceof LineConnectionFigure){
//        		if (figure.getAttribute(AttributeKeys.TEXT_COLOR).equals(Color.red)) {
//        			ConnectionRecommendationAction.getInstance(null).actionFromConnection((LineConnectionFigure)figure);
//        		}
//        	}
            return figure.getToolTipText(viewToDrawing(evt.getPoint()));
        }
        return null;
    }
    
    public void setEmptyDrawingMessage(String newValue) {
        String oldValue = (emptyDrawingLabel == null) ? null : emptyDrawingLabel.getText();
        if (newValue == null) {
            emptyDrawingLabel = null;
        } else {
            emptyDrawingLabel = new JLabel(newValue);
            emptyDrawingLabel.setHorizontalAlignment(JLabel.CENTER);
        }
        firePropertyChange("emptyDrawingMessage", oldValue, newValue);
        repaint();
    }
    public String getEmptyDrawingMessage() {
        return (emptyDrawingLabel == null) ? null : emptyDrawingLabel.getText();
    }
    
    /**
     * Paints the drawing view.
     * Uses rendering hints for fast painting. Paints the background, the
     * grid, the drawing, the handles and the current tool.
     */
    public void paintComponent(Graphics gr) {
        
        Graphics2D g = (Graphics2D) gr;
        
        // Set rendering hints for speed
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, (Options.isFractionalMetrics()) ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, (Options.isTextAntialiased()) ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        
        drawBackground(g);
        drawGrid(g);
        drawDrawing(g);
        
        drawHandles(g);
        drawTool(g);
    }
    /**
     * Prints the drawing view.
     * Uses high quality rendering hints for printing. Only prints the drawing.
     * Doesn't print the background, the grid, the handles and the tool.
     */
    public void printComponent(Graphics gr) {
        
        Graphics2D g = (Graphics2D) gr;
        
        // Set rendering hints for quality
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        //g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, (Options.isFractionalMetrics()) ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, (Options.isTextAntialiased()) ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        
        
        //drawBackground(g);
        //drawGrid(g);
        drawDrawing(g);
        
        //drawHandles(g);
        //drawTool(g);
    }
    
    protected void drawBackground(Graphics2D g) {
        // Position of the zero coordinate point on the view
        int x = (int) (-translate.x * scaleFactor);
        int y = (int) (-translate.y * scaleFactor);
        
        int w = getWidth();
        int h = getHeight();
        
        g.setColor(getBackground());
        g.fillRect(x, y, w - x, h - y);
        
        // Draw a gray background for the area which is at
        // negative view coordinates.
        if (y > 0) {
            g.setColor(new Color(0xf0f0f0));
            g.fillRect(0, 0, w, y);
        }
        if (x > 0) {
            g.setColor(new Color(0xf0f0f0));
            g.fillRect(0, y, x, h - y);
        }
        
        if (getDrawing() != null) {
            Dimension2DDouble canvasSize = getDrawing().getCanvasSize();
            if (canvasSize != null) {
                Point lowerRight = drawingToView(
                        new Point2D.Double(canvasSize.width, canvasSize.height)
                        );
                if (lowerRight.x < w) {
                    g.setColor(new Color(0xf0f0f0));
                    g.fillRect(lowerRight.x, y, w - lowerRight.x, h - y);
                }
                if (lowerRight.y < h) {
                    g.setColor(new Color(0xf0f0f0));
                    g.fillRect(x, lowerRight.y, w - x, h - lowerRight.y);
                }
            }
        }
    }
    
    protected void drawGrid(Graphics2D g) {
        constrainer.draw(g, this);
    }
    
    protected void drawDrawing(Graphics2D gr) {
        /* Fill background with alternating colors to debug clipping
            rainbow = (rainbow + 10) % 360;
            gr.setColor(
            new Color(Color.HSBtoRGB((float) (rainbow / 360f), 0.3f, 1.0f))
            );
            gr.fill(gr.getClipBounds());
         */
        
        if (drawing != null) {
            if (drawing.getFigureCount() == 0 && emptyDrawingLabel != null) {
                emptyDrawingLabel.setBounds(0, 0, getWidth(), getHeight());
                emptyDrawingLabel.paint(gr);
            } else {
                Graphics2D g = (Graphics2D) gr.create();
                AffineTransform tx = g.getTransform();
                tx.translate(-translate.x * scaleFactor, -translate.y * scaleFactor);
                tx.scale(scaleFactor, scaleFactor);
                g.setTransform(tx);
                
                drawing.setFontRenderContext(g.getFontRenderContext());
                drawing.draw(g);
                
                g.dispose();
            }
        }
    }
    
    protected void drawHandles(java.awt.Graphics2D g) {
        if (editor != null && editor.getActiveView() == this) {
            validateHandles();
            for (Handle h : getSelectionHandles()) {
                h.draw(g);
            }
            for (Handle h : getSecondaryHandles()) {
                h.draw(g);
            }
        }
    }
    
    protected void drawTool(Graphics2D g) {
        if (editor != null && editor.getActiveView() == this && editor.getTool() != null) {
            editor.getTool().draw(g);
        }
    }
    
    public void setDrawing(Drawing d) {
        if (this.drawing != null) {
            this.drawing.removeDrawingListener(this);
            clearSelection();
        }
        this.drawing = d;
        if (this.drawing != null) {
            this.drawing.addDrawingListener(this);
        }
        invalidateDimension();
        invalidate();
        if (getParent() != null) {
            getParent().validate();
            if (getParent() instanceof JViewport) {
                JViewport vp = (JViewport) getParent();
                
                Rectangle2D.Double r = getDrawingArea();
                vp.setViewPosition(drawingToView(new Point2D.Double(Math.min(0, -r.x), Math.min(0, -r.y))));
            }
        }
        repaint();
    }
    
    protected void repaint(Rectangle2D.Double r) {
        Rectangle vr = drawingToView(r);
        vr.grow(1, 1);
        repaint(vr);
    }
    
    public void areaInvalidated(DrawingEvent evt) {
        repaint(evt.getInvalidatedArea());
        invalidateDimension();
    }
    public void areaInvalidated(HandleEvent evt) {
        repaint(evt.getInvalidatedArea());
        invalidateDimension();
    }
    public void figureAdded(DrawingEvent evt) {
        // Repaint the whole drawing to remove the message label
        if (evt.getDrawing().getFigureCount() == 1) {
            repaint();
        } else {
            repaint(evt.getInvalidatedArea());
        }
        invalidateDimension();
    }
    public void figureRemoved(DrawingEvent evt) {
        // Repaint the whole drawing to draw the message label
        if (evt.getDrawing().getFigureCount() == 0) {
            repaint();
        } else {
            repaint(evt.getInvalidatedArea());
        }
        removeFromSelection(evt.getFigure());
        invalidateDimension();
    }
    public void invalidate() {
        invalidateDimension();
        super.invalidate();
    }
    
    /**
     * Adds a figure to the current selection.
     */
    public void addToSelection(Figure figure) {
        if (DEBUG) System.out.println("DefaultDrawingView"+".addToSelection("+figure+")");
        Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
        if (selectedFigures.add(figure)) {
            figure.addFigureListener(handleInvalidator);
            Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
            invalidateHandles();
            fireSelectionChanged(oldSelection, newSelection);
            repaint();
        }
    }
    /**
     * Adds a collection of figures to the current selection.
     */
    public void addToSelection(Collection<Figure> figures) {
        Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
        if (selectedFigures.addAll(figures)) {
            Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
            for (Figure f : figures) {
                f.addFigureListener(handleInvalidator);
            }
            invalidateHandles();
            fireSelectionChanged(oldSelection, newSelection);
            repaint();
        }
    }
    
    /**
     * Removes a figure from the selection.
     */
    public void removeFromSelection(Figure figure) {
        Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
        if (selectedFigures.remove(figure)) {
            Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
            invalidateHandles();
            figure.removeFigureListener(handleInvalidator);
            fireSelectionChanged(oldSelection, newSelection);
            repaint();
        }
    }
    
    /**
     * If a figure isn't selected it is added to the selection.
     * Otherwise it is removed from the selection.
     */
    public void toggleSelection(Figure figure) {
        Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
        if (selectedFigures.contains(figure)) {
            selectedFigures.remove(figure);
        } else {
            selectedFigures.add(figure);
        }
        Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
        fireSelectionChanged(oldSelection, newSelection);
        invalidateHandles();
        repaint();
    }
    
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        setCursor(Cursor.getPredefinedCursor(b ? Cursor.DEFAULT_CURSOR : Cursor.WAIT_CURSOR));
    }
    
    
    /**
     * Selects all figures.
     */
    public void selectAll() {
        Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
        selectedFigures.clear();
        selectedFigures.addAll(drawing.getFigures());
        Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
        invalidateHandles();
        fireSelectionChanged(oldSelection, newSelection);
        repaint();
    }
    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        if (getSelectionCount()  > 0) {
            Set<Figure> oldSelection = new HashSet<Figure>(selectedFigures);
            selectedFigures.clear();
            Set<Figure> newSelection = new HashSet<Figure>(selectedFigures);
            invalidateHandles();
            fireSelectionChanged(oldSelection, newSelection);
        }
        repaint();
    }
    /**
     * Test whether a given figure is selected.
     */
    public boolean isFigureSelected(Figure checkFigure) {
        return selectedFigures.contains(checkFigure);
    }
    
    /**
     * Gets the current selection as a FigureSelection. A FigureSelection
     * can be cut, copied, pasted.
     */
    public Set<Figure> getSelectedFigures() {
        return Collections.unmodifiableSet(selectedFigures);
    }
    /**
     * Gets the number of selected figures.
     */
    public int getSelectionCount() {
        return selectedFigures.size();
    }
    
    /**
     * Gets the currently active selection handles.
     */
    private java.util.List<Handle> getSelectionHandles() {
        validateHandles();
        return Collections.unmodifiableList(selectionHandles);
    }
    /**
     * Gets the currently active secondary handles.
     */
    private java.util.List<Handle> getSecondaryHandles() {
        validateHandles();
        return Collections.unmodifiableList(secondaryHandles);
    }
    
    /**
     * Invalidates the handles.
     */
    private void invalidateHandles() {
        if (handlesAreValid) {
            handlesAreValid = false;
            
            Rectangle invalidatedArea = null;
            for (Handle handle : selectionHandles) {
                handle.removeHandleListener(this);
                if (invalidatedArea == null) {
                    invalidatedArea = handle.getDrawingArea();
                } else {
                    invalidatedArea.add(handle.getDrawingArea());
                }
                handle.dispose();
            }
            selectionHandles.clear();
            secondaryHandles.clear();
            
            switch (selectedFigures.size()) {
                case 0 :
                    if (invalidatedArea != null) {
                        repaint(invalidatedArea);
                    }
                    break;
                case 1 :
                    if (invalidatedArea != null) {
                        repaint(invalidatedArea);
                    }
                    //validateHandles();
                    break;
                default :
                    repaint();
                    break;
            }
        }
    }
    /**
     * Validates the handles.
     */
    private void validateHandles() {
        if (! handlesAreValid) {
            handlesAreValid = true;
            
            Rectangle invalidatedArea = null;
            int level = detailLevel;
            do {
                for (Figure figure : getSelectedFigures()) {
                    for (Handle handle : figure.createHandles(level)) {
                        handle.setView(this);
                        selectionHandles.add(handle);
                        handle.addHandleListener(this);
                        if (invalidatedArea == null) {
                            invalidatedArea = handle.getDrawingArea();
                        } else {
                            invalidatedArea.add(handle.getDrawingArea());
                        }
                    }
                }
            } while (level-- > 0 && selectionHandles.size() == 0);
            detailLevel = level + 1;
            
            if (invalidatedArea != null) {
                repaint(invalidatedArea);
            }
        }
        
    }
    /**
     * Finds a handle at a given coordinates.
     * @return A handle, null if no handle is found.
     */
    public Handle findHandle(Point p) {
        validateHandles();
        
        for (Handle handle : new ReversedList<Handle>(getSecondaryHandles())) {
            if (handle.contains(p)) {
                return handle;
            }
        }
        for (Handle handle : new ReversedList<Handle>(getSelectionHandles())) {
            if (handle.contains(p)) {
                return handle;
            }
        }
        return null;
    }
    /**
     * Gets compatible handles.
     * @return A collection containing the handle and all compatible handles.
     */
    public Collection<Handle> getCompatibleHandles(Handle master) {
        validateHandles();
        
        HashSet<Figure> owners = new HashSet<Figure>();
        LinkedList<Handle> compatibleHandles = new LinkedList<Handle>();
        owners.add(master.getOwner());
        compatibleHandles.add(master);
        
        for (Handle handle : getSelectionHandles()) {
            if (! owners.contains(handle.getOwner())
            && handle.isCombinableWith(master)) {
                owners.add(handle.getOwner());
                compatibleHandles.add(handle);
            }
        }
        return compatibleHandles;
        
    }
    /**
     * Finds a figure at a given coordinates.
     * @return A figure, null if no figure is found.
     */
    public Figure findFigure(Point p) {
        return getDrawing().findFigure(viewToDrawing(p));
    }
    
    public Collection<Figure> findFigures(Rectangle r) {
        return getDrawing().findFigures(viewToDrawing(r));
    }
    
    public Collection<Figure> findFiguresWithin(Rectangle r) {
        return getDrawing().findFiguresWithin(viewToDrawing(r));
    }
    
    /**
     * Repaints the handles.
     */
    protected void repaintHandles() {
        Rectangle bounds = null;
        for (Handle handle : selectionHandles) {
            if (bounds == null) {
                bounds = handle.getDrawingArea();
            } else {
                bounds.add(handle.getDrawingArea());
            }
        }
        if (bounds != null) {
            repaint(bounds);
        }
    }
    
    
    public void addFigureSelectionListener(FigureSelectionListener fsl) {
        listenerList.add(FigureSelectionListener.class, fsl);
    }
    
    public void removeFigureSelectionListener(FigureSelectionListener fsl) {
        listenerList.remove(FigureSelectionListener.class, fsl);
    }
    /**
     *  Notify all listenerList that have registered interest for
     * notification on this event type.
     */
    protected void fireSelectionChanged(
            Set<Figure> oldValue,
            Set<Figure> newValue) {
        if (listenerList.getListenerCount() > 0) {
            FigureSelectionEvent event = null;
            // Notify all listeners that have registered interest for
            // Guaranteed to return a non-null array
            Object[] listeners = listenerList.getListenerList();
            // Process the listeners last to first, notifying
            // those that are interested in this event
            for (int i = listeners.length-2; i>=0; i-=2) {
                if (listeners[i] == FigureSelectionListener.class) {
                    // Lazily create the event:
                    if (event == null)
                        event = new FigureSelectionEvent(this, oldValue, newValue);
                    ((FigureSelectionListener)listeners[i+1]).selectionChanged(event);
                }
            }
        }
    }
    
    public void handleRequestRemove(HandleEvent e) {
        selectionHandles.remove(e.getHandle());
        e.getHandle().dispose();
        invalidateHandles();
        //validateHandles();
        repaint(e.getInvalidatedArea());
    }
    
    protected void invalidateDimension() {
        cachedPreferredSize = null;
        cachedDrawingArea = null;
    }
    
    public Constrainer getConstrainer() {
        return constrainer;
    }
    
    public void setConstrainer(Constrainer newValue) {
        Constrainer oldValue = constrainer;
        constrainer = newValue;
        repaint();
        firePropertyChange("constrainer", oldValue, newValue);
    }
    
    
    /**
     * Side effect: Changes view Translation!!!
     */
    public Dimension getPreferredSize() {
        if (cachedPreferredSize == null) {
            Rectangle2D.Double r = getDrawingArea();
            translate.x = Math.min(0, r.x);
            translate.y = Math.min(0, r.y);
            cachedPreferredSize = new Dimension(
                    (int) ((r.width + 10 - translate.x) * scaleFactor),
                    (int) ((r.height + 10 - translate.y) * scaleFactor)
                    );
            fireViewTransformChanged();
            repaint();
        }
        return cachedPreferredSize;
    }
    /**
     * Side effect: Changes view Translation!!!
     */
    protected Rectangle2D.Double getDrawingArea() {
        if (cachedDrawingArea == null) {
            cachedDrawingArea = new Rectangle2D.Double();
            if (drawing != null) {
                for (Figure f : drawing.getFigures()) {
                    if (cachedDrawingArea == null) {
                        cachedDrawingArea = f.getDrawingArea();
                    } else {
                        cachedDrawingArea.add(f.getDrawingArea());
                    }
                }
            }
            if (cachedDrawingArea == null) {
                cachedDrawingArea = new Rectangle2D.Double();
            }
        }
        return (Rectangle2D.Double) cachedDrawingArea.clone();
    }
    
    /**
     * Converts drawing coordinates to view coordinates.
     */
    public Point drawingToView(Point2D.Double p) {
        return new Point(
                (int) ((p.x - translate.x) * scaleFactor),
                (int) ((p.y - translate.y) * scaleFactor)
                );
    }
    /**
     * Converts view coordinates to drawing coordinates.
     */
    public Point2D.Double viewToDrawing(Point p) {
        return new Point2D.Double(
                p.x / scaleFactor + translate.x,
                p.y / scaleFactor + translate.y
                );
    }
    public Rectangle drawingToView(Rectangle2D.Double r) {
        return new Rectangle(
                (int) ((r.x - translate.x) * scaleFactor),
                (int) ((r.y - translate.y) * scaleFactor),
                (int) (r.width * scaleFactor),
                (int) (r.height * scaleFactor)
                );
    }
    public Rectangle2D.Double viewToDrawing(Rectangle r) {
        return new Rectangle2D.Double(
                r.x / scaleFactor + translate.x,
                r.y / scaleFactor + translate.y,
                r.width / scaleFactor,
                r.height / scaleFactor
                );
    }
    
    public JComponent getComponent() {
        return this;
    }
    
    public double getScaleFactor() {
        return scaleFactor;
    }
    
    public void setScaleFactor(double newValue) {
        double oldValue = scaleFactor;
        scaleFactor = newValue;
        
        fireViewTransformChanged();
        
        firePropertyChange("scaleFactor", oldValue, newValue);
        
        invalidateDimension();
        invalidate();
        if (getParent() != null) getParent().validate();
        repaint();
    }
    
    protected void fireViewTransformChanged() {
        for (Handle handle : selectionHandles) {
            handle.viewTransformChanged();
        }
        for (Handle handle : secondaryHandles) {
            handle.viewTransformChanged();
        }
    }
    
    public void setHandleDetailLevel(int newValue) {
        detailLevel = newValue;
        invalidateHandles();
        repaint();
    }
    public int getHandleDetailLevel() {
        return detailLevel;
    }
    
    public void handleRequestSecondaryHandles(HandleEvent e) {
        //if (e.getHandle() != secondaryHandleOwner) {
        secondaryHandleOwner = e.getHandle();
        secondaryHandles.clear();
        secondaryHandles.addAll(secondaryHandleOwner.createSecondaryHandles());
        for (Handle h : secondaryHandles) {
            h.setView(this);
            h.addHandleListener(this);
        }
        repaint();
        //}
    }
    
    public AffineTransform getDrawingToViewTransform() {
        AffineTransform t = new AffineTransform();
        t.scale(scaleFactor, scaleFactor);
        t.translate(- translate.x, - translate.y);
        return t;
    }
    
    public void delete() {
        final LinkedList<DrawingEvent> deletionEvents = new LinkedList<DrawingEvent>();
        final LinkedList<Figure> selectedFigures = new LinkedList<Figure>(getSelectedFigures());
        clearSelection();
        DrawingListener removeListener = new DrawingListener() {
            public void areaInvalidated(DrawingEvent e) {
            }
            
            public void figureAdded(DrawingEvent e) {
            }
            
            public void figureRemoved(DrawingEvent evt) {
                deletionEvents.addFirst(evt);
            }
            
        };
        getDrawing().addDrawingListener(removeListener);
        getDrawing().removeAll(selectedFigures);
        getDrawing().removeDrawingListener(removeListener);
        
        getDrawing().fireUndoableEditHappened(new AbstractUndoableEdit() {
            public String getPresentationName() {
                ResourceBundleUtil labels = ResourceBundleUtil.getLAFBundle("org.jhotdraw.draw.Labels");
                return labels.getString("delete");
            }
            public void undo() throws CannotUndoException {
                super.undo();
                clearSelection();
                Drawing d = getDrawing();
                for (DrawingEvent evt : deletionEvents) {
                    d.add(evt.getIndex(), evt.getFigure());
                }
                addToSelection(selectedFigures);
            }
            public void redo() throws CannotRedoException {
                super.redo();
                for (DrawingEvent evt : new ReversedList<DrawingEvent>(deletionEvents)) {
                getDrawing().remove(evt.getFigure());
                }
            }
        });
    }
    
    public void duplicate() {
        Collection<Figure> sorted = getDrawing().sort(getSelectedFigures());
        HashMap<Figure,Figure> originalToDuplicateMap = new HashMap<Figure,Figure>(sorted.size());
        
        clearSelection();
        Drawing drawing = getDrawing();
        final ArrayList<Figure> duplicates = new ArrayList<Figure>(sorted.size());
        AffineTransform tx = new AffineTransform();
        tx.translate(5,5);
        for (Figure f : sorted) {
            Figure d = (Figure) f.clone();
            d.transform(tx);
            duplicates.add(d);
            originalToDuplicateMap.put(f, d);
            drawing.add(d);
        }
        for (Figure f : duplicates) {
            f.remap(originalToDuplicateMap);
        }
        addToSelection(duplicates);
        
        getDrawing().fireUndoableEditHappened(new AbstractUndoableEdit() {
            public String getPresentationName() {
                ResourceBundleUtil labels = ResourceBundleUtil.getLAFBundle("org.jhotdraw.draw.Labels");
                return labels.getString("duplicate");
            }
            public void undo() throws CannotUndoException {
                super.undo();
                getDrawing().removeAll(duplicates);
            }
            public void redo() throws CannotRedoException {
                super.redo();
                getDrawing().addAll(duplicates);
            }
        });
    }
    
    public void removeNotify(DrawingEditor editor) {
        this.editor = null;
        repaint();
    }
    
    public void addNotify(DrawingEditor editor) {
        this.editor = editor;
        repaint();
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    // End of variables declaration//GEN-END:variables
    
}