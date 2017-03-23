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
 **/


package plugins.perrine.easyclemv0;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;


import Jama.Matrix;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.util.GuiUtil;

import icy.roi.ROI;
import icy.roi.ROIUtil;
import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
import icy.type.point.Point5D;

import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;

// mode 3D is not implemented here
public class ComputeLeaveOneOut extends EzPlug {

	private EzVarSequence source;
	private EzVarSequence target;
	Sequence backupsource;
	EzVarText choiceinputsection = new EzVarText("I want to study the transformation in:",
			new String[] { "Rigid (or affine)","non Rigid)"}, 0, false);
	private double[][] sourcepoints;
	private double[][] targetpoints;
	private double[][] backuptargetpoints;
	private double[][] backupsourcepoints;
	private boolean mode3D=false;
	private Vector<PointsPair> fiducialsvector;
	private Vector<PointsPair3D> fiducialsvector3D;
	private ArrayList<ROI> backuproitarget;
	private ArrayList<ROI> backuproisource;
	private XYSeries curve= new XYSeries("TRE vs Discrepancy");
	JPanel mainPanel = GuiUtil.generatePanel("Graph");
	IcyFrame mainFrame = GuiUtil.generateTitleFrame("Target Registration Error (predicted)", mainPanel, new Dimension(300, 100), true, true, true,
	            true);
		
	
	
	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		// Rigid case only for now
		// step 1: backup source sequence, and backup ROIs
		backupsource = SequenceUtil.getCopy(source.getValue());
		GetTargetPointsfromROI();
		GetSourcePointsfromROI();
		this.backuproitarget=target.getValue().getROIs();
		this.backuproisource=source.getValue().getROIs();
		this.backuptargetpoints=this.targetpoints;
		this.backupsourcepoints=this.sourcepoints;
		// Step 2: Compute transform with all points
		if (sourcepoints.length != targetpoints.length) {
			System.out.println("source points different from target point");
			return;

		} 
		if (mode3D == false) {
			fiducialsvector = createVectorfromdoublearray(
					sourcepoints, targetpoints);
			fiducialsvector3D = new Vector<PointsPair3D>();
		} else {
			fiducialsvector3D = createVectorfromdoublearray3D(
					sourcepoints, targetpoints);
			fiducialsvector = new Vector<PointsPair>();
		}
		ComputeTransfo(); 
		// Step 3: Get discrepancy for all points + TRE for all points 
		CheckTREvsFRE() ;
		// Step 4: For each Roi, Reload original sequence and back up roi , remove the same Roi, 
		for(int p=0; p<this.backupsourcepoints.length;p++){
			target.getValue().removeAllROI();
			source.getValue().removeAllROI();
			target.getValue().addROIs(this.backuproitarget, false);
			source.getValue().addROIs(this.backuproisource, false);
			ArrayList<ROI> listfiducialst = this.backuproitarget;
			ReOrder(listfiducialst);
			ArrayList<ROI> listfiducialss = this.backuproisource;
			ReOrder(listfiducialss);
			target.getValue().removeROI(listfiducialst.get(p));
			source.getValue().removeROI(listfiducialss.get(p));
			GetTargetPointsfromROI();
			GetSourcePointsfromROI();
			// recompute the new transfo
			if (mode3D == false) {
				fiducialsvector = createVectorfromdoublearray(
					sourcepoints, targetpoints);
			fiducialsvector3D = new Vector<PointsPair3D>();
			} else {
				fiducialsvector3D = createVectorfromdoublearray3D(
					sourcepoints, targetpoints);
				fiducialsvector = new Vector<PointsPair>();
			}	
		SimilarityTransformation2D newtransfo = ComputeTransfo();
		// Step 5: compute the discrepancy after registration with the other , and compute TRE for this point
		CheckTREvsFRE();
		//update the left-out point position
		Point2D testPoint=new Point2D.Double(this.backupsourcepoints[p][0], this.backupsourcepoints[p][1]);
		newtransfo.apply(testPoint);
		PointsPair outpoint=new PointsPair(
				new Point2D.Double(this.backupsourcepoints[p][0], this.backupsourcepoints[p][1]),
				new Point2D.Double(this.backuptargetpoints[p][0], this.backuptargetpoints[p][1]));
		CheckTREvsFRE(outpoint,listfiducialst.get(p).getName());
		
		
	}
		final XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(curve);
		// Step 6: write it/save it/ plot it
        JFreeChart jfreechart=CreateChart(dataset);
		ChartPanel  chartPanel = new ChartPanel(jfreechart);
	    chartPanel.setFillZoomRectangle(true);
	    chartPanel.setMouseWheelEnabled(true);
	    chartPanel.setPreferredSize(new Dimension(500, 270));
	    mainPanel.add(chartPanel);

