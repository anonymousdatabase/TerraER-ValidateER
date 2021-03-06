/*
 * @(#)CreationTool.java  2.2  2006-12-14
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

package org.jhotdraw.draw;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.jhotdraw.draw.AttributeKeys.StrokeType;
import org.jhotdraw.util.ResourceBundleUtil;

/**
 * A tool to create new figures. The figure to be created is specified by a
 * prototype.
 * <p>
 * To create a figure using the CreationTool, the user does the following mouse
 * gestures on a DrawingView:
 * <ol>
 * <li>Press the mouse button over the DrawingView. This defines the start point
 * of the Figure bounds.</li>
 * <li>Drag the mouse while keeping the mouse button pressed, and then release
 * the mouse button. This defines the end point of the Figure bounds.</li>
 * </ol>
 * The CreationTool works well with most figures that fit into a rectangular
 * shape or that concist of a single straight line. For figures that need
 * additional editing after these mouse gestures, the use of a specialized
 * creation tool is recommended. For example the TextTool allows to enter the
 * text into a TextFigure after the user has performed the mouse gestures.
 * <p>
 * Alltough the mouse gestures might be fitting for the creation of a
 * connection, the CreationTool is not suited for the creation of a
 * ConnectionFigure. Use the ConnectionTool for this type of figures instead.
 * 
 * @author Werner Randelshofer
 * @version 2.1.1 2006-07-20 Minimal size treshold was enforced too eagerly.
 *          <br>
 *          2.1 2006-07-15 Changed to create prototype creation from class
 *          presentationName. <br>
 *          2.0 2006-01-14 Changed to support double precision coordinates. <br>
 *          1.0 2003-12-01 Derived from JHotDraw 5.4b1.
 */
public class CreationTool extends AbstractTool {
	/**
	 * Attributes to be applied to the created ConnectionFigure. These
	 * attributes override the default attributes of the DrawingEditor.
	 */
	private Map<AttributeKey, Object> prototypeAttributes;

	/**
	 * A localized name for this tool. The presentationName is displayed by the
	 * UndoableEdit.
	 */
	private String presentationName;
	/**
	 * Treshold for which we create a larger shape of a minimal size.
	 */
	private Dimension minimalSizeTreshold = new Dimension(2, 2);
	/**
	 * We set the figure to this minimal size, if it is smaller than the minimal
	 * size treshold.
	 */
	private Dimension minimalSize = new Dimension(40, 40);
	/**
	 * The prototype for new figures.
	 */
	private Figure prototype;
	/**
	 * The created figure.
	 */
	protected Figure createdFigure;

	/** Creates a new instance. */
	public CreationTool(String prototypeClassName) {
		this(prototypeClassName, null, null);
	}

	public CreationTool(String prototypeClassName, Map<AttributeKey, Object> attributes) {
		this(prototypeClassName, attributes, null);
	}

	public CreationTool(String prototypeClassName, Map<AttributeKey, Object> attributes, String name) {
		try {
			this.prototype = (Figure) Class.forName(prototypeClassName).newInstance();
		} catch (Exception e) {
			InternalError error = new InternalError("Unable to create Figure from " + prototypeClassName);
			error.initCause(e);
			throw error;
		}
		this.prototypeAttributes = attributes;
		if (name == null) {
			ResourceBundleUtil labels = ResourceBundleUtil.getLAFBundle("org.jhotdraw.draw.Labels");
			name = labels.getString("createFigure");
		}
		this.presentationName = name;
	}

	/**
	 * Creates a new instance with the specified prototype but without an
	 * attribute set. The CreationTool clones this prototype each time a new
	 * Figure needs to be created. When a new Figure is created, the
	 * CreationTool applies the default attributes from the DrawingEditor to it.
	 * 
	 * @param prototype
	 *            The prototype used to create a new Figure.
	 */
	public CreationTool(Figure prototype) {
		this(prototype, null, null);
	}
	
	public CreationTool(Figure prototype, String name) {
		this(prototype, null, name);
	}

