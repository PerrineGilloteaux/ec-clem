/**
 * Copyright 2010-2017 Perrine Paul-Gilloteaux, CNRS.
 * Perrine.Paul-Gilloteaux@univ-nantes.fr
 * 
 * This file is part of EC-CLEM.
 * 
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * 
 * Main Class
 **/
package plugins.perrine.easyclemv0;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import Jama.Matrix;
import plugins.adufour.ezplug.EzButton;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;
import plugins.kernel.roi.descriptor.measure.ROIMassCenterDescriptorsPlugin;
import plugins.kernel.roi.roi2d.plugin.ROI2DPointPlugin;
import plugins.kernel.roi.roi3d.ROI3DPoint;
import plugins.kernel.roi.roi3d.plugin.ROI3DPointPlugin;
import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ToolTipFrame;
import icy.gui.util.FontUtil;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.lut.LUT;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import icy.sequence.SequenceUtil;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.system.thread.ThreadUtil;
import icy.type.DataType;
import icy.type.point.Point5D;
import icy.util.XMLUtil;

public class EasyCLEMv0 extends EzPlug implements EzStoppable, SequenceListener {
	private ActionListener actionbutton = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (source.getValue() != null) {
				for (final PluginDescriptor pluginDescriptor : PluginLoader.getPlugins()) {
					if (pluginDescriptor.getSimpleClassName().compareToIgnoreCase("TransformBasedonCameraView") == 0) {
						PluginLauncher.start(pluginDescriptor);
					}
				}
			} else {
				MessageDialog.showDialog("Source was closed. Please open one and try again");
			}
		}

	};
	EzButton prealign = new EzButton("I want to prealign (rotate in 3D) my data)", actionbutton);
	boolean stopFlag = true;
	// TODO tester la taille des images par rapport a la memoire dsipo et prevenir qu'il faut cropper

	Vector<PointsPair> fiducialsvector;
	Vector<PointsPair3D> fiducialsvector3D;
	double[][] targetpoints;
	double[][] sourcepoints;
	List<Double> listoftrevalues;
	List<Double> listofNvalues;
	private Runnable transformer;

	private boolean flagReadyToMove;
	private boolean done;

	File XMLFile;
	private Overlay myoverlaysource;
	private Overlay myoverlaytarget;
	Overlay myoverlaypredictederror;
	Overlay myoverlayerror;
	private Overlay messageSource;
	private Overlay messageTarget;
	boolean nonrigid;

	static String[] listofRegistrationchoice = new String[] { "From Live to EM", "From Section to EM",
			"From Live to Section" };
	EzVarBoolean showgrid = new EzVarBoolean(" Show grid deformation", false);
	EzVarText choiceinputsection = new EzVarText("I want to compute the transformation in:",
			new String[] { "2D (X,Y,[T])", "2D but let me update myself", "3D (X,Y,Z,[T])",
					"3D but let me update myself", "non rigid (2D or 3D)" },
			0, false
	);

	EzLabel versioninfo = new EzLabel("Version " +this.getDescriptor().getVersion());

	EzVarSequence target = new EzVarSequence("Select image that will not be modified (likely EM)");

	EzVarSequence source = new EzVarSequence("Select image that will be transformed and resized (likely FM)");
	EzGroup grp = new EzGroup("Images to process", source, target);
	Sequence copysource;

	Sequence backupsource;
	// backup also calibration
	double bucalibx;
	double bucaliby;
	double bucalibz;
	protected boolean predictederrorselected = false;

	protected boolean overlayerrorselected = false;

	boolean mode3D = false;

	private boolean pause = false;

	private Color[] Colortab;

	boolean monitor = false;

	public int xtarget;

	public int ytarget;

	public boolean waitfortarget = false;

	public boolean monitortargetonsource = false;

	protected boolean checkgrid = false;
	private GuiCLEMButtons2 rigidspecificbutton;

	/**
	 * Overlay surdefined for paint would happen at each paint: will make the
	 * points more visible.
	 * 
	 */
	private class VisiblepointsOverlay extends Overlay {
		public VisiblepointsOverlay() {
			super("Visible points");
		}

		@Override
		public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
			// check if we are dealing with a 2D canvas and we have a valid
			// Graphics object
			if ((canvas instanceof IcyCanvas2D) && (g != null)) {

				ArrayList<ROI> listfiducials = sequence.getROIs();

				for (ROI roi : listfiducials) {

					Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
					if (Double.isNaN(p3D.getX()))
						p3D = roi.getPosition5D(); // some Roi does not have
													// gravity

					g.setColor(Color.BLACK);
					g.setStroke(new BasicStroke(5));
					Font f = g.getFont();
					f = FontUtil.setName(f, "Arial");
					f = FontUtil.setSize(f, (int) canvas.canvasToImageLogDeltaX(20));
					g.setFont(f);

					g.drawString(roi.getName(), (float) p3D.getX(), (float) p3D.getY());

					g.setColor(Color.YELLOW);
					g.drawString(roi.getName(), (float) p3D.getX() + 1, (float) p3D.getY() + 1);

				}
			}
		}
	}

	/**
	 * Overlay surdefined for paint would happen at each paint: will draw as a
	 * circle the difference in position
	 * 
	 */
	private class ErrorinPositionOverlay extends Overlay {
		public ErrorinPositionOverlay() {
			super("Difference in position");
		}

		@Override
		public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
			// check if we are dealing with a 2D canvas and we have a valid
			// Graphics object
			if ((canvas instanceof IcyCanvas2D) && (g != null)) {
				if ((sourcepoints != null) || (targetpoints != null)) {
					if (mode3D == false) {
						fiducialsvector = createVectorfromdoublearray(sourcepoints, targetpoints);
						for (int index = 0; index < fiducialsvector.size(); index++) {

							g.setStroke(new BasicStroke((int) canvas.canvasToImageLogDeltaX(5)));
							g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
							g.setColor(Color.RED);
							double error = fiducialsvector.get(index).getDiffinpixels();

							/*
							 * g.drawOval( (int)
							 * Math.round(fiducialsvector.get(index).first
							 * .getX() - error), (int)
							 * Math.round(fiducialsvector.get(index).first
							 * .getY() - error), (int) Math .round(error * 2),
							 * (int) Math .round(error * 2));
							 */
							// now we draw an arrow:

							double l = error / 4;
							double w = 3;

							plotarrow(fiducialsvector.get(index).getfirstxinpixels(),
									fiducialsvector.get(index).getfirstyinpixels(),
									fiducialsvector.get(index).getsecondxinpixels(),
									fiducialsvector.get(index).getsecondyinpixels(), l, w, g);

							// g.draw( l3 );

						}
					} else// mode3D
					{
						fiducialsvector3D = createVectorfromdoublearray3D(sourcepoints, targetpoints);
						for (int index = 0; index < fiducialsvector3D.size(); index++) {
							g.setColor(Color.RED);
							double error = fiducialsvector3D.get(index).getDiffinpixels();

							g.drawOval((int) Math.round(fiducialsvector3D.get(index).first.getX() - error),
									(int) Math.round(fiducialsvector3D.get(index).first.getY() - error),
									(int) Math.round(error * 2), (int) Math.round(error * 2));
						}
					}
				}

			}
		}

	}

	/**
	 * Draw an arrow from point (x1,y1) to (x2,y2) with arrow-end length of l
	 * and arrow-end width of w
	 */
	private void plotarrow(double x1, double y1, double x2, double y2, double l, double w, Graphics2D g) {
		/*
		 * c a ------------------- b d
		 */
		double[] ab = { x2 - x1, y2 - y1 }; // ab vector
		double norm = Math.sqrt(ab[0] * ab[0] + ab[1] * ab[1]);
		if (norm > l) {// draw only if length(ab) > head length
			// t = ab vector normalized to l
			int[] t = { (int) Math.rint((double) ab[0] * (l / norm)), (int) Math.rint((double) ab[1] * (l / norm)) };

			double[] r = { ab[1], -ab[0] };
			norm = Math.sqrt(r[0] * r[0] + r[1] * r[1]);
			r[0] = (int) Math.rint((double) r[0] / norm * (w / 2));
			r[1] = (int) Math.rint((double) r[1] / norm * (w / 2));

			double[][] tri = { { x2, x2 - t[0] + r[0], x2 - t[0] - r[0], x2 },
					{ y2, y2 - t[1] + r[1], y2 - t[1] - r[1], y2 } };
			Line2D l1 = new Line2D.Double(x1, y1, x2, y2);
			g.draw(l1);

			GeneralPath filledPolygon = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 3);
			filledPolygon.moveTo(tri[0][0], tri[1][0]);
			for (int index = 1; index < 3; index++) {
				filledPolygon.lineTo(tri[0][index], tri[1][index]);
			}
			;
			filledPolygon.closePath();

			g.fill(filledPolygon);

			g.draw(filledPolygon);

		}
	}

	/**
	 * Overlay surdefined for paint would happen at each paint: will draw as a
	 * circle the predicted Registration error in the Fitzpatrick definition
	 * (even if we assimilate TRE to FRE here)
	 * 
	 */
	private class PredictedErrorinPositionOverlay extends Overlay {
		public PredictedErrorinPositionOverlay() {
			super("Predicted Error from point configuration");
		}

		@Override
		public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
			// check if we are dealing with a 2D canvas and we have a valid
			// Graphics object
			if ((canvas instanceof IcyCanvas2D) && (g != null)) {
				TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
				ComputeFRE.ReadFiducials(sequence);
				// OMEXMLMetadataImpl sourcepixelsize = sequence.getMetadata();
				double xsource = sequence.getPixelSizeX();

				// PositiveFloat ysource =
				// sourcepixelsize.getPixelsPhysicalSizeY(0);
				double[] f = ComputeFRE.PreComputeTRE();

				ArrayList<ROI> listfiducials = sequence.getROIs();

				for (ROI roi : listfiducials) {

					Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
					if (Double.isNaN(p3D.getX()))
						p3D = roi.getPosition5D(); // some Roi does not have
													// gravity
					// center such as points

					int x = (int) Math.round(p3D.getX());
					int y = (int) Math.round(p3D.getY());
					g.setColor(Color.ORANGE);
					g.setStroke(new BasicStroke(5));
					double FLEmax = maxdifferrorinnm();
					double diameter = ComputeFRE.ComputeTRE(FLEmax, x, y, 0, f); // in
																					// nanometers->
																					// convert
																					// to
					// dimater was radius // pixels

					diameter = (diameter * 2) / (1000 * xsource); // in
																	// pixels
																	// from
																	// nm
					x = (int) Math.round(p3D.getX() - diameter / 2);
					y = (int) Math.round(p3D.getY() - diameter / 2);
					g.drawOval(x, y, (int) Math.round(diameter), (int) Math.round(diameter));

				}

			}
		}
	}

	/**
	 * Display an informative image on the top of sequences
	 * 
	 * @author Perrine
	 *
	 */
	private class MessageOverlay extends Overlay {
		String mytext;

		public MessageOverlay(String text) {

			super("Message");
			mytext = text;
		}

		@Override
		public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
			// check if we are dealing with a 2D canvas and we have a valid
			// Graphics object
			if ((canvas instanceof IcyCanvas2D) && (g != null)) {

				g.setColor(Color.RED);
				g.setStroke(new BasicStroke(5));
				Font f = g.getFont();
				f = FontUtil.setName(f, "Arial");
				f = FontUtil.setSize(f, (int) canvas.canvasToImageLogDeltaX(20));
				g.setFont(f);

				// String mytext="test";

				g.drawString(mytext, 10, (int) canvas.canvasToImageLogDeltaX(50));

			}

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
		Colortab = new Color[9];
		Colortab[0] = Color.RED;
		Colortab[1] = Color.YELLOW;
		Colortab[2] = Color.PINK;
		Colortab[3] = Color.GREEN;
		Colortab[4] = Color.BLUE;
		Colortab[5] = Color.CYAN;
		Colortab[6] = Color.LIGHT_GRAY;
		Colortab[7] = Color.MAGENTA;
		Colortab[8] = Color.ORANGE;
		new ToolTipFrame("<html>" + "<br> Press Play when ready. " + "<br> <li> Add point (2D or 3D ROI) on target image only.</li> "
				+ "<br> <li> Drag the point in Source, and RIGHT CLICK. Then add point again on target. "
				+ "<br> <li> If you add a point on source image instead (called point2D), delete it, "
				+ "<br> and select the ROI Point to add a point from Target</li> "
				+ "<br> <li> You can also prepare pair of points before , "
				+ "<br> by making sure they will have the same name in both images.</li>"
				+ "<br> <li> Do not forget that the transformation will be automatically saved "
				+ "<br> and that you can apply to any image with the same original or a rescaled dimension.</li>"
				+ "<br> <li> When working in 3D mode, make sure metadata (pixel size) are correctly calibrated, see Sequence Properties.</li> "
				+ "</html>","startmessage");
		
		addEzComponent(versioninfo);
		addEzComponent(choiceinputsection);

		addEzComponent(showgrid);

		addEzComponent(prealign);
		prealign.setToolTipText("Volume can be turned in order to generate a new and still calibrated stack");
		choiceinputsection.addVisibilityTriggerTo(prealign, "3D (X,Y,Z,[T])", "3D but let me update myself");
		addComponent(new GuiCLEMButtonPreprocess(this));

		addComponent(new GuiCLEMButtonApply(this));

		addComponent(new advancedmodules(this));
		addEzComponent(grp);
		

		choiceinputsection.setToolTipText("2D transform will be only in the plane XY "
				+ "but can be applied to all dimensions.\n WARNING make sure to have the metadata correctly set in 3D");

		choiceinputsection.addVisibilityTriggerTo(showgrid, "non rigid (2D or 3D)");

		addComponent(new GuiCLEMButtons(this));
		rigidspecificbutton = new GuiCLEMButtons2(this);
		addComponent(rigidspecificbutton);

		this.listoftrevalues = new ArrayList<Double>();
		this.listofNvalues = new ArrayList<Double>();

		transformer = new Runnable() {

			@Override
			public void run() {

				if (stopFlag == false) {

					GetSourcePointsfromROI();
					GetTargetPointsfromROI();
					if (sourcepoints.length == targetpoints.length) {
						if (mode3D == false) {
							fiducialsvector = createVectorfromdoublearray(sourcepoints, targetpoints);
							fiducialsvector3D = new Vector<PointsPair3D>();
						} else {
							fiducialsvector3D = createVectorfromdoublearray3D(sourcepoints, targetpoints);
							fiducialsvector = new Vector<PointsPair>();
						}

					} else
					// to do separate case where source points more than target
					// point= adding wrongly a point on target point
					{

						// removing roi not called Point number
						boolean removed = false;
						ArrayList<ROI> listroi = source.getValue().getROIs();
						for (ROI roi : listroi) {
							if (roi.getName().contains("Point2D")) {
								source.getValue().removeROI(roi);
								removed = true;
							}
							if (roi.getName().contains("Point3D")) {
								source.getValue().removeROI(roi);
								removed = true;
							}
						}
						listroi = target.getValue().getROIs();
						for (ROI roi : listroi) {
							if (roi.getName().contains("Point2D")) {
								target.getValue().removeROI(roi);
								removed = true;
							}
							if (roi.getName().contains("Point3D")) {
								target.getValue().removeROI(roi);
								removed = true;
							}
						}

						GetSourcePointsfromROI();
						GetTargetPointsfromROI();
						if (removed)
							new AnnounceFrame(
									"All points named Point2D or Point3D and likely not added by you have been removed. Re click now on \"apply transform\"");
						if (sourcepoints.length != targetpoints.length) {
							MessageDialog.showDialog("Number of points", 
									"The number of points of ROI in source and target image are different. \n Check your ROI points and update transfo ");
							

						}
						Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
						return;

					}
					// Perrine: Why did I do that?
					int z = source.getValue().getFirstViewer().getPositionZ();
					ROI roi = source.getValue().getROIs().get(source.getValue().getROIs().size() - 1);// was
																										// get
																										// selected
																										// roi

					if (roi != null) {
						Point5D pos = roi.getPosition5D();
						// set z et recuperer
						pos.setZ(z);
						roi.setPosition5D(pos);
						// roi.setColor(Color.green);
						if (pause == false) {
							ComputeTransfo();
						} else {
							new AnnounceFrame("You are in pause mode, click on update transfo", 3);
							Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
						}

					}
				}

			}

		};

	}

	/**
	 * convert the array of source and taget point in a pait of ficulial vector
	 * for 3D points
	 * 
	 * @param sourcepoints2
	 * @param targetpoints2
	 * @return fidulciallist
	 */
	Vector<PointsPair3D> createVectorfromdoublearray3D(double[][] sourcepoints2, double[][] targetpoints2) {
		Vector<PointsPair3D> points = new Vector<PointsPair3D>();
		if (sourcepoints2.length == targetpoints2.length) {
			for (int i = 0; i < sourcepoints2.length; i++) {
				points.addElement(
						new PointsPair3D(new PPPoint3D(sourcepoints2[i][0], sourcepoints2[i][1], sourcepoints2[i][2]),
								new PPPoint3D(targetpoints2[i][0], targetpoints2[i][1], targetpoints2[i][2])));
				/*
				 * System.out.print("Point " + i + 1 + " source " +
				 * sourcepoints2[i][0] + " " + sourcepoints2[i][1] + " target "
				 * + targetpoints2[i][0] + " " + targetpoints2[i][1] + "\n");
				 */
			}
		} /*
			 * else{ new AnnounceFrame(
			 * "Warning: not the same number of point on both image. Nothing done"
			 * ,5); }
			 */
		return points;
	}

	/**
	 * convert the array of source and taget point in a pait of ficulial vector
	 * for 2D points
	 * 
	 * @param sourcepoints2
	 * @param targetpoints2
	 * @return
	 */
	Vector<PointsPair> createVectorfromdoublearray(double[][] sourcepoints2, double[][] targetpoints2) {

		Vector<PointsPair> points = new Vector<PointsPair>();
		if (targetpoints2.length == sourcepoints2.length) {
			for (int i = 0; i < sourcepoints2.length; i++) {
				points.addElement(new PointsPair(new Point2D.Double(sourcepoints2[i][0], sourcepoints2[i][1]),
						new Point2D.Double(targetpoints2[i][0], targetpoints2[i][1])));
				/*
				 * System.out.print("Point " + i + 1 + " source " +
				 * sourcepoints2[i][0] + " " + sourcepoints2[i][1] + " target "
				 * + targetpoints2[i][0] + " " + targetpoints2[i][1] + "\n");
				 */
			}
		} // else{
			// new AnnounceFrame("Warning: not the same number of point on both
			// image. Nothing done",5);
			// }
		return points;

	}

	/**
	 * This methods reorder in alphanumerical order the ROI in order to have
	 * matching point pairs
	 * 
	 * @param listfiducials
	 */
	private void ReOrder(ArrayList<ROI> listfiducials) {

		int longueur = listfiducials.size();

		ROI tampon;
		boolean permut;

		do {

			permut = false;
			for (int i = 0; i < longueur - 1; i++) {

				if (listfiducials.get(i).getName().compareTo(listfiducials.get(i + 1).getName()) > 0) {

					tampon = listfiducials.get(i);
					listfiducials.set(i, listfiducials.get(i + 1));
					listfiducials.set(i + 1, tampon);
					permut = true;
				}
			}
		} while (permut);

	}

	/**
	 * This method will create an ordered array for the target points
	 */
	void GetTargetPointsfromROI() {
		if (target.getValue() == null) {
			MessageDialog.showDialog("Make sure target image is openned");
			return;
		}
		target.getValue().removeListener(this);
		ArrayList<ROI> listfiducials = target.getValue().getROIs();
		for (int i = 0; i < listfiducials.size(); i++) {
			ROI roi = listfiducials.get(i);
			if (roi.getClassName() != "plugins.kernel.roi.roi3d.ROI3DPoint") {
				ROI3DPoint roi3D = new ROI3DPoint(roi.getPosition5D());
				roi3D.setName(roi.getName());
				roi3D.setColor(roi.getColor());
				roi3D.setStroke(roi.getStroke());
				listfiducials.set(i, roi3D);// then we convert the Roi
			}
		}

		target.getValue().removeAllROI();

		target.getValue().addROIs(listfiducials, false);
		
		ReOrder(listfiducials);
		// target.getValue().removeAllROI();
		// target.getValue().addROIs(listfiducials, true);
		this.targetpoints = new double[listfiducials.size()][3];

		int i = -1;
		for (ROI roi : listfiducials) {
			i++;
			Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
			if (roi.getClassName() == "plugins.kernel.roi.roi3d.ROI3DPoint")
				p3D = roi.getPosition5D();
			if (Double.isNaN(p3D.getX()))
				p3D = roi.getPosition5D(); // some Roi does not have gravity
											// center such as points
			this.targetpoints[i][0] = p3D.getX();
			this.targetpoints[i][1] = p3D.getY();
			this.targetpoints[i][2] = p3D.getZ();
			// if (target.getValue().getSizeZ()==1){
			// this.targetpoints[i][2] =1.0;
			// }
			// else{
			this.targetpoints[i][2] = p3D.getZ();
			// }
		}
		target.getValue().addListener(this);
	}

	/**
	 * This method will create an ordered array for the source points
	 */
	void GetSourcePointsfromROI() {
		if (source.getValue() == null) {
			MessageDialog.showDialog("Make sure source image is openned");
			return;
		}
		source.getValue().removeListener(this);
		
		ArrayList<ROI> listfiducials = source.getValue().getROIs();
		for (int i = 0; i < listfiducials.size(); i++) {
			ROI roi = listfiducials.get(i);
			if (roi.getClassName() != "plugins.kernel.roi.roi3d.ROI3DPoint") {
				ROI3DPoint roi3D = new ROI3DPoint(roi.getPosition5D());
				roi3D.setName(roi.getName());
				roi3D.setColor(roi.getColor());
				roi3D.setStroke(roi.getStroke());
				listfiducials.set(i, roi3D);// then we convert the Roi
				
			}
		}

		source.getValue().removeAllROI();
		source.getValue().addROIs(listfiducials, false);
		// ORDER ROI by name
		ReOrder(listfiducials); // should be myRoi3d now

		// source.getValue().getROIs().replaceAll(arg0);
		this.sourcepoints = new double[listfiducials.size()][3];
		// fiducials=new double[10][3];
		int i = -1;

		for (ROI roi : listfiducials) {
			i++;
			Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
			if (roi.getClassName() == "plugins.kernel.roi.roi3d.ROI3DPoint")
				p3D = roi.getPosition5D();

			if (Double.isNaN(p3D.getX()))
				p3D = roi.getPosition5D(); // some Roi does not have gravity
											// center such as points
			this.sourcepoints[i][0] = p3D.getX();
			this.sourcepoints[i][1] = p3D.getY();

			this.sourcepoints[i][2] = p3D.getZ();// should be double here now

		}
		// source.getValue().addListener(this);

	}

	/**
	 * happening when pressing the play button: read the settings and launch the
	 * interactive mode of placing the points TODO: add the installation of the
	 * EDF easy plugin when needed
	 * 
	 * @param nonrigid
	 */
	@Override
	protected void execute() {
		// TODO Auto-generated by Icy4Eclipse
		stopFlag = false;

		flagReadyToMove = false;
		Sequence targetseq = target.getValue();
		Sequence sourceseq = source.getValue();

		if (targetseq == sourceseq) {
			MessageDialog.showDialog(
					"You have selected the same sequence for target sequence and source sequence. \n Check the IMAGES to PROCESS selection");
			return;
		}
		if (sourceseq == null) {
			MessageDialog.showDialog(
					"No sequence selected for Source. \n Check the IMAGES to PROCESS selection");
			return;
		}
		if (targetseq == null) {
			MessageDialog.showDialog(
					"No sequence selected for Target. \n Check the IMAGES to PROCESS selection");
			return;
		}
		GetSourcePointsfromROI();
		GetTargetPointsfromROI();
		choiceinputsection.setEnabled(false);

		if (choiceinputsection.getValue() == "3D (X,Y,Z,[T])") {
			nonrigid = false;
			mode3D = true;
			pause = false;

		}
		if (choiceinputsection.getValue() == "2D but let me update myself") {
			mode3D = false;
			nonrigid = false;
			pause = true;

		}
		if (choiceinputsection.getValue() == "2D (X,Y,[T])") {
			mode3D = false;
			pause = false;
			nonrigid = false;

		}
		if (choiceinputsection.getValue() == "3D but let me update myself") {

			mode3D = true;
			pause = true;
			nonrigid = false;

		}

		//
		//
		if (choiceinputsection.getValue() == "non rigid (2D or 3D)") {
			checkgrid = showgrid.getValue();
			pause = true;
			mode3D = false;
			nonrigid = true;
			rigidspecificbutton.removespecificrigidbutton();
			/*
			 * if (source.getValue().getPixelSizeX()!=target.getValue().
			 * getPixelSizeX()){ new AnnounceFrame(
			 * "Non rigid transformation should be done after pre registering rigidly the images\n that then should have the same calibration for pixels (metadata). \n Check your calibration (Sequence properties) or align first in 2D or 3D. "
			 * ); } if
			 * (source.getValue().getSizeX()!=target.getValue().getSizeX()){ new
			 * AnnounceFrame(
			 * "Non rigid transformation should be done after pre registering rigidly the images\n that then should have the image size. \n Align first in 2D or 3D. "
			 * ); }
			 */
		}
		if (mode3D) {
			new AnnounceFrame(
					"Computation will be done in 3D, it can lead to instability in case of planar transformation", 5);

			// convert to 8 bits
			// sourceseq.convertToType(DataType.UBYTE, true);
			// targetseq.convertToType(DataType.UBYTE, true);
			Sequence tmp = null;
			if (sourceseq.getDataType_().getBitSize() != 8) {
				tmp = SequenceUtil.convertToType(sourceseq, DataType.UBYTE, true);
				sourceseq.beginUpdate();
				sourceseq.removeAllImages();
				try {

					for (int t = 0; t < tmp.getSizeT(); t++) {
						for (int z = 0; z < tmp.getSizeZ(); z++) {

							IcyBufferedImage image = tmp.getImage(t, z);

							sourceseq.setImage(t, z, image);

						}
					}
					//
				} finally {

					sourceseq.endUpdate();

					// sequence.
				}
			}
			if (targetseq.getDataType_().getBitSize() != 8) {

				tmp = SequenceUtil.convertToType(targetseq, DataType.UBYTE, true);
				targetseq.beginUpdate();
				targetseq.removeAllImages();
				try {

					for (int t = 0; t < tmp.getSizeT(); t++) {
						for (int z = 0; z < tmp.getSizeZ(); z++) {

							IcyBufferedImage image = tmp.getImage(t, z);

							targetseq.setImage(t, z, image);

						}
					}
					//
				} finally {

					targetseq.endUpdate();

					// sequence.
				}
			}
			targetseq.setAutoUpdateChannelBounds(true);
			sourceseq.setAutoUpdateChannelBounds(true);
			new AnnounceFrame("Warning:" + target.getValue().getName() + "and " + source.getValue().getName()
					+ " have been converted to 8 bytes (to save memory in 3D)", 5);
			// TODO Add viewers for test of pickers?
			// boolean visible=true;
			// Viewer sourcevtk=new Viewer(sourceseq,visible);
			// VtkCanvas canvassourcevtk=new VtkCanvas(sourcevtk);
			// canvassourcevtk.
			// Change the z calibration in case of a 2D source image to be
			// registered on 3D
			if (sourceseq.getSizeZ() == 1) {
				sourceseq.setPixelSizeZ(targetseq.getPixelSizeZ());
			}

		}
		Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
		// copysource=SequenceUtil.getCopy(source.getValue());

		if (sourceseq == null)
			return;
		// sourceseq.getFirstViewer().getLutViewer().setAutoBound(false);

		backupsource = SequenceUtil.getCopy(sourceseq);
		bucalibx = backupsource.getPixelSizeX();
		bucaliby = backupsource.getPixelSizeY();
		bucalibz = backupsource.getPixelSizeZ();
		myoverlaysource = new VisiblepointsOverlay();
		myoverlaytarget = new VisiblepointsOverlay();
		myoverlayerror = new ErrorinPositionOverlay();
		myoverlaypredictederror = new PredictedErrorinPositionOverlay();
		messageSource = new MessageOverlay(
				"SourceImage: will be transformed. Do not add point here but drag the points added from target");
		messageTarget = new MessageOverlay("Target Message: add Roi points here");
		sourceseq.addOverlay(messageSource);
		targetseq.addOverlay(messageTarget);
		sourceseq.addOverlay(myoverlaysource);
		targetseq.addOverlay(myoverlaytarget);

		if (predictederrorselected) {
			source.getValue().addOverlay(myoverlaypredictederror);

		}
		if (overlayerrorselected) {
			source.getValue().addOverlay(myoverlayerror);
		}
		// To avoid overwriting LUT and metadata in the original filename
		// overlay.
		sourceseq.setName(sourceseq.getName() + " (transformed)");

		String name = sourceseq.getFilename() + "_transfo.xml";
		XMLFile = new File(name);
		sourceseq.setFilename(sourceseq.getName() + ".tif");
		Document myXMLdoc = XMLUtil.createDocument(true);
		Element transfoElement = XMLUtil.addElement(myXMLdoc.getDocumentElement(), "TargetSize");
		XMLUtil.setAttributeIntValue(transfoElement, "width", target.getValue().getWidth());
		XMLUtil.setAttributeIntValue(transfoElement, "height", target.getValue().getHeight());
		XMLUtil.setAttributeDoubleValue(transfoElement, "sx", target.getValue().getPixelSizeX());
		XMLUtil.setAttributeDoubleValue(transfoElement, "sy", target.getValue().getPixelSizeY());
		XMLUtil.setAttributeDoubleValue(transfoElement, "sz", target.getValue().getPixelSizeZ());
		if (mode3D) {
			XMLUtil.setAttributeIntValue(transfoElement, "nz", target.getValue().getSizeZ());
			
		}
		if (!XMLFile.exists())
			try {
				XMLFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		XMLUtil.saveDocument(myXMLdoc, XMLFile);
		System.out.println("Transformation will be saved as " + XMLFile.getPath());

		new AnnounceFrame("Select point on image" + target.getValue().getName() + ", then drag it on source image and RIGHT CLICK", 5);

		// targetseq.addListener(this);
		// if (flagRegister)
		// sourceseq.addListener(this);
		while (!stopFlag) {
			ThreadUtil.sleep(10);
		}
		targetseq.removeListener(this);
		sourceseq.removeListener(this);
		System.out.println("Listeners off now");
	}

	/**
	 * Method actually computing and applying the transformation: first back up
	 * the image to avoid cropping of the image in case of misplaced points.
	 * Write each transform to an xml file. TODO: add the 3D transform (checking
	 * vtk maybe)
	 */
	void ComputeTransfo() {
		// fiducialsvector(mode2D) OR fiducialsvector3D (mode3D)
		// could have been thinking differently

		if ((fiducialsvector.size() > 2) || (fiducialsvector3D.size() > 3)) {
			double back_up_pixelsizex = source.getValue().getPixelSizeX();
			double back_up_pixelsizey = source.getValue().getPixelSizeY();
			double back_up_pixelsizez = source.getValue().getPixelSizeZ();
			source.getValue().setAutoUpdateChannelBounds(false);
			source.getValue().beginUpdate();
			source.getValue().removeAllImages();
			if (backupsource == null) {
				MessageDialog.showDialog("Please press the Play button to initialize process first");
				return;
			}
			try {
				// final ArrayList<IcyBufferedImage> images =
				// sequence.getAllImage();

				for (int t = 0; t < backupsource.getSizeT(); t++) {
					for (int z = 0; z < backupsource.getSizeZ(); z++) {

						source.getValue().setImage(t, z, backupsource.getImage(t, z));

					}
				}
			}
			//

			finally {

				source.getValue().endUpdate();

				// sequence.
			}
			//source.getValue().setAutoUpdateChannelBounds(true);
			
			// we apply the previous combined transfo to the orginal image
			// before applying the new transfo in order to avoid bad cropping of
			// the pixels intensity values
			Document document = XMLUtil.loadDocument(XMLFile);
			SimilarityTransformation2D lasttransfo = null;
			if (mode3D == false) {
				Matrix combinedtransfobefore = getCombinedTransfo(document);
				SimilarityRegistrationAnalytic meanfiducialsalgo = new SimilarityRegistrationAnalytic();
				SimilarityTransformation2D newtransfo = meanfiducialsalgo.apply(fiducialsvector);
				lasttransfo = newtransfo;
				//double Sangle = newtransfo.getS();
				//double Cangle = newtransfo.getC();
				double dx = newtransfo.getdx();
				double dy = newtransfo.getdy();
				double scale = newtransfo.getscale();
				// write xml file
				Matrix transfo = newtransfo.getMatrix();
				writeTransfo(transfo, fiducialsvector.size());

				// combined the matrix and the new one in order to apply it
				// directly to the new image
				transfo = transfo.times(combinedtransfobefore);

				ImageTransformer mytransformer = new ImageTransformer();

				mytransformer.setImageSource(source.getValue());
				// mytransformer.setParameters(dx, dy, Sangle, Cangle, scale);
				mytransformer.setParameters(transfo);
				mytransformer.setDestinationsize(target.getValue().getWidth(), target.getValue().getHeight());
				mytransformer.run();
				
				
				// set the calibration to target calibration
				double pixelsizexum = target.getValue().getPixelSizeX();
				double pixelsizeyum = target.getValue().getPixelSizeY();
				source.getValue().setPixelSizeX(pixelsizexum);// TO DO rather by
																// scale
				source.getValue().setPixelSizeY(pixelsizeyum);
				double angleyz = Math.atan2(transfo.get(2, 1), transfo.get(2, 2));
				double anglexz = Math.atan2(-transfo.get(2, 0),
						Math.sqrt(transfo.get(2, 1) * transfo.get(2, 1) + transfo.get(2, 2) * transfo.get(2, 2)));
				double anglexy = Math.atan2(transfo.get(1, 0), transfo.get(0, 0));
				angleyz = Math.round(Math.toDegrees(angleyz) * 1000.0) / 1000.0;
				anglexz = Math.round(Math.toDegrees(anglexz) * 1000.0) / 1000.0;
				anglexy = Math.round(Math.toDegrees(anglexy) * 1000.0) / 1000.0;
				double dxt = Math.round(transfo.get(3, 0) * 1000.0) / 1000.0;
				double dyt = Math.round(transfo.get(3, 1) * 1000.0) / 1000.0;
				dx = Math.round(dx * 1000.0) / 1000.0;
				dy = Math.round(dy * 1000.0) / 1000.0;
				scale = Math.round(scale * 1000.0) / 1000.0;

				System.out.println("Total computed Translation x " + dxt + " Total Translation y " + dyt
						+ " angle Oz (in degrees) " + anglexy + " Scale " + scale);

				updateSourcePoints2D(newtransfo);
				updateRoi();
				new AnnounceFrame("Transformation Updated", 5);
			} else // mode3D
			{
				if (!(testcoplanarity(fiducialsvector3D) && fiducialsvector3D.size() < 6)) {
					SimilarityTransformation3D combinedtransfobefore = getCombinedTransfo3D(document);
					SimilarityRegistrationAnalytic3D meanfiducialsalgo = new SimilarityRegistrationAnalytic3D();

					SimilarityTransformation3D newtransfo = meanfiducialsalgo.apply(fiducialsvector3D,
							back_up_pixelsizex, back_up_pixelsizey, back_up_pixelsizez,
							target.getValue().getPixelSizeX(), target.getValue().getPixelSizeY(),
							target.getValue().getPixelSizeZ());

					// write xml file
					Matrix transfo = newtransfo.getMatrix();
					if (transfo.get(2, 2) != 0) {
						writeTransfo3D(newtransfo, fiducialsvector3D.size());
						transfo = transfo.times(combinedtransfobefore.getMatrix());

						Stack3DVTKTransformer transfoimage3D = new Stack3DVTKTransformer();
						transfoimage3D.setImageSource(source.getValue(), combinedtransfobefore.getorisizex(),
								combinedtransfobefore.getorisizey(), combinedtransfobefore.getorisizez());
						transfoimage3D.setDestinationsize(target.getValue().getSizeX(), target.getValue().getSizeY(),
								target.getValue().getSizeZ(), target.getValue().getPixelSizeX(),
								target.getValue().getPixelSizeY(), target.getValue().getPixelSizeZ());
						transfoimage3D.setParameters(transfo, newtransfo.getscalex(), newtransfo.getscalez());
						transfoimage3D.run();
						updateSourcePoints3D(newtransfo);
						updateRoi();
						double angleyz = Math.atan2(transfo.get(2, 1), transfo.get(2, 2));
						double anglexz = Math.atan2(-transfo.get(2, 0), Math
								.sqrt(transfo.get(2, 1) * transfo.get(2, 1) + transfo.get(2, 2) * transfo.get(2, 2)));
						double anglexy = Math.atan2(transfo.get(1, 0), transfo.get(0, 0));
						angleyz = Math.round(Math.toDegrees(angleyz) * 1000.0) / 1000.0;
						anglexz = Math.round(Math.toDegrees(anglexz) * 1000.0) / 1000.0;
						anglexy = Math.round(Math.toDegrees(anglexy) * 1000.0) / 1000.0;
						double dxt = Math.round(transfo.get(0, 3) * 1000.0) / 1000.0;
						double dyt = Math.round(transfo.get(1, 3) * 1000.0) / 1000.0;
						double dzt = Math.round(transfo.get(2, 3) * 1000.0) / 1000.0;
						double scalexy = Math.round(newtransfo.getscalex() * 1000.0) / 1000.0;
						double scalez = Math.round(newtransfo.getscalez() * 1000.0) / 1000.0;
						System.out.println("Total computed Translation x: " + dxt + "  y:" + dyt + "z: " + dzt
								+ " angle Oz: " + anglexy + " angle Oy: " + anglexz + " angle Ox: " + angleyz
								+ " Scale xy (in physical unit): " + scalexy + " Scale z:  " + scalez);
						new AnnounceFrame("Transformation Updated", 5);
					}
				} else {
					System.out.println("Instability: One more point");
					new AnnounceFrame(
							"The position of the points does not allow a correct 3D transform. \n You need at least 2 points in separate z (slice). \n You may want to consider a 2D transform (it will still transform the full stack).");
				}

			}
			if (monitor) {
				TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
				ComputeFRE.ReadFiducials(target.getValue());
				double[] f = ComputeFRE.PreComputeTRE();
				double FLEmax = maxdifferrorinnm();
				System.out.println("Max localization error FLE estimated " + FLEmax + " nm");
				if (this.monitortargetonsource) { // in that case we need to
													// update the position of
													// target
					Point2D testPoint = new Point2D.Double(this.xtarget, this.ytarget);
					lasttransfo.apply(testPoint);
					this.xtarget = (int) testPoint.getX();
					this.ytarget = (int) testPoint.getY();
				}
				double diameter = ComputeFRE.ComputeTRE(FLEmax, this.xtarget, this.ytarget, 0, f); // in
																									// nm
				this.listofNvalues.add(this.listofNvalues.size(), (double) this.targetpoints.length);
				this.listoftrevalues.add(this.listoftrevalues.size(), diameter);
				double[][] TREValues = new double[this.listofNvalues.size()][2];

				for (int i = 0; i < this.listofNvalues.size(); i++) {
					TREValues[i][0] = listofNvalues.get(i);
					TREValues[i][1] = listoftrevalues.get(i);
					System.out.println("N=" + TREValues[i][0] + ", TRE=" + TREValues[i][1]);
				}
				MonitorTargetPoint.UpdatePoint(TREValues);
			}
		} else {
			System.out.println("One more point"); // We did transform at the
													// beginning such that we
													// have images at the same
													// size to find the points
													// more easily.
			// target.getValue().addListener(this);
			if (mode3D) {
				new AnnounceFrame("No transformation will be computed with less than 4 points. You have placed "
						+ fiducialsvector3D.size() + " points", 2);
			} else {
				new AnnounceFrame("No transformation will be computed with less than 3 points. You have placed "
						+ fiducialsvector.size() + " points", 2);
			}

		}
		if (mode3D){
			Icy.getMainInterface().setSelectedTool(ROI3DPointPlugin.class.getName());
		}
		else{
			Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
		}
			// corrected for LUT adjustement
		source.getValue().getFirstViewer().getLutViewer().setAutoBound(false);
	}

	/**
	 * 
	 * @param fiducialsvector3d2
	 * @return true if one set of points (source or target) at least is
	 *         coplanar.
	 */
	private boolean testcoplanarity(Vector<PointsPair3D> fiducialsvector3d2) {
		boolean testsource = true;
		boolean testtarget = true;
		// check if at least one point source has a z different from the other
		// one,
		// and do the same for target points
		double zsource = fiducialsvector3d2.get(0).first.getZ();

		for (int i = 1; i < fiducialsvector3d2.size(); i++) {
			PointsPair3D currentpair = fiducialsvector3d2.get(i);
			if (currentpair.first.getZ() != zsource) {
				testsource = false;
				break;
			}

		}

		double ztarget = fiducialsvector3d2.get(0).second.getZ();
		for (int i = 1; i < fiducialsvector3d2.size(); i++) {
			PointsPair3D currentpair = fiducialsvector3d2.get(i);
			if (currentpair.second.getZ() != ztarget) {
				testtarget = false;
				break;
			}

		}

		return (testsource || testtarget);
	}

	private void writeTransfo3D(SimilarityTransformation3D newtransfo, int order) {
		// TODO Auto-generated method stub
		Matrix transfo = newtransfo.getMatrix();

		Document document = XMLUtil.loadDocument(XMLFile);
		Element transfoElement = XMLUtil.addElement(document.getDocumentElement(), "MatrixTransformation");

		XMLUtil.setAttributeIntValue(transfoElement, "order", order);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m00", transfo.get(0, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m01", transfo.get(0, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m02", transfo.get(0, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m03", transfo.get(0, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m10", transfo.get(1, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m11", transfo.get(1, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m12", transfo.get(1, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m13", transfo.get(1, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m20", transfo.get(2, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m21", transfo.get(2, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m22", transfo.get(2, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m23", transfo.get(2, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m30", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m31", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m32", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m33", 1);
		XMLUtil.setAttributeDoubleValue(transfoElement, "formerpixelsizeX", newtransfo.getorisizex());
		XMLUtil.setAttributeDoubleValue(transfoElement, "formerpixelsizeY", newtransfo.getorisizey());
		XMLUtil.setAttributeDoubleValue(transfoElement, "formerpixelsizeZ", newtransfo.getorisizez());
		XMLUtil.setAttributeValue(transfoElement, "process_date", new Date().toString());
		XMLUtil.saveDocument(document, XMLFile);
		System.out.println("Saved as" + XMLFile.getPath());
	}

	SimilarityTransformation3D getCombinedTransfo3D(Document document) {
		// the default value of orisizex has to the actual pixel size:
				// otherwise during the initialisation (i.e the first tranform
		// when getcombined transform has nothing to return
		double orisizex = source.getValue().getPixelSizeX();
		double orisizey = source.getValue().getPixelSizeY();
		double orisizez = source.getValue().getPixelSizeZ();
		if (XMLFile==null)
		{
			System.out.println("XMLFile Not created yet, return identity");
			Matrix CombinedTransfo = Matrix.identity(4, 4);
			

			SimilarityTransformation3D resulttransfo = new SimilarityTransformation3D(CombinedTransfo, orisizex, orisizey,
					orisizez);
			return resulttransfo;
			
		}
		if (document==null)
		{
			System.out.println("XMLFile Not created yet, return identity");
			Matrix CombinedTransfo = Matrix.identity(4, 4);
			

			SimilarityTransformation3D resulttransfo = new SimilarityTransformation3D(CombinedTransfo, orisizex, orisizey,
					orisizez);
			return resulttransfo;
			
		}
		Element root = XMLUtil.getRootElement(document);

		ArrayList<Element> transfoElementArrayList = XMLUtil.getElements(root, "MatrixTransformation");
		// int nbtransfo=transfoElementArrayList.size();
		ArrayList<Matrix> listoftransfo = new ArrayList<Matrix>();
		boolean firsttime = true;
		

		for (Element transfoElement : transfoElementArrayList) {
			double[][] m = new double[4][4];
			// int order = XMLUtil.getAttributeIntValue( transfoElement, "order"
			// , -1 ); //to be check for now only: has to be used!!!
			// the only different pixel size (i.e the orginal source size) is
			// given only at the first transformation
			if (firsttime) {
				orisizex = XMLUtil.getAttributeDoubleValue(transfoElement, "formerpixelsizeX", 0);
				orisizey = XMLUtil.getAttributeDoubleValue(transfoElement, "formerpixelsizeY", 0);
				orisizez = XMLUtil.getAttributeDoubleValue(transfoElement, "formerpixelsizeZ", 0);
				firsttime = false;
			}

			m[0][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m00", 0);
			m[0][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m01", 0);
			m[0][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m02", 0);
			m[0][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m03", 0);

			m[1][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m10", 0);
			m[1][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m11", 0);
			m[1][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m12", 0);
			m[1][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m13", 0);

			m[2][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m20", 0);
			m[2][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m21", 0);
			m[2][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m22", 0);
			m[2][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m23", 0);

			m[3][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m30", 0);
			m[3][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m31", 0);
			m[3][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m32", 0);
			m[3][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m33", 0);

			Matrix T = new Matrix(m);
			listoftransfo.add(T);

		}

		Matrix CombinedTransfo = Matrix.identity(4, 4);
		for (int i = 0; i < listoftransfo.size(); i++) {
			CombinedTransfo = listoftransfo.get(i).times(CombinedTransfo);
		}

		SimilarityTransformation3D resulttransfo = new SimilarityTransformation3D(CombinedTransfo, orisizex, orisizey,
				orisizez);
		return resulttransfo;

	}

	void updateSourcePoints3D(SimilarityTransformation3D newtransfo) {
		for (int i = 0; i < this.sourcepoints.length; i++) {
			PPPoint3D testPoint = new PPPoint3D(this.sourcepoints[i][0], this.sourcepoints[i][1],
					this.sourcepoints[i][2]);
			newtransfo.apply(testPoint);// get the output IN PHYSICAL UNIT!
			this.sourcepoints[i][0] = testPoint.getX() / source.getValue().getPixelSizeX();
			this.sourcepoints[i][1] = testPoint.getY() / source.getValue().getPixelSizeY();
			this.sourcepoints[i][2] = testPoint.getZ() / source.getValue().getPixelSizeZ();
		}

	}

	// this method append R, T and scale to an opened xml file
	// this one does not bear flipping.
	/*
	 * private void writeTransfo_Old(SimilarityTransformation2D newtransfo, int
	 * order) {
	 * 
	 * Matrix R = newtransfo.getR(); Matrix T = newtransfo.getT(); double scale
	 * = newtransfo.getscale();
	 * 
	 * Document document = XMLUtil.loadDocument(XMLFile); Element transfoElement
	 * = XMLUtil.addElement( document.getDocumentElement(),
	 * "MatrixTransformation");
	 * 
	 * XMLUtil.setAttributeIntValue(transfoElement, "order", order);
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m00", R.get(0, 0)
	 * scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m01", R.get(0,
	 * 1) scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m02",
	 * R.get(0, 2) scale); XMLUtil.setAttributeDoubleValue(transfoElement,
	 * "m03", T.get(0, 0));
	 * 
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m10", R.get(1, 0)
	 * scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m11", R.get(1,
	 * 1) scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m12",
	 * R.get(1, 2) scale); XMLUtil.setAttributeDoubleValue(transfoElement,
	 * "m13", T.get(1, 0));
	 * 
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m20", R.get(2, 0)
	 * scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m21", R.get(2,
	 * 1) scale); XMLUtil.setAttributeDoubleValue(transfoElement, "m22",
	 * R.get(2, 2) scale); XMLUtil.setAttributeDoubleValue(transfoElement,
	 * "m23", T.get(2, 0));
	 * 
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m30", 0);
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m31", 0);
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m32", 0);
	 * XMLUtil.setAttributeDoubleValue(transfoElement, "m33", 1);
	 * XMLUtil.setAttributeValue(transfoElement, "process_date", new
	 * Date().toString()); XMLUtil.saveDocument(document, XMLFile);
	 * 
	 * }
	 */
	/**
	 * this method append R, T and scale to an opened xml file
	 * 
	 * @param transfo
	 * @param order
	 */
	private void writeTransfo(Matrix transfo, int order) {

		// Matrix transfo = newtransfo.getMatrix();

		Document document = XMLUtil.loadDocument(XMLFile);
		if (document==null){
			MessageDialog.showDialog(
					"The document where to write the transfo could not be loaded:  \n "+ XMLFile.getPath() +"\n Check if the source image was saved on disk first, /n and if you have writing rights on the directory mentionned above",
					MessageDialog.QUESTION_MESSAGE);
			return;
		}
		Element transfoElement = XMLUtil.addElement(document.getDocumentElement(), "MatrixTransformation");

		XMLUtil.setAttributeIntValue(transfoElement, "order", order);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m00", transfo.get(0, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m01", transfo.get(0, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m02", transfo.get(0, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m03", transfo.get(0, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m10", transfo.get(1, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m11", transfo.get(1, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m12", transfo.get(1, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m13", transfo.get(1, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m20", transfo.get(2, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m21", transfo.get(2, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m22", transfo.get(2, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m23", transfo.get(2, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m30", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m31", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m32", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m33", 1);
		XMLUtil.setAttributeValue(transfoElement, "process_date", new Date().toString());
		XMLUtil.saveDocument(document, XMLFile);
		System.out.println("Transformation matrix as been saved as " + XMLFile.getPath());
		System.out.println("If there is no path indicated, it means it is in your ICY installation path");
	}

	/**
	 * This method apply the transform to 2D points in order to get the right
	 * place for the ROIs after a transform
	 * 
	 * @param newtransfo
	 */

	private void updateSourcePoints2D(SimilarityTransformation2D newtransfo) {

		for (int i = 0; i < this.sourcepoints.length; i++) {
			Point2D testPoint = new Point2D.Double(this.sourcepoints[i][0], this.sourcepoints[i][1]);
			newtransfo.apply(testPoint);
			this.sourcepoints[i][0] = testPoint.getX();
			this.sourcepoints[i][1] = testPoint.getY();
		}

	}

	/**
	 * Update the position of ROI points after a transform for exemple from the
	 * list of pair fiducials
	 */
	void updateRoi() {

		ArrayList<ROI> listfiducials = source.getValue().getROIs();

		ReOrder(listfiducials);
		// fiducials=new double[10][3];
		int i = -1;
		//System.out.println("True Z position (zd in roi xml):");
		for (ROI roi : listfiducials) {
			// roi=(myRoi3D)roi;
			i++;
			Point5D position = roi.getPosition5D();
			position.setX(this.sourcepoints[i][0]);
			position.setY(this.sourcepoints[i][1]);
			position.setZ(this.sourcepoints[i][2]);
			roi.setPosition5D(position); // should now copy zd as well
			System.out.println(roi.getName() + " " + this.sourcepoints[i][0] + " " + this.sourcepoints[i][1] + " "
					+ this.sourcepoints[i][2]);

		}

	}

	/**
	 * Not overwritten
	 */
	@Override
	public void clean() {
		// TODO Auto-generated by Icy4Eclipse what for??
	}

	/**
	 * track the point addition and their dragging
	 */
	@Override
	public void sequenceChanged(SequenceEvent event) {
		if (stopFlag == false) {
			if (event.getSequence() == target.getValue())
				if (event.getSourceType() == SequenceEventSourceType.SEQUENCE_ROI) {
					// System.out.println("event on target type ROI");
					if (event.getType() == SequenceEventType.ADDED) {
						target.getValue().removeListener(this);
						flagReadyToMove = false;
						//System.out.println("event on target type ROI ADDED");
						double z = target.getValue().getFirstViewer().getPositionZ(); // was
																// z
						ROI roi = (ROI) event.getSource();

						Point5D pos = roi.getPosition5D();
						// set z et recuperer
						pos.setZ(z);
						roi.setPosition5D(pos);

						int colornb = (int) Math.round(Math.random() * (Colortab.length));
						if (colornb > 8)
							colornb = 8;
						System.out.println("Selected color" + colornb);
						roi.setColor(Colortab[colornb]);
						roi.setName("Point " + target.getValue().getROIs().size());

						ROI roisource = roi.getCopy();
						// ROI roisource = new myRoi3D(roi);
						if (source.getValue() == null) {
							new AnnounceFrame("You've closed the source image");

							return;
						}
						int zs = source.getValue().getFirstViewer().getPositionZ(); // was
																					// get
																					// active
																					// viewer,
																					// changed
																					// to
																					// prevent
																					// bad
																					// placing
																					// of
																					// z

						Point5D pos2 = roisource.getPosition5D();
						// set z et recuperer
						pos2.setZ(zs);
						roisource.setPosition5D(pos2);
						if ((source.getValue().getWidth() != target.getValue().getWidth())
								|| (source.getValue().getHeight() != target.getValue().getHeight()))// source
																									// is
																									// different,
																									// meaning
																									// we
																									// had
						// less than 3 poinst, and if the size
						// is different,
						// so we position
						// arbitrarilly the point at the
						// middle.
						// This should happen ONLY the first time since then
						// images will be transformed
						{

							Point5D position = (Point5D) pos.clone();

							position.setLocation(source.getValue().getWidth() / 2, source.getValue().getHeight() / 2,
									source.getValue().getFirstViewer().getPositionZ(),
									source.getValue().getFirstViewer().getPositionT(), pos.getC());
							roisource.setPosition5D(position);

						}
						/*
						 * else // we still need to get the correct z in case of
						 * 3D { Point5D position = (Point5D) pos.clone();
						 * position.setLocation(pos.getX(), pos.getY(), source
						 * .getValue().getFirstViewer().getPositionZ(),
						 * source.getValue().getFirstViewer() .getPositionT(),
						 * pos.getC()); roisource.setPosition5D(position); }
						 */
						System.out.println("Adding Roi Landmark " + target.getValue().getROIs().size() + " on source");
						roisource.setName("Point " + target.getValue().getROIs().size());
						source.getValue().removeListener(this); // to avoid to
																// catch the add
																// roi as an
																// event....

						source.getValue().addROI(roisource);

						/*
						 * vtkActor sphereActor= new vtkActor();
						 * 
						 * sphereActor.SetPosition(roisource.getPosition5D().
						 * getX() * source.getValue().getPixelSizeX(),
						 * roisource.getPosition5D().getY() *
						 * source.getValue().getPixelSizeY(),
						 * roisource.getPosition5D().getZ() *
						 * source.getValue().getPixelSizeZ());
						 */

						roisource.setStroke(9); // change size

						// source.getValue().setSelectedROI(roisource);
						roisource.setFocused(false);
						flagReadyToMove = true;
						done = false;

						source.getValue().addListener(this);

					}
				}

			if (flagReadyToMove) {

				if (event.getSequence() == source.getValue())

					if (event.getSourceType() == SequenceEventSourceType.SEQUENCE_ROI) {
						// System.out.println("event on SOURCE type ROI");

						if (event.getType() == SequenceEventType.CHANGED) {
							//System.out.println("event on SOURCE type ROI CHANGED");
							
							
							boolean test= ((ROI)event.getSource()).isSelected()||((ROI)event.getSource()).isFocused();
							//System.out.println(test);
							ThreadUtil.sleep(10);
							
							if (test) {
								//System.out.println("Roi is still updated");
								 ThreadUtil.sleep(1);
								
							} else {
								target.getValue().addListener(this);
								source.getValue().removeListener(this);

								if (!done) {
									ThreadUtil.bgRunSingle(transformer);
									done = true;

								}
							}

						}

					}
			}
		}
		/**
		 * else // flage not ready to move {
		 * //source.getValue().addListener(this); if (event.getSequence() ==
		 * source.getValue()){
		 * 
		 * if (event.getSourceType() == SequenceEventSourceType.SEQUENCE_ROI) {
		 * if (event.getType() == SequenceEventType.ADDED) {
		 * System.out.println("added"); }
		 * 
		 * } } }
		 **/

	}

	/**
	 * not overwritten
	 */
	@Override
	public void sequenceClosed(Sequence sequence) {
		// TODO Auto-generated method stub

	}

	/**
	 * Compute the combined transform from all previous transform step stored in
	 * the xml, in order to apply it once only to avoid interpolation errors
	 * 
	 * @param document
	 * @return
	 */
	public Matrix getCombinedTransfo(Document document) {
		// to fix the java null exception
		if (XMLFile==null)
		{
			System.out.println("XMLFile Not created yet, return identity");
			return Matrix.identity(4, 4);
		}
		if (document==null)
		{
			System.out.println("XMLFile Not created yet, return identity");
			return Matrix.identity(4, 4);
		}
		Element root = XMLUtil.getRootElement(document);
		// V1.0.7 add securities
		if (root == null) {
			// @TODO : verifier le java null exception ici.
			new AnnounceFrame("Could not find " + XMLFile.getName() + ". Check the CONSOLE output.", 5);
			System.out.println("The file " + XMLFile.getName()
					+ "was not found , check that you have writing right in the directory");
			System.out.println(
					"If no directory for this file is indicated, check that you have writing rights to the ICY directory (ex: C:/ICY)");
			System.out.println(
					"Reminder: as indicated on ICY download webpage, ICY should not be copy under the Program files directory");
			
		}
		
		ArrayList<Element> transfoElementArrayList = XMLUtil.getElements(root, "MatrixTransformation");
		if (transfoElementArrayList == null) {
			new AnnounceFrame(
					"You have likely chosen a wrong file, it should be suffixed with _transfo.xml, not only .xml", 5);

		}
		// int nbtransfo=transfoElementArrayList.size();
		ArrayList<Matrix> listoftransfo = new ArrayList<Matrix>();
		for (Element transfoElement : transfoElementArrayList) {
			double[][] m = new double[4][4];
			// int order = XMLUtil.getAttributeIntValue( transfoElement, "order"
			// , -1 ); //to be check for now only: has to be used!!!

			m[0][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m00", 0);
			m[0][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m01", 0);
			m[0][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m02", 0);
			m[0][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m03", 0);

			m[1][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m10", 0);
			m[1][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m11", 0);
			m[1][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m12", 0);
			m[1][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m13", 0);

			m[2][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m20", 0);
			m[2][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m21", 0);
			m[2][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m22", 0);
			m[2][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m23", 0);

			m[3][0] = XMLUtil.getAttributeDoubleValue(transfoElement, "m30", 0);
			m[3][1] = XMLUtil.getAttributeDoubleValue(transfoElement, "m31", 0);
			m[3][2] = XMLUtil.getAttributeDoubleValue(transfoElement, "m32", 0);
			m[3][3] = XMLUtil.getAttributeDoubleValue(transfoElement, "m33", 0);

			Matrix T = new Matrix(m);
			listoftransfo.add(T);

		}
		Matrix CombinedTransfo = Matrix.identity(4, 4);
		for (int i = 0; i < listoftransfo.size(); i++) {
			CombinedTransfo = listoftransfo.get(i).times(CombinedTransfo);
		}
		return CombinedTransfo;
	}

	/**
	 * Overwritten method: when pressing the stop button, an overlay is created.
	 * TODO: add the error map?
	 */
	@Override
	public void stopExecution() {
		// TODO Auto-generated method stub
		stopFlag = true;
		try {
			choiceinputsection.setEnabled(true);
			rigidspecificbutton.reshowspecificrigidbutton();
			
			// Display the Total Final Transformation For Information
			Document document = XMLUtil.loadDocument(XMLFile);
			if ((mode3D==false)&&(nonrigid==false)){
			Matrix combinedtransfobefore = getCombinedTransfo(document);
			System.out.println("Here is transformation resulting from combined operation (between Play and Stop):");
			combinedtransfobefore.print(3, 2);
			double scale_x=Math.sqrt(Math.pow(combinedtransfobefore.get(0, 0),2)+Math.pow(combinedtransfobefore.get(0, 1),2));
			//double scale_y=Math.sqrt(Math.pow(combinedtransfobefore.get(1, 0),2)+Math.pow(combinedtransfobefore.get(1, 1),2));
			System.out.println("Estimated Scaling :" + (double)(Math.round(scale_x*100))/100);
			}
			if ((mode3D==true)&&(nonrigid==false)){
				SimilarityTransformation3D combinedtransfobefore = getCombinedTransfo3D(document);
				System.out.println("Here is transformation resulting from combined operation (between Play and Stop):");
				combinedtransfobefore.getMatrix().print(3, 2);
			}
			if (source.getValue() != null)
				source.getValue().removeListener(this);
			if (target.getValue() != null)
				target.getValue().removeListener(this);
			ThreadUtil.invokeLater(new Runnable() {
				public void run() {

					if ((source.getValue() != null) && (target.getValue() != null)) {

						Sequence Result1 = SequenceUtil.extractSlice(source.getValue(),
								source.getValue().getFirstViewer().getPositionZ());
						Result1 = SequenceUtil.extractFrame(Result1, source.getValue().getFirstViewer().getPositionT());
						LUT sourcelut = source.getValue().getFirstViewer().getLut();
						int sourcenchannel = source.getValue().getSizeC();
						LUT targetlut = target.getValue().getFirstViewer().getLut();
						int targetnchannel = target.getValue().getSizeC();
						// we want to merge the same z, arbitrarily the one of
						// source
						Sequence Result2 = null;
						if (target.getValue().getSizeZ() >= source.getValue().getSizeZ()) {
							Result2 = SequenceUtil.extractSlice(target.getValue(),
									source.getValue().getFirstViewer().getPositionZ());
						} else {
							Result2 = SequenceUtil.extractSlice(target.getValue(),
									target.getValue().getFirstViewer().getPositionZ());
						}

						Result2 = SequenceUtil.extractFrame(Result2, target.getValue().getFirstViewer().getPositionT());
						Result2.dataChanged();
						// Viewer Result2v=new Viewer(Result2); //
						if (Result1.getDataType_() != Result2.getDataType_())
							Result2 = SequenceUtil.convertToType(Result2, Result1.getDataType_(), true); // warning:
																											// bug
																											// in
																											// icy
																											// if
																											// converttotype
																											// to
																											// the
																											// same
																											// type.
																											// should
																											// be
																											// ok
																											// here
						Result2.dataChanged();
						// Viewer Result3v=new Viewer(Result2); //
						// Result2v.getLut().copyFrom(targetlut);
						// Result2v.close();

						Sequence[] sequences = new Sequence[Result1.getSizeC()+Result2.getSizeC()];
						for (int c=0;c<Result1.getSizeC();c++)
							sequences[c]=SequenceUtil.extractChannel(Result1, c);
						for (int c=Result1.getSizeC();c<Result1.getSizeC()+Result2.getSizeC();c++)
							sequences[c]=SequenceUtil.extractChannel(Result2, c-Result1.getSizeC());
						boolean fillEmpty=false;
						boolean rescale=false;
						int[] channels=new int[sequences.length];
						Sequence Result = SequenceUtil.concatC(sequences, channels, fillEmpty, rescale, null);
						
						Viewer vout = new Viewer(Result);
						
						Result.setName("Overlayed");
						for (int c = 0; c < sourcenchannel; c++)
							vout.getLut().getLutChannel(c).getColorMap()
									.copyFrom(sourcelut.getLutChannel(c).getColorMap());

						for (int c = 0; c < targetnchannel; c++)
							vout.getLut().getLutChannel(sourcenchannel + c).getColorMap()
									.copyFrom(targetlut.getLutChannel(c).getColorMap());

						if (mode3D)// merge data and show source and target original with point on it.
							new AnnounceFrame(
									"Only the current z have been overlayed. Use the Merge Channel option if you want to create an overlay of the full stacks",
									5);
						else
							new AnnounceFrame(
									"The current views of both source and target image have been overlayed. Save it if you want to keep it. No further transform was done",
									5);
						/*
						 * File file=new File(
						 * ApplicationPreferences.getPreferences().node(
						 * "frame/imageLoader").get("path",
						 * ".")+source.getValue().getFilename()); boolean
						 * multipleFiles=false; boolean showProgress=true;
						 * Saver.save(source.getValue(), file, multipleFiles,
						 * showProgress);
						 */

						source.getValue().removeOverlay(myoverlaysource);
						source.getValue().removeOverlay(messageSource);
						target.getValue().removeOverlay(myoverlaytarget);
						target.getValue().removeOverlay(messageTarget);
						source.getValue().removeOverlay(myoverlayerror);
						source.getValue().removeOverlay(myoverlaypredictederror);
						checknonRigid();
					}

				}

			});
		} catch (Exception e) {
			System.out.println("byebye");
		}
	}

	private void checknonRigid() {
		// TODO add a check on TRE VS FRE
		if (nonrigid == false) {
			if (sourcepoints != null) {
				boolean needed = CheckTREvsFRE();
				// boolean needed=true;

				if (needed) {
					if (sourcepoints.length > 4) {
						MessageDialog.showDialog(
								" Based on the discrepancy between observed error and predicted error, \n computed from the landmark configuration you have used,\n Either this image DOES require deformable registration, Either at least one landmark is (really) badly placed.\n You can use the \"show difference in position\" to detect it. \n Check the position of your landmarks pair,\n select \"Non Rigid Correction\" in the list of transform, \n and click on update transform",
								MessageDialog.QUESTION_MESSAGE);
					}
					/*
					 * NonRigidTranformationVTK nrtransfo=new
					 * NonRigidTranformationVTK();
					 * 
					 * GetSourcePointsfromROI(); GetTargetPointsfromROI();
					 * 
					 * nrtransfo.setImageSourceandpoints(source.getValue(),
					 * sourcepoints);
					 * nrtransfo.setImageTargetandpoints(target.getValue(),
					 * targetpoints);
					 * 
					 * nrtransfo.run();
					 * 
					 * updateRoi(); new AnnounceFrame(
					 * "Non Rigid Transformation Updated",5);
					 */

				} else {
					if (sourcepoints.length > 4) {
						MessageDialog.showDialog(
								"Apparently this image does not required deformable registration, at least in the area where points where placed \n if you want to reach a better accuracy in the alignment, add more points.",
								MessageDialog.QUESTION_MESSAGE);
					}
					/*
					 * else{ MessageDialog.showDialog(
					 * "not enough point to make conclusions about the type of transfo"
					 * ); }
					 */
				}
			}
		}
	}

	/**
	 * 
	 * @return max error between point localisation on registered image, or
	 *         200nm (for fluo resolution) OR the pixel size by default if no
	 *         point pair. Should be expressed in nm considered as the FLE
	 *         (fiducial localization error)
	 */
	public double maxdifferrorinnm() {
		// TODO Auto-generated method stub
		// the min localization error is one pixel or the resolution of
		// fluorescence
		/*
		 * double error = Math.max(source.getValue().getPixelSizeX(),
		 * target.getValue().getPixelSizeX()); error=error*1000; // in nm, was
		 * in um error=Math.max(error, 200);
		 */
		if (sourcepoints == null) {
			System.err.println("Please initialize EasyClem first by pressing the Play button");
			return 0.0;
		}
		if (sourcepoints.length < 5) {// then the points are perfectly
										// registered which may be a non sense
										// from FLE,
			// we then assume an error of 20 pixels
			double error = Math.max(source.getValue().getPixelSizeX(), target.getValue().getPixelSizeX());
			error = 20 * error * 1000; // in nm, was in um
			error = Math.max(200, error);
			error = Math.min(1000, error);
			return error;
		}
		double error = 200; // this is the min error in fluorescence
		if ((sourcepoints != null) && (targetpoints.length == sourcepoints.length)) {

			if (mode3D == false) {
				fiducialsvector = createVectorfromdoublearray(sourcepoints, targetpoints);
				double newerror = 0;
				// ReOrder(fiducialsvector);
				for (int index = 0; index < fiducialsvector.size(); index++) {

					newerror += fiducialsvector.get(index).getDiffinpixels() * source.getValue().getPixelSizeX() * 1000;

				}
				newerror = newerror / fiducialsvector.size();
				// if (error>100/(source.getValue().getPixelSizeX()*1000))
				// //minimal fidulcial localisation error is the fluorecsnce
				// limitation
				// error=error*source.getValue().getPixelSizeX()*1000;// the
				// pixel size is returned in um and we want in in nm.
				if (newerror > error)
					error = newerror;
			} else// mode3D
			{
				fiducialsvector3D = createVectorfromdoublearray3D(sourcepoints, targetpoints);
				double newerror = 0;
				for (int index = 0; index < fiducialsvector3D.size(); index++) {

					newerror = Math.sqrt(fiducialsvector3D.get(index).getDiffx_squared_inpixels()
							* source.getValue().getPixelSizeX() * 1000
							+ fiducialsvector3D.get(index).getDiffy_squared_inpixels()
									* source.getValue().getPixelSizeY() * 1000
							+ fiducialsvector3D.get(index).getDiffz_squared_inpixels()
									* source.getValue().getPixelSizeZ() * 1000);// NOT
																				// GOOD
																				// SHOULD
																				// BE
																				// CORRECTED
																				// IN
																				// 3D

				}
				newerror = newerror / fiducialsvector3D.size();
				if (newerror > error)
					error = newerror;
			}

		}

		return error;
	}

	private boolean CheckTREvsFRE() {
		// For each ROI
		boolean check = false;
		// Compute FRE and compute TRE
		// return true when one has a tre > observed error
		double error = 0; // in nm
		double predictederror = 0; // in nm

		double FLEmax = maxdifferrorinnm();
		System.out.println("Max localization error FLE estimated " + FLEmax + " nm");
		TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
		ComputeFRE.ReadFiducials(target.getValue());
		double[] f = ComputeFRE.PreComputeTRE();
		GetSourcePointsfromROI();
		GetTargetPointsfromROI();
		if ((sourcepoints != null) || (targetpoints != null)) {
			if (targetpoints.length == sourcepoints.length) {
				if (mode3D == false) {
					ArrayList<ROI> listfiducials = source.getValue().getROIs();
					ReOrder(listfiducials);

					fiducialsvector = createVectorfromdoublearray(sourcepoints, targetpoints);
					for (int index = 0; index < fiducialsvector.size(); index++) {
						error = fiducialsvector.get(index).getDiffinpixels();
						String name = listfiducials.get(index).getName();

						error = error * source.getValue().getPixelSizeX() * 1000; // in
																					// um
																					// ,
																					// to
																					// be
																					// converted
																					// in
																					// nm
						predictederror = ComputeFRE.ComputeTRE(FLEmax, (int) fiducialsvector.get(index).first.getX(),
								(int) fiducialsvector.get(index).first.getY(), 0, f);
						System.out.println(
								name + " Discrepancy in nm: " + error + "vs Predicted error in nm: " + predictederror);
						if (error > predictederror)
							check = true;
					}
				} else// mode3D
				{
					fiducialsvector3D = createVectorfromdoublearray3D(sourcepoints, targetpoints);
					for (int index = 0; index < fiducialsvector3D.size(); index++) {

						error = Math.sqrt(Math
								.pow((fiducialsvector3D.get(index).getfirstxinpixels()
										- fiducialsvector3D.get(index).getsecondxinpixels())
										* source.getValue().getPixelSizeX(), 2)
								+ Math.pow((fiducialsvector3D.get(index).getfirstyinpixels()
										- fiducialsvector3D.get(index).getsecondyinpixels())
										* source.getValue().getPixelSizeY(), 2)
								+ Math.pow((fiducialsvector3D.get(index).getfirstzinpixels()
										- fiducialsvector3D.get(index).getsecondzinpixels())
										* source.getValue().getPixelSizeZ(), 2));

						error = error * 1000; // in um , to be converted in nm
						predictederror = ComputeFRE.ComputeTRE(FLEmax,
								(int) fiducialsvector3D.get(index).getfirstxinpixels(),
								(int) fiducialsvector3D.get(index).getfirstyinpixels(),
								(int) fiducialsvector3D.get(index).getfirstzinpixels(), f);
						System.out.println("Point " + (index + 1) + "Discrepancy in nm: " + error
								+ "vs Predicted error in nm: " + predictederror);
						if (error > predictederror)
							check = true;
					}
				}
			}
		}
		return check; // error was never above predicted error

	}
}