	    mainFrame.pack();
	        
	    addIcyFrame(mainFrame);
	        
	    mainFrame.setVisible(true);
	    mainFrame.center();
	    mainFrame.requestFocus();
	}

	private static JFreeChart CreateChart(XYSeriesCollection dataset) {
		// TODO Auto-generated method stub
		 JFreeChart chart = ChartFactory.createXYLineChart(
	            "TRE vs Discrepancy ",      // chart title
	            "TRE in nm",                      // x axis label
	            "Discrepancy in nm",                      // y axis label
	            dataset,                  // data
	            PlotOrientation.VERTICAL,
	           false,                     // include legend
	            true,                     // tooltips
	            false                     // urls
	        );
		 chart.setBackgroundPaint(Color.white);
	        XYPlot xyplot = (XYPlot) chart.getPlot();
	        xyplot.setInsets(new RectangleInsets(5D, 5D, 5D, 20D));
	        xyplot.setBackgroundPaint(Color.lightGray);
	        xyplot.setAxisOffset(new RectangleInsets(5D, 5D, 5D, 5D));
	        xyplot.setDomainGridlinePaint(Color.white);
	        xyplot.setRangeGridlinePaint(Color.white);
	        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer( );
	        renderer.setSeriesPaint( 0 , Color.RED );
	        renderer.setSeriesStroke( 0 , new BasicStroke( 4.0f ) );
	        renderer.setSeriesLinesVisible(0, false);
	        xyplot.setRenderer(renderer);
	        NumberAxis numberaxis = (NumberAxis) xyplot.getRangeAxis();
	        
	        numberaxis.setAutoRangeIncludesZero(true);
	        numberaxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        return chart;

		
	}

	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
		EzLabel textinfo1 = new EzLabel(
				"Give information about error computation, usind leave one out as well.");
		
		
		source = new EzVarSequence("Select Source Image ");

		target = new EzVarSequence("Select Target Image ");

		
		addEzComponent(source);
		addEzComponent(target);
		addEzComponent(textinfo1);
		addEzComponent(choiceinputsection);
		
	}
	/**
	 * This method will create an ordered array for the target points
	 */
		void GetTargetPointsfromROI() {
			ArrayList<ROI> listfiducials = target.getValue().getROIs();
			ReOrder(listfiducials);
			this.targetpoints = new double[listfiducials.size()][3];

			int i = -1;
			for (ROI roi : listfiducials) {
				i++;

				Point5D p3D = ROIUtil.getMassCenter(roi);
				if (Double.isNaN(p3D.getX()))
					p3D = roi.getPosition5D(); // some Roi does not have gravity
												// center such as points
				this.targetpoints[i][0] = p3D.getX();
				this.targetpoints[i][1] = p3D.getY();
				this.targetpoints[i][2] = p3D.getZ();
				//if (target.getValue().getSizeZ()==1){
				//	this.targetpoints[i][2] =1.0;
				//}
				//else{
					this.targetpoints[i][2]=p3D.getZ();
				//}
			}
		}
		/**
		 * This method will create an ordered array for the source points
		 */
		void GetSourcePointsfromROI() {
			if (source.getValue() == null) {
				MessageDialog.showDialog("Make sure source image is openned");
				return;
			}
			ArrayList<ROI> listfiducials = source.getValue().getROIs();
			ReOrder(listfiducials);
			// ORDER ROI by name
			this.sourcepoints = new double[listfiducials.size()][3];
			// fiducials=new double[10][3];
			int i = -1;
			for (ROI roi : listfiducials) {
				i++;

				Point5D p3D = ROIUtil.getMassCenter(roi);
				if (Double.isNaN(p3D.getX()))
					p3D = roi.getPosition5D(); // some Roi does not have gravity
												// center such as points
				this.sourcepoints[i][0] = p3D.getX();
				this.sourcepoints[i][1] = p3D.getY();
				//if (source.getValue().getSizeZ()==1){
				//	this.sourcepoints[i][2] =1.0;
				//}
				//else{
					this.sourcepoints[i][2]=p3D.getZ();
				//}
					                  
			}

		}
		private void ReOrder(ArrayList<ROI> listfiducials) {

			int longueur = listfiducials.size();
			ROI tampon;
			boolean permut;

			do {

				permut = false;
				for (int i = 0; i < longueur - 1; i++) {

					if (listfiducials.get(i).getName()
							.compareTo(listfiducials.get(i + 1).getName()) > 0) {

						tampon = listfiducials.get(i);
						listfiducials.set(i, listfiducials.get(i + 1));
						listfiducials.set(i + 1, tampon);
						permut = true;
					}
				}
			} while (permut);

		}
		/**
		 * Warning modified to output new transfo, plus no need to apply the transfo to the image.
		 */
		SimilarityTransformation2D ComputeTransfo() {
			// fiducialsvector(mode2D) OR fiducialsvector3D (mode3D)
			// could have been thinking differently
			SimilarityTransformation2D newtransfo = null ;
			if ((fiducialsvector.size() > 2) || (fiducialsvector3D.size() > 3)) {
				double back_up_pixelsizex=source.getValue().getPixelSizeX();
				double back_up_pixelsizey=source.getValue().getPixelSizeY();
				double back_up_pixelsizez=source.getValue().getPixelSizeZ();
				/*source.getValue().beginUpdate();
				source.getValue().removeAllImages();
				if (backupsource == null) {
					MessageDialog
							.showDialog("Please press the Play button to initialize process first");
					return null;
				}
				try {
					// final ArrayList<IcyBufferedImage> images =
					// sequence.getAllImage();

					for (int t = 0; t < backupsource.getSizeT(); t++) {
						for (int z = 0; z < backupsource.getSizeZ(); z++) {

							source.getValue().setImage(t, z,
									backupsource.getImage(t, z));

						}
					}
				}
				//

				finally {

					source.getValue().endUpdate();

					// sequence.
				}*/
				// we apply the previous combined transfo to the orginal image
				// before applying the new transfo in order to avoid bad cropping of
				// the pixels intensity values
				
				if (mode3D == false) {
					
					SimilarityRegistrationAnalytic meanfiducialsalgo = new SimilarityRegistrationAnalytic();
					newtransfo = meanfiducialsalgo.apply(fiducialsvector);
					
					double Sangle = newtransfo.getS();
					double Cangle = newtransfo.getC();
					double dx = newtransfo.getdx();
					double dy = newtransfo.getdy();
					double scale = newtransfo.getscale();
					// write xml file
					Matrix transfo = newtransfo.getMatrix();

					/*ImageTransformer mytransformer = new ImageTransformer();

					mytransformer.setImageSource(source.getValue());
					
					mytransformer.setParameters(transfo);
					mytransformer.setDestinationsize(target.getValue().getWidth(),
							target.getValue().getHeight());
					mytransformer.run();*/
					// set the calibration to target calibration
					double pixelsizexum = target.getValue().getPixelSizeX();
					double pixelsizeyum = target.getValue().getPixelSizeY();
					source.getValue().setPixelSizeX(pixelsizexum);//TO DO rather by scale
					source.getValue().setPixelSizeY(pixelsizeyum);
					
					
					updateSourcePoints2D(newtransfo);
					updateRoi();
					//new AnnounceFrame("Transformation Updated",5);
				} else // mode3D
				{
					if (!(testcoplanarity(fiducialsvector3D)&&fiducialsvector3D.size()<6)){
						
						SimilarityRegistrationAnalytic3D meanfiducialsalgo = new SimilarityRegistrationAnalytic3D();


						SimilarityTransformation3D newtransfo3 = meanfiducialsalgo.apply(fiducialsvector3D, back_up_pixelsizex,back_up_pixelsizey,back_up_pixelsizez, target.getValue().getPixelSizeX(), target.getValue().getPixelSizeY(),target.getValue().getPixelSizeZ());

						// write xml file
						Matrix transfo = newtransfo3.getMatrix();
						if (transfo.get(2, 2)!=0){
						
							

							/*Stack3DVTKTransformer transfoimage3D=new Stack3DVTKTransformer();
							transfoimage3D.setImageSource(source.getValue(),source.getValue().getSizeX(),source.getValue().getSizeY(), source.getValue().getSizeZ());
							transfoimage3D.setDestinationsize(target.getValue().getSizeX(), target.getValue().getSizeY(), target.getValue().getSizeZ(),
									target.getValue().getPixelSizeX(), target.getValue().getPixelSizeY(), target.getValue().getPixelSizeZ());
							transfoimage3D.setParameters(transfo,newtransfo3.getscalex(),newtransfo3.getscalez());
							transfoimage3D.run();*/
							updateSourcePoints3D(newtransfo3);
							updateRoi();
							/*double angleyz=Math.atan2(transfo.get(2, 1), transfo.get(2, 2));
							double anglexz=Math.atan2(-transfo.get(2, 0), Math.sqrt(transfo.get(2, 1)*transfo.get(2, 1)+transfo.get(2, 2)*transfo.get(2, 2)));
							double anglexy=Math.atan2(transfo.get(1, 0), transfo.get(0, 0));
							angleyz=Math.round(Math.toDegrees(angleyz) * 1000.0) / 1000.0;
							anglexz=Math.round(Math.toDegrees(anglexz) * 1000.0) / 1000.0;
							anglexy=Math.round(Math.toDegrees(anglexy) * 1000.0) / 1000.0;
							double dxt=Math.round(transfo.get(0, 3) * 1000.0) / 1000.0;
							double dyt=Math.round(transfo.get(1, 3) * 1000.0) / 1000.0;
							double dzt=Math.round(transfo.get(2, 3) * 1000.0) / 1000.0;
							double scalexy=Math.round(newtransfo3.getscalex() * 1000.0) / 1000.0;
							double scalez=Math.round(newtransfo3.getscalez() * 1000.0) / 1000.0;
							System.out.println("Total computed Translation x: " + dxt+ "  y:" + dyt +"z: "+dzt+ " angle Oz: "
									+anglexy + " angle Oy: " + anglexz +  " angle Ox: "+ angleyz+" Scale xy (in physical unit): " + scalexy+" Scale z:  " + scalez);
							new AnnounceFrame("Transformation Updated",5);*/
						}
					}
					else
					{
						System.out.println("Instability: One more point");
						new AnnounceFrame(
								"The position of the points does not allow a correct 3D transform. \n You need at least 2 points in separate z (slice). \n You may want to consider a 2D transform (it will still transform the full stack).");
					}
					

				}
				
			} else {
				System.out.println("One more point"); // We did transform at the
														// beginning such that we
														// have images at the same
														// size to find the points
														// more easily.
				// target.getValue().addListener(this);
				if (mode3D){
					new AnnounceFrame(
						"No transformation will be computed with less than 4 points. You have placed "+fiducialsvector3D.size()+ " points",2);
				}
				else
				{
					new AnnounceFrame(
							"No transformation will be computed with less than 3 points. You have placed "+fiducialsvector.size()+ " points",2);
				}

			}
			
			
		
			return newtransfo;

		}
		/**
		 * convert the array of source and taget point in a pait of ficulial vector for 2D points
		 * @param sourcepoints2
		 * @param targetpoints2
		 * @return
		 */
			Vector<PointsPair> createVectorfromdoublearray(double[][] sourcepoints2,
					double[][] targetpoints2) {

				Vector<PointsPair> points = new Vector<PointsPair>();
				if (targetpoints2.length==sourcepoints2.length){
				for (int i = 0; i < sourcepoints2.length; i++) {
					points.addElement(new PointsPair(
							new Point2D.Double(sourcepoints2[i][0], sourcepoints2[i][1]),
							new Point2D.Double(targetpoints2[i][0], targetpoints2[i][1])));
					/*
					 * System.out.print("Point " + i + 1 + " source " +
					 * sourcepoints2[i][0] + " " + sourcepoints2[i][1] + " target " +
					 * targetpoints2[i][0] + " " + targetpoints2[i][1] + "\n");
					 */
				}
				}
				return points;

			}
			/**
			 * convert the array of source and taget point in a pait of ficulial vector for 3D points
			 * @param sourcepoints2
			 * @param targetpoints2
			 * @return
			 */
			Vector<PointsPair3D> createVectorfromdoublearray3D(
					double[][] sourcepoints2, double[][] targetpoints2) {
				Vector<PointsPair3D> points = new Vector<PointsPair3D>();
				for (int i = 0; i < sourcepoints2.length; i++) {
					points.addElement(new PointsPair3D(new PPPoint3D(
							sourcepoints2[i][0], sourcepoints2[i][1],
							sourcepoints2[i][2]), new PPPoint3D(targetpoints2[i][0],
							targetpoints2[i][1], targetpoints2[i][2])));
					/*
					 * System.out.print("Point " + i + 1 + " source " +
					 * sourcepoints2[i][0] + " " + sourcepoints2[i][1] + " target " +
					 * targetpoints2[i][0] + " " + targetpoints2[i][1] + "\n");
					 */
				}
				return points;
			}

			private void updateSourcePoints2D(SimilarityTransformation2D newtransfo) {
				

				for (int i = 0; i < this.sourcepoints.length; i++) {
					Point2D testPoint = new Point2D.Double(this.sourcepoints[i][0],
							this.sourcepoints[i][1]);
					newtransfo.apply(testPoint);
					this.sourcepoints[i][0] = testPoint.getX();
					this.sourcepoints[i][1] = testPoint.getY();
				}

			}
			/**
			 * Update the position of ROI points after a transform for exemple 
			 * from the list of pair fiducials.
			 * WARNING: modified here
			 */
			void updateRoi() {

				ArrayList<ROI> listfiducials = source.getValue().getROIs();
				ReOrder(listfiducials);
				// fiducials=new double[10][3];
				int i = -1;
				for (ROI roi : listfiducials) {
					i++;
					Point5D position = roi.getPosition5D();
					position.setX(this.sourcepoints[i][0]);
					position.setY(this.sourcepoints[i][1]);
					position.setZ(this.sourcepoints[i][2]);
					roi.setPosition5D(position);
					

				}
			}
			/**
			 * 
			 * @param fiducialsvector3d2
			 * @return true if one set of points (source or target) at least is coplanar.
			 */
				private boolean testcoplanarity(Vector<PointsPair3D> fiducialsvector3d2) {
					boolean testsource=true;
					boolean testtarget=true;
					//check if at least one point source has a z different from the other one, 
					//and do the same for target points
					double zsource=fiducialsvector3d2.get(0).first.getZ();
					
					for(int i=1;i<fiducialsvector3d2.size();i++){
						PointsPair3D currentpair = fiducialsvector3d2.get(i);
						if (currentpair.first.getZ()!=zsource)
						{
							testsource=false;
							break;
						}
							
					}
					
					double ztarget=fiducialsvector3d2.get(0).second.getZ();
					for(int i=1;i<fiducialsvector3d2.size();i++){
						PointsPair3D currentpair = fiducialsvector3d2.get(i);
						if (currentpair.second.getZ()!=ztarget)
						{
							testtarget=false;
							break;
						}
							
					}
					
					return (testsource||testtarget);
				}
				void updateSourcePoints3D(SimilarityTransformation3D newtransfo) {
					for (int i = 0; i < this.sourcepoints.length; i++) {
						PPPoint3D testPoint = new PPPoint3D(this.sourcepoints[i][0],
								this.sourcepoints[i][1],this.sourcepoints[i][2]);
						newtransfo.apply(testPoint);// get the output IN PHYSICAL UNIT!
						this.sourcepoints[i][0] = testPoint.getX()/source.getValue().getPixelSizeX();
						this.sourcepoints[i][1] = testPoint.getY()/source.getValue().getPixelSizeY();
						this.sourcepoints[i][2] = testPoint.getZ()/source.getValue().getPixelSizeZ();
					}
				
			}
				private boolean CheckTREvsFRE() {
					//For each ROI
					boolean check=false;
					//Compute FRE and compute TRE
					//return true when one has a tre > observed error
					double error=0; //in nm
					double predictederror=0; //in nm
					
					double FLEmax = maxdifferrorinnm();
					System.out.println("Max localization error FLE "+FLEmax+" nm");
					TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
					ComputeFRE.ReadFiducials(target.getValue());
					double[] f = ComputeFRE.PreComputeTRE();
					if ((sourcepoints != null) || (targetpoints != null)) {
						if (mode3D == false) {
							ArrayList<ROI> listfiducials = source.getValue().getROIs();
							ReOrder(listfiducials);
							fiducialsvector = createVectorfromdoublearray(
									sourcepoints, targetpoints);
							for (int index = 0; index < fiducialsvector.size(); index++) 
							{
								error = fiducialsvector.get(index)
									.getDiffinpixels();
								String name=listfiducials.get(index).getName();
								
								error=error*source.getValue().getPixelSizeX()*1000; // in um , to be converted in nm
								predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)fiducialsvector.get(index).first.getX(), (int) fiducialsvector.get(index).first.getY(), 0, f); 
								System.out.println(name+" Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
								if (error>predictederror)
									check=true;
							}
						} else// mode3D
						{
							fiducialsvector3D = createVectorfromdoublearray3D(
									sourcepoints, targetpoints);
							for (int index = 0; index < fiducialsvector3D.size(); index++) {
								
								error = Math.sqrt( Math.pow((fiducialsvector3D.get(index).getfirstxinpixels()-fiducialsvector3D.get(index).getsecondxinpixels())*source.getValue().getPixelSizeX(),2)
										+Math.pow((fiducialsvector3D.get(index).getfirstyinpixels()-fiducialsvector3D.get(index).getsecondyinpixels())*source.getValue().getPixelSizeY(),2)
										+ Math.pow((fiducialsvector3D.get(index).getfirstzinpixels()-fiducialsvector3D.get(index).getsecondzinpixels())*source.getValue().getPixelSizeZ(),2));
										

								error=error*1000; // in um , to be converted in nm
								predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)fiducialsvector3D.get(index).getfirstxinpixels(), (int) fiducialsvector3D.get(index).getfirstyinpixels(), (int)fiducialsvector3D.get(index).getfirstzinpixels(), f); 
								System.out.println("Point "+(index+1)+"Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
								if (error>predictederror)
									check=true;
							}
						}
					}
					return check; // error was never above predicted error

				}	
				
				private boolean CheckTREvsFRE(PointsPair leftpoint, String name ) {
					//For each ROI
					boolean check=false;
					//Compute FRE and compute TRE
					//return true when one has a tre > observed error
					double error=0; //in nm
					double predictederror=0; //in nm
					
					double FLEmax = maxdifferrorinnm();
					System.out.println("Left Point: Max localization error FLE "+FLEmax+" nm");
					TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
					ComputeFRE.ReadFiducials(target.getValue());
					double[] f = ComputeFRE.PreComputeTRE();
					error=leftpoint.getDiffinpixels();		
					error=error*source.getValue().getPixelSizeX()*1000; // in um , to be converted in nm
					predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)leftpoint.first.getX(), (int)leftpoint.first.getY(), 0, f); 
					System.out.println(name+" Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
					curve.add(predictederror,error);
					return check;
				}	
				private boolean CheckTREvsFRE(PointsPair3D leftpoint, String name ) {
					//For each ROI
					boolean check=false;
					//Compute FRE and compute TRE
					//return true when one has a tre > observed error
					double error=0; //in nm
					double predictederror=0; //in nm
					
					double FLEmax = maxdifferrorinnm();
					System.out.println("Left Point: Max localization error FLE "+FLEmax+" nm");
					TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
					ComputeFRE.ReadFiducials(target.getValue());
					double[] f = ComputeFRE.PreComputeTRE();
					
					
						
					error = Math.sqrt( Math.pow((leftpoint.getfirstxinpixels()-leftpoint.getsecondxinpixels())*source.getValue().getPixelSizeX(),2)
							+Math.pow((leftpoint.getfirstyinpixels()-leftpoint.getsecondyinpixels())*source.getValue().getPixelSizeY(),2)
							+ Math.pow((leftpoint.getfirstzinpixels()-leftpoint.getsecondzinpixels())*source.getValue().getPixelSizeZ(),2));
							

					error=error*1000; // in um , to be converted in nm
						predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)leftpoint.getfirstxinpixels(), (int) leftpoint.getfirstyinpixels(), (int)leftpoint.getfirstzinpixels(), f); 
						System.out.println(name +"Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
						
					
				
					return check;
				}	
				/**
				 * 
				 * @return max error between point localisation on registered image, or 200nm (for fluo resolution) OR the pixel size by default if no point pair.
				 * Should be expressed in nm
				 * considered as the FLE (fiducial localization error)
				 */
					public double maxdifferrorinnm() {
						// TODO Auto-generated method stub
						// the min localization error is one pixel or the resolution of fluorescence
						/*double error = Math.max(source.getValue().getPixelSizeX(), target.getValue().getPixelSizeX());
						error=error*1000; // in nm, was in um 
						error=Math.max(error, 200);*/
						
						if (sourcepoints.length<5) {//then the points are perfectly registered which may be a non sense from FLE, 
							//we then assume an error of 2 pixels
							double error = Math.max(source.getValue().getPixelSizeX(), target.getValue().getPixelSizeX());
							error=4*error*1000; // in nm, was in um 
							return error;
						}
						double error=200; //this is the min error in fluorescence
						if ((sourcepoints!= null) && (targetpoints.length == sourcepoints.length)) {

							if (mode3D == false) {
								fiducialsvector = createVectorfromdoublearray(sourcepoints,
										targetpoints);
								double newerror =0;
								//ReOrder(fiducialsvector);
								for (int index = 0; index < fiducialsvector.size(); index++) {

									newerror += fiducialsvector.get(index)
											.getDiffinpixels()*source.getValue().getPixelSizeX()*1000;
									

								}
								newerror=newerror/fiducialsvector.size();
								//if (error>100/(source.getValue().getPixelSizeX()*1000)) //minimal fidulcial localisation error is the fluorecsnce limitation
								//	error=error*source.getValue().getPixelSizeX()*1000;// the pixel size is returned in um and we want in in nm.
								if (newerror > error)
									error = newerror;
							} else// mode3D
							{
								fiducialsvector3D = createVectorfromdoublearray3D(sourcepoints,
										targetpoints);
								double newerror =0;
								for (int index = 0; index < fiducialsvector3D.size(); index++) {

									newerror = fiducialsvector3D.get(index)
											.getDiffinpixels()*source.getValue().getPixelSizeX()*1000;// NOT GOOD SHOULD BE CORRECTED IN 3D
									

								}
								newerror=newerror/fiducialsvector.size();
								if (newerror > error)
									error = newerror;
							}

						}
						return error;
					}

}