	/**
	 * Creates a new instance with the specified prototype but without an
	 * attribute set. The CreationTool clones this prototype each time a new
	 * Figure needs to be created. When a new Figure is created, the
	 * CreationTool applies the default attributes from the DrawingEditor to it,
	 * and then it applies the attributes to it, that have been supplied in this
	 * constructor.
	 * 
	 * @param prototype
	 *            The prototype used to create a new Figure.
	 * @param attributes
	 *            The CreationTool applies these attributes to the prototype
	 *            after having applied the default attributes from the
	 *            DrawingEditor.
	 */
	public CreationTool(Figure prototype, Map<AttributeKey, Object> attributes) {
		this(prototype, attributes, null);
	}

	/**
	 * Creates a new instance with the specified prototype and attribute set.
	 * 
	 * @param prototype
	 *            The prototype used to create a new Figure.
	 * @param attributes
	 *            The CreationTool applies these attributes to the prototype
	 *            after having applied the default attributes from the
	 *            DrawingEditor.
	 * @param presentationName
	 *            The presentationName parameter is currently not used.
	 * @deprecated This constructor might go away, because the presentationName
	 *             parameter is not used.
	 */
	public CreationTool(Figure prototype, Map<AttributeKey, Object> attributes, String name) {
		this.prototype = prototype;
		this.prototypeAttributes = attributes;
		ResourceBundleUtil labels = ResourceBundleUtil.getLAFBundle("org.jhotdraw.draw.Labels");
		if (name == null) {			
			name = labels.getString("createFigure");
		} else {
			name = labels.getString(name + ".desc");
		}
		this.presentationName = name;
	}

	public Figure getPrototype() {
		return prototype;
	}

