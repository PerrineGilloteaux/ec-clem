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
/**
 * @author perrine.paul-gilloteaux@univ-nantes.fr
 * This plugin allow to compute the leave one out error for rigid 
 * or non rig transformation based on monteCarlo Simulation to mimick the Fiducial localisation error
 * TODO implement the non rigid part and the 3D config.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.BoxAndWhiskerToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.xy.DeviationRenderer;

import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.RectangleInsets;


import Jama.Matrix;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.frame.progress.ToolTipFrame;
import icy.gui.util.GuiUtil;

import icy.roi.ROI;

import icy.sequence.Sequence;

import icy.type.point.Point5D;

import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;

import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;
import plugins.kernel.roi.descriptor.measure.ROIMassCenterDescriptorsPlugin;
import plugins.kernel.roi.roi3d.ROI3DPoint;


// mode 3D is not implemented here
public class MonteCarloTREstudy extends EzPlug implements EzStoppable {

	private EzVarSequence source;
	private EzVarSequence target;
	EzVarDouble uFLE=new EzVarDouble("Fiducial localisation error in nm", 200,0,10000,10);
	EzVarInteger simulnumber = new EzVarInteger("Nb MonteCarlo Simulations",100, 10, 10000, 10);
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
	boolean stopflag;
	Random generator= new Random();
	JPanel mainPanel = GuiUtil.generatePanel("Graph");
	IcyFrame mainFrame = GuiUtil.generateTitleFrame("Real configuration Error MC Simulations", mainPanel, new Dimension(300, 100), true, true, true,
	            true);
	private YIntervalSeries curve1;
	private YIntervalSeries curve2;
	private String namep;

	
		
	
	
	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		// Rigid case only for now
		mainPanel = GuiUtil.generatePanel("Graph");
		mainFrame = GuiUtil.generateTitleFrame("Real configuration Error MC Simulations", mainPanel, new Dimension(300, 100), true, true, true,
		            true);
		// step 1: backup source sequence, and backup ROIs
		stopflag=false;
		//backupsource = SequenceUtil.getCopy(source.getValue());
		//Icy.getMainInterface().addSequence(backupsource);
		// Prepare ROI:
		/*ArrayList<ROI> list = source.getValue().getROIs();
		int i=0;
		for (ROI roi:list){
			i++
			roi.setName("Point "+i);
		}*/
		GetTargetPointsfromROI();
		GetSourcePointsfromROI();
		if (sourcepoints.length != targetpoints.length) {
			System.out.println("source points different from target point");
			return;

		} 
		curve1= new YIntervalSeries("Discrepancy");
		curve2= new YIntervalSeries("Predicted Error");
		backuproitarget=new ArrayList<ROI>();
		backuproisource=new ArrayList<ROI>();
		// create a true copy, not a pointer
		this.backuptargetpoints = new double[this.targetpoints.length][3];
		this.backupsourcepoints = new double[this.sourcepoints.length][3];
		for (int i=0;i<this.targetpoints.length;i++){
			for( int j=0;j<3;j++){
		this.backuptargetpoints[i][j]=this.targetpoints[i][j];
		this.backupsourcepoints[i][j]=this.sourcepoints[i][j];
			}
			this.backuproitarget.add(new ROI3DPoint(target.getValue().getROIs().get(i).getPosition5D()));
			
			this.backuproisource.add(new ROI3DPoint(source.getValue().getROIs().get(i).getPosition5D()));
		}
		// Step 2: Compute transform with all points
		
		
		if (mode3D == false) {
			fiducialsvector = createVectorfromdoublearray(
					sourcepoints, targetpoints);
			fiducialsvector3D = new Vector<PointsPair3D>();
		} else {
			fiducialsvector3D = createVectorfromdoublearray3D(
					sourcepoints, targetpoints);
			fiducialsvector = new Vector<PointsPair>();
		}
		ComputeTransfo(true); //message: initialtransfo display
		
		// Step 3: Get discrepancy for all points + TRE for all points 
		
		CheckTREvsFRE() ;
		double FLE=uFLE.getValue();
		int nbsimul=simulnumber.getValue();
		ProgressFrame myprogressbar = new ProgressFrame("Computing simulations...");
		myprogressbar.setLength(nbsimul*this.backupsourcepoints.length);
		myprogressbar.setPosition(0);
		// Step 4: For each Roi, Reload original sequence and back up roi , remove the same Roi, 
		final DefaultBoxAndWhiskerCategoryDataset dataset2 
        = new DefaultBoxAndWhiskerCategoryDataset();
		for(int p=0; p<this.backupsourcepoints.length;p++){
			if (stopflag)break;
			ArrayList<double[]> datap=new ArrayList<double[]>();
			for (int mc=0;mc<nbsimul;mc++){
				myprogressbar.setPosition(p*nbsimul+mc);
				ArrayList<ROI> tmpcopyroisource=new ArrayList<ROI>();
				ArrayList<ROI> tmpcopyroitarget=new ArrayList<ROI>();
				for (int i=0;i<this.backupsourcepoints.length;i++){
					
					tmpcopyroisource.add(new ROI3DPoint(this.backuproitarget.get(i).getPosition5D()));
					
					tmpcopyroitarget.add(new ROI3DPoint(this.backuproisource.get(i).getPosition5D()));
				}
				if (stopflag)break;
			target.getValue().removeAllROI();
			source.getValue().removeAllROI();
			target.getValue().addROIs(tmpcopyroitarget, false);
			source.getValue().addROIs(tmpcopyroisource, false);
			//randomly move the position of all rois, including the future left out point
			shakeRois(target.getValue(),FLE*2);
			shakeRois(source.getValue(),FLE*2);
			ArrayList<ROI> listfiducialst = target.getValue().getROIs();
			ReOrder(listfiducialst);
			ArrayList<ROI> listfiducialss = source.getValue().getROIs();
			ReOrder(listfiducialss);
			namep=listfiducialst.get(p).getName();
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
		SimilarityTransformation2D newtransfo = ComputeTransfo(false); //nomessage
		// Step 5: compute the discrepancy after registration with the other (transformed source against modified target position, and compute TRE for this point
		//CheckTREvsFRE();
		//update the left-out point position (discrepancy against registered point and point "perfectly localized")
		
		//TRUE discrepancy then here (with ideal localisation of tre point
		Point2D testPoint=new Point2D.Double(this.backupsourcepoints[p][0], this.backupsourcepoints[p][1]);
		newtransfo.apply(testPoint);
		PointsPair outpoint=new PointsPair(
				testPoint,
				new Point2D.Double(this.backuptargetpoints[p][0], this.backuptargetpoints[p][1])); //target point was not moved
		
		CheckTREvsFREmc(outpoint,listfiducialst.get(p).getName(),datap);
		
			}
			double averageerror=0;
			double maxerror=0;
			double minerror=1000;
			double averagepredictederror=0;
			double averagedist=0;
			double maxperror=0;
			double minperror=1000;
			List<Double> list=new ArrayList<Double>();
			List<Double> list2=new ArrayList<Double>();
			for (int i=0;i<datap.size();i++){
				averageerror+=datap.get(i)[1];
				averagepredictederror+=datap.get(i)[0];
				maxperror=Math.max(maxperror, datap.get(i)[0]);
				maxerror=Math.max(maxerror, datap.get(i)[1]); //0 is predicted error and 1 is discrepancy error (see 1 is Checktremc fonction)
				minperror=Math.min(minperror, datap.get(i)[0]); //
				minerror=Math.min(minerror, datap.get(i)[1]);
				averagedist+=datap.get(i)[2];
				list.add(datap.get(i)[1]);
				list2.add(datap.get(i)[0]);
			}
			averageerror=averageerror/datap.size();
			averagedist=averagedist/datap.size();
			averagepredictederror=averagepredictederror/datap.size();
			curve1.add(averagedist,averageerror,minerror,maxerror);
			curve2.add(averagedist,averagepredictederror,minperror,maxperror);
			dataset2.add(list, "Left one out discrepancy (Ground truth TRE)", "ROI "+namep);
			dataset2.add(list2, "Predicted TRE ", "ROI "+namep);
	}
		// just to let the iamges in the states where I found it.
		target.getValue().removeAllROI();
		source.getValue().removeAllROI();
		target.getValue().addROIs(this.backuproitarget, false);
		source.getValue().addROIs(this.backuproisource, false);
		
		final YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();
        dataset.addSeries(curve1);
        dataset.addSeries(curve2);
		// Step 6: write it/save it/ plot it
        JFreeChart jfreechart=CreateChart(dataset,nbsimul,FLE);
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
	    WhiskerPlot(dataset2);
	    myprogressbar.close();
	}