	public void activate(DrawingEditor editor) {
		super.activate(editor);
		//getView().clearSelection();
		getView().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	public void deactivate(DrawingEditor editor) {
		super.deactivate(editor);
		if (getView() != null) {
			getView().setCursor(Cursor.getDefaultCursor());
		}
		if (createdFigure != null) {
			if (createdFigure instanceof CompositeFigure) {
				((CompositeFigure) createdFigure).layout();
			}
			createdFigure = null;
		}
	}

	public void mousePressed(MouseEvent evt) {
		super.mousePressed(evt);
		getView().clearSelection();
		createdFigure = createFigure();
		Point2D.Double p = constrainPoint(viewToDrawing(anchor));
		anchor.x = evt.getX();
		anchor.y = evt.getY();
		createdFigure.setBounds(p, p);
		getDrawing().add(createdFigure);
		if (!(createdFigure instanceof TextFigure) && !(createdFigure instanceof TextAreaFigure)){
			this.mouseReleased(evt);
		}
	}

	public void mouseDragged(MouseEvent evt) {
		// if (createdFigure != null) {
		// Point2D.Double p = constrainPoint(new Point(evt.getX(), evt.getY()));
		// createdFigure.willChange();
		// createdFigure.setBounds(
		// constrainPoint(new Point(anchor.x, anchor.y)),
		// p
		// );
		// createdFigure.changed();
		// }
	}
	
	public void setBoundsEntity(Rectangle2D.Double bounds) {
		GroupFigure gf = (GroupFigure) createdFigure;
		gf.setBounds(constrainPoint(new Point(anchor.x, anchor.y)), constrainPoint(
				new Point(anchor.x + (int) Math.max(bounds.width, 80), anchor.y + (int) Math.max(bounds.height, 40))));
		for (Figure f : gf.getChildren()) {
			f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
					constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
							anchor.y + (int) Math.max(bounds.height, 40))));
			if (f.getClass().equals(RectangleFigure.class)) {
				f.setAttribute(AttributeKeys.FILL_COLOR, new Color(235, 255, 232));
			} else if (f.getClass().equals(DiamondFigure.class)) {
				f.setBounds(constrainPoint(new Point(anchor.x + 2, anchor.y + 2)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 76),
								anchor.y + (int) Math.max(bounds.height, 36))));
				f.setAttribute(AttributeKeys.FILL_COLOR, new Color(221, 221, 255));
			} else if (f.getClass().equals(TextFigure.class)) {
				f.setBounds(constrainPoint(new Point(anchor.x + 10, anchor.y + 13)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
								anchor.y + (int) Math.max(bounds.height, 20))));
			}
		}
	}

	public void setBoundsAttribute(Rectangle2D.Double bounds) {
		GroupFigure gf = (GroupFigure) createdFigure;
		gf.setBounds(constrainPoint(new Point(anchor.x, anchor.y)), constrainPoint(
				new Point(anchor.x + (int) Math.max(bounds.width, 80), anchor.y + (int) Math.max(bounds.height, 40))));
		for (Figure f : gf.getChildren()) {
			f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
					constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
							anchor.y + (int) Math.max(bounds.height, 40))));
			if (f.getClass().equals(EllipseFigure.class)) {
				f.setAttribute(AttributeKeys.FILL_COLOR, new Color(255, 235, 235));
				if (createdFigure.getClass().equals(AtributoChaveParcialFigure.class)) {
					f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
							constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 125),
									anchor.y + (int) Math.max(bounds.height, 27))));
				} else {
					f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
							constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
									anchor.y + (int) Math.max(bounds.height, 20))));
				}
			} else if (f.getClass().equals(TextFigure.class)) {
				f.setBounds(constrainPoint(new Point(anchor.x + 10, anchor.y + 3)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
								anchor.y + (int) Math.max(bounds.height, 20))));
			}
		}
	}

	public void setBoundsRelationship(Rectangle2D.Double bounds) {
		GroupFigure gf = (GroupFigure) createdFigure;

		gf.setBounds(constrainPoint(new Point(anchor.x, anchor.y)), constrainPoint(
				new Point(anchor.x + (int) Math.max(bounds.width, 80), anchor.y + (int) Math.max(bounds.height, 40))));

		for (Figure f : gf.getChildren()) {
			if (createdFigure.getClass().equals(RelacionamentoFracoFigure.class)) {
				gf.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 97),
								anchor.y + (int) Math.max(bounds.height, 50))));
			} else {
				f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
								anchor.y + (int) Math.max(bounds.height, 40))));
			}
			if (f.getClass().equals(DiamondFigure.class)) {
				f.setAttribute(AttributeKeys.FILL_COLOR, new Color(221, 221, 255));
			} else if (f.getClass().equals(TextFigure.class)) {
				f.setBounds(constrainPoint(new Point(anchor.x + 15, anchor.y + 13)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
								anchor.y + (int) Math.max(bounds.height, 20))));
			}
		}
	}

	public void setBoundsInheritanceMode(Rectangle2D.Double bounds) {
		GroupFigure gf = (GroupFigure) createdFigure;
		gf.setBounds(constrainPoint(new Point(anchor.x, anchor.y)), constrainPoint(
				new Point(anchor.x + (int) Math.max(bounds.width, 20), anchor.y + (int) Math.max(bounds.height, 20))));
		for (Figure f : gf.getChildren()) {
			if (f.getClass().equals(CircleFigure.class)) {
				f.setAttribute(AttributeKeys.FILL_COLOR, new Color(245, 242, 224));
				f.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 20),
								anchor.y + (int) Math.max(bounds.height, 20))));
			} else if (f.getClass().equals(TextNegritoFigure.class)) {
				f.setBounds(constrainPoint(new Point(anchor.x + 4, anchor.y + 1)),
						constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 20),
								anchor.y + (int) Math.max(bounds.height, 20))));
			}
		}
	}

	public void setBoundsOthes(Rectangle2D.Double bounds) {
		if (createdFigure.getClass().equals(RectangleFigure.class)
				|| createdFigure.getClass().equals(DiamondFigure.class)) {
			// createdFigure.setAttribute(AttributeKeys.STROKE_WIDTH,2d);
			// nao usado
			createdFigure.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
					constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
							anchor.y + (int) Math.max(bounds.height, 40))));
			if (createdFigure.getClass().equals(RectangleFigure.class)) {
				createdFigure.setAttribute(AttributeKeys.FILL_COLOR, new Color(235, 255, 232));
			} else if (createdFigure.getClass().equals(DiamondFigure.class)) {
				createdFigure.setAttribute(AttributeKeys.FILL_COLOR, new Color(221, 221, 255));
			}
		} else if (createdFigure.getClass().equals(EllipseFigure.class)) {
			// nao usado
			createdFigure.setAttribute(AttributeKeys.FILL_COLOR, new Color(255, 235, 235));
			createdFigure.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
					constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, 80),
							anchor.y + (int) Math.max(bounds.height, 20))));
		} else if (createdFigure.getClass().equals(TextNegritoFigure.class)) {
			// nao usado
			TextFigure tf = (TextFigure) createdFigure;
			tf.setAttribute(tf.getAttributeKey("fontBold"), Boolean.TRUE);
			tf.setFontSize(16);
			tf.setEditable(false);
		} else {
			createdFigure.setBounds(constrainPoint(new Point(anchor.x, anchor.y)),
					constrainPoint(new Point(anchor.x + (int) Math.max(bounds.width, minimalSize.width),
							anchor.y + (int) Math.max(bounds.height, minimalSize.height))));
		}
	}

	public void mouseReleased(MouseEvent evt) {
		if (createdFigure != null) {
			Rectangle2D.Double bounds = createdFigure.getBounds();
			if (bounds.width == 0 && bounds.height == 0) {
				getDrawing().remove(createdFigure);
				fireToolDone();
			} else {
				if (Math.abs(anchor.x - evt.getX()) < minimalSizeTreshold.width
						&& Math.abs(anchor.y - evt.getY()) < minimalSizeTreshold.height) {
					createdFigure.willChange();
					if (createdFigure.getClass().equals(EntidadeRelacionamentoFigure.class)
							|| createdFigure.getClass().equals(EntidadeFigure.class)
							|| createdFigure.getClass().equals(EntidadeFracaFigure.class)) {
						setBoundsEntity(bounds);
					} else if (createdFigure.getClass().equals(AtributoFigure.class)
							|| createdFigure.getClass().equals(AtributoDerivadoFigure.class)
							|| createdFigure.getClass().equals(AtributoMultivaloradoFigure.class)
							|| createdFigure.getClass().equals(AtributoChaveFigure.class)
							|| createdFigure.getClass().equals(AtributoChaveParcialFigure.class)) {
						setBoundsAttribute(bounds);
					} else if (createdFigure.getClass().equals(RelacionamentoFigure.class)
							|| createdFigure.getClass().equals(RelacionamentoFracoFigure.class)) {
						setBoundsRelationship(bounds);
					} else if (createdFigure.getClass().equals(DisjuncaoFigure.class)
							|| createdFigure.getClass().equals(UniaoFigure.class)
							|| createdFigure.getClass().equals(SobreposicaoFigure.class)) {
						setBoundsInheritanceMode(bounds);
					} else if (createdFigure.getClass().equals(TextItalicoFigure.class)) {
						((TextFigure) createdFigure).setAttribute(AttributeKeys.FONT_ITALIC, Boolean.TRUE);
					} else {
						setBoundsOthes(bounds);
					}
					createdFigure.changed();
				}
				getView().addToSelection(createdFigure);
				if (createdFigure instanceof CompositeFigure) {
					((CompositeFigure) createdFigure).layout();
				}
				final Figure addedFigure = createdFigure;
				final Drawing addedDrawing = getDrawing();
				getDrawing().fireUndoableEditHappened(new AbstractUndoableEdit() {
					public String getPresentationName() {
						return presentationName;
					}

					public void undo() throws CannotUndoException {
						super.undo();
						addedDrawing.remove(addedFigure);
					}

					public void redo() throws CannotRedoException {
						super.redo();
						addedDrawing.add(addedFigure);
					}
				});
				creationFinished(createdFigure);
			}

		} else {
			fireToolDone();
		}
	}

	protected Figure createFigure() {
		Figure f = (Figure) prototype.clone();
		getEditor().applyDefaultAttributesTo(f);
		if (prototypeAttributes != null) {
			for (Map.Entry<AttributeKey, Object> entry : prototypeAttributes.entrySet()) {
				f.setAttribute(entry.getKey(), entry.getValue());
			}
		}
		return f;
	}

	protected Figure getCreatedFigure() {
		return createdFigure;
	}

	protected Figure getAddedFigure() {
		return createdFigure;
	}

	/**
	 * This method allows subclasses to do perform additonal user interactions
	 * after the new figure has been created. The implementation of this class
	 * just invokes fireToolDone.
	 */
	protected void creationFinished(Figure createdFigure) {
		fireToolDone();
	}
}