private void WhiskerPlot(DefaultBoxAndWhiskerCategoryDataset dataset2) {
		
	final CategoryAxis xAxis = new CategoryAxis("Left Out Point");
    final NumberAxis yAxis = new NumberAxis("in nanometers");
    yAxis.setAutoRangeIncludesZero(true);
    final BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
    renderer.setFillBox(true);
    renderer.setMeanVisible(true);
    
    renderer.setBaseToolTipGenerator(new BoxAndWhiskerToolTipGenerator());
    final CategoryPlot plot = new CategoryPlot(dataset2, xAxis, yAxis, renderer);
    plot.setDomainGridlinesVisible(true);
    plot.setRangePannable(true);
    NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
    rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
    final JFreeChart chart2 = new JFreeChart("Discrepancy distribution for TRE per ROI", JFreeChart.DEFAULT_TITLE_FONT, plot, true );
    final ChartPanel chartPanel = new ChartPanel(chart2);
    chartPanel.setPreferredSize(new java.awt.Dimension(450, 270));
    mainPanel.add(chartPanel);
	}

/**
 * this method shake randomly (gaussian centered i 0, std FLE/2) the roi on the sequence in input
 * @param value
 * @param fLE
 */
	private void shakeRois(Sequence seq, double FLE) {
		ArrayList<ROI> listfiducials = seq.getROIs();
		double FLEp=FLE/(seq.getPixelSizeX()*1000);
		double FLEpz=FLE/(seq.getPixelSizeZ()*1000);
		//double lower = -(Math.sqrt((FLEp*FLEp)/3)); // in order to have a distance max of FLE we consider a sphere of diameter fle
		double higher = (Math.sqrt((FLEp*FLEp)/3));
		double lowerz = -FLEpz;
		double higherz = FLEpz;
		
		for (ROI roi : listfiducials) {
			
			Point5D position = roi.getPosition5D();
			//position in pixels + ((random between 0 and 1 )* half FLE in nm - half FLE in nm )/(pixel size in nm)
			//position.setX(position.getX()+(Math.random() * (higher-lower)) + lower);
			//position.setY(position.getY()+(Math.random() * (higher-lower)) + lower);
			//Gaussian generation (generarted centered at 0 with std 1
			position.setX(position.getX()+(generator.nextGaussian() * (higher/2)));
			position.setY(position.getY()+(generator.nextGaussian() * (higher/2)));
			if (mode3D)
				position.setZ(position.getZ()+(Math.random() * (higherz-lowerz)) + lowerz);
			roi.setPosition5D(position);
		}
		
	}

	/*private static JFreeChart CreateChart(YIntervalSeriesCollection dataset) {
		// TODO Auto-generated method stub
		 JFreeChart chart = ChartFactory.createXYLineChart(
	            "TRE vs Discrepancy ",      // chart title
	            "TRE in nm",                      // x axis label
	            "Discrepancy in nm",                      // y axis label
	            dataset,                  // data
	            PlotOrientation.VERTICAL,
	           true,                     // include legend
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
	        //renderer.setSeriesLinesVisible(0, false);
	        xyplot.setRenderer(renderer);
	        NumberAxis numberaxis = (NumberAxis) xyplot.getRangeAxis();
	        
	        numberaxis.setAutoRangeIncludesZero(true);
	        numberaxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	        return chart;

		
	}*/
	private static JFreeChart CreateChart(YIntervalSeriesCollection dataset,int mc,double fle)
    {

		 JFreeChart chart = ChartFactory.createXYLineChart(
		            "Discrepancy vs error for "+mc+"  simulations, FLE= "+fle+ "nm",      // chart title
		            "Distance from the center of gravity for the point removed ",                      // x axis label
		            "in nm",                      // y axis label
		            dataset,                  // data
		            PlotOrientation.VERTICAL,
		           true,                     // include legend
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
        DeviationRenderer deviationrenderer = new DeviationRenderer(true, false);
        deviationrenderer.setSeriesStroke(0, new BasicStroke(3F, 1, 1));
        deviationrenderer.setSeriesShapesVisible(0, true);
        deviationrenderer.setSeriesShapesVisible(1, true);
        deviationrenderer.setSeriesStroke(1, new BasicStroke(3F, 1, 1));
        deviationrenderer.setSeriesFillPaint(0, new Color(255, 200, 200));
        deviationrenderer.setSeriesFillPaint(1, new Color(200, 200, 255));
        xyplot.setRenderer(deviationrenderer);
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
		new ToolTipFrame(    			
    			"<html>"+
    			"<br>This plugin compute from a set of matching points: "+
    			"<br> <li> The accuracy with Monte Carlo Simulations "+
    			"<br>(moving randomly all points around their initial position with the FLE error),"+
    			"<br>  of the registration error of a point against its target position" +
    			"<br> when the point is left OUT the set of point for the registration (i.e N-1 points are used)</li>"+
    			"<br><li> The predicted average  error on the same point, computed <b>without any ground truth</b></li> "+
    			"<br><b> FLE </b> is the localization error you ca expect, i.e basically the resolution of your image "+
    			"<br>(around 400 nm i Fluoresence for exemple), "+
    			" <br>ROI Points should have similar names in both source and target image, such as Point 1, Point 2,..)"+
    			"</html>"
    			);
		
		source = new EzVarSequence("Select Source Image ");

		target = new EzVarSequence("Select Target Image ");
		
		
		addEzComponent(source);
		addEzComponent(target);
		addEzComponent(uFLE);
		addEzComponent(simulnumber);
		addEzComponent(textinfo1);
		//addEzComponent(choiceinputsection);
		new ToolTipFrame("Use a set of Roi as generated by Ec-Clem on 2 images.\n Pay attention to the image metadata (pixel size)");
		
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

				Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
				if (Double.isNaN(p3D.getX()))
					p3D = roi.getPosition5D(); // some Roi does not have gravity
												// center such as points
				if (roi.getClassName()=="plugins.perrine.easyclemv0.myRoi3D")
					p3D=roi.getPosition5D();
				if (roi.getClassName()=="plugins.kernel.roi.roi2d.ROI2DPoint")
					p3D=roi.getPosition5D();
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

				Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
				if (roi.getClassName()=="plugins.perrine.easyclemv0.myRoi3D")
					p3D=roi.getPosition5D();
				if (roi.getClassName()=="plugins.kernel.roi.roi2d.ROI2DPoint")
					p3D=roi.getPosition5D();
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
		SimilarityTransformation2D ComputeTransfo(boolean message) {
			// fiducialsvector(mode2D) OR fiducialsvector3D (mode3D)
			// could have been thinking differently
			SimilarityTransformation2D newtransfo = null ;
			if ((fiducialsvector.size() > 2) || (fiducialsvector3D.size() > 3)) {
				double back_up_pixelsizex=source.getValue().getPixelSizeX();
				double back_up_pixelsizey=source.getValue().getPixelSizeY();
				double back_up_pixelsizez=source.getValue().getPixelSizeZ();
			
				
				if (mode3D == false) {
					
					SimilarityRegistrationAnalytic meanfiducialsalgo = new SimilarityRegistrationAnalytic();
					if (message)
						newtransfo = meanfiducialsalgo.apply(fiducialsvector);
					else
					newtransfo = meanfiducialsalgo.applynomessage(fiducialsvector);
					
					
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

							updateSourcePoints3D(newtransfo3);
							updateRoi();

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
		 * convert the array of source and target point in a pait of ficulial vector for 2D points
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
					
					//double FLEmax = maxdifferrorinnm();
					double FLEmax=uFLE.getValue();
					//System.out.println("Max localization error FLE "+FLEmax+" nm");
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
								//String name=listfiducials.get(index).getName();
								
								error=error*source.getValue().getPixelSizeX()*1000; // in um , to be converted in nm
								predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)fiducialsvector.get(index).first.getX(), (int) fiducialsvector.get(index).first.getY(), 0, f); 
								//System.out.println(name+" Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
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
								//System.out.println("Point "+(index+1)+"Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
								if (error>predictederror)
									check=true;
							}
						}
					}
					return check; // error was never above predicted error

				}	
				
				private boolean CheckTREvsFREmc(PointsPair leftpoint, String name, ArrayList<double[]> datap ) {
					//For each ROI
					boolean check=false;
					//Compute FRE and compute TRE
					//return true when one has a tre > observed error
					double error=0; //in nm
					double predictederror=0; //in nm
					
					double FLEmax = uFLE.getValue();
					//System.out.println("Left Point: Max localization error FLE "+FLEmax+" nm");
					TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
					ComputeFRE.ReadFiducials(target.getValue());
					double[] f = ComputeFRE.PreComputeTRE();
					error=leftpoint.getDiffinpixels();		
					error=error*source.getValue().getPixelSizeX()*1000; // in um , to be converted in nm
					predictederror = ComputeFRE.ComputeTRE(FLEmax, (int)leftpoint.first.getX(), (int)leftpoint.first.getY(), 0, f); //in nm directly
					//System.out.println(name+" Discrepancy in nm: "+error+ "vs Predicted error in nm: "+predictederror);
					double[] mytab=new double[3];
					mytab[0]=predictederror;
					mytab[1]=error;
					mytab[2]=distancetogravitycenter(leftpoint.second,target.getValue());
					datap.add(mytab);
					return check;
				}	
				/**
				 * 
				 * @param point
				 * @param seqwithRois
				 * @return Computed distance (in 2D) from  point to 
				 * the gravity center of the set of ROI, in nm
				 */
				private double distancetogravitycenter(Point2D point, Sequence seqwithRois) {
					ArrayList<ROI> listRoi = seqwithRois.getROIs();
					double GravityCenterX=0.0;
					double GravityCenterY=0.0;
					
					for (ROI roi:listRoi){
						GravityCenterX+=roi.getPosition5D().getX();
						GravityCenterY+=roi.getPosition5D().getY();
						
					}
					GravityCenterX=GravityCenterX/listRoi.size();
					GravityCenterY=GravityCenterY/listRoi.size();
					double distance=Math.sqrt(Math.pow(point.getX()-GravityCenterX,2)+Math.pow(point.getY()-GravityCenterY,2)); //in pixels
					return distance*seqwithRois.getPixelSizeX()*1000; //in nm
				}

				/*private boolean CheckTREvsFREmc(PointsPair3D leftpoint, String name ) {
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
				}	*/
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
					
@Override
public void stopExecution(){
	stopflag=true;
}

}
