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


import java.awt.Dimension;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import java.util.Random;


import javax.swing.JPanel;






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
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;
import plugins.kernel.roi.roi3d.ROI3DPoint;


//Purpose: create a table of value (TRE against FLE, N number of points (not the distance since it is given by error map
// Have a point named target. ADD from 3 to N points with center of gravity in pos 'Gravity, place point within radius R
// and check data on 'target'
// save the confifiguration. 
// TODO MC simulation with predefined configurations 
// mode 3D is not implemented here
public class StudyLandmarksConfagainstN extends EzPlug implements EzStoppable {

	private EzVarSequence source;

	EzVarDouble uFLE=new EzVarDouble("Fiducial localisation error in nm", 200,10,10000,10);
	EzVarDouble Nvalue=new EzVarDouble("max N Value to be tested", 20,4,10000,10);
	EzVarInteger simulnumber = new EzVarInteger("Nb MonteCarlo Simulations",100, 10, 10000, 10);
	EzVarInteger radius = new EzVarInteger("Radius in nanometers",1000, 0, 1000000, 10);
	EzVarFile savedfile=new EzVarFile("Indicate the csv file to create to save the results", ".");
	Sequence target;
	EzVarText choiceinputsection = new EzVarText("I want to study the transformation in:",
			new String[] { "Rigid (or affine)","non Rigid)"}, 0, false);
	private double[][] sourcepoints;

	
	boolean stopflag;
	Random generator= new Random();
	JPanel mainPanel = GuiUtil.generatePanel("Graph");
	IcyFrame mainFrame = GuiUtil.generateTitleFrame("Real configuration Error MC Simulations", mainPanel, new Dimension(300, 100), true, true, true,
	            true);
	
	private Sequence sourceseq;

	
		
	
	
	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		// Rigid case only for now
		mainPanel = GuiUtil.generatePanel("Graph");
		mainFrame = GuiUtil.generateTitleFrame("test plot", mainPanel, new Dimension(300, 100), true, true, true,
		            true);
		// step 1: backup source sequence, and backup ROIs
		stopflag=false;
		sourceseq=source.getValue();
		if (sourceseq==null){
			new AnnounceFrame("Open an image with Rois on it first !!!");
			return;
		}
		if (sourceseq.getROIs().size()<2){
			new AnnounceFrame("Open an image with Rois on it first !!!");
			return;
		}
		Point5D targetpoint=new Point5D.Double();
		Point5D centerpoint=new Point5D.Double();
		// Prepare ROI:
		ArrayList<ROI> listr = sourceseq.getROIs();
		boolean targetnotfound=true;
		boolean centernotfound=true;
		for (ROI roi:listr){
			System.out.println(roi.getName());
			if (roi.getName().matches("Target")){
				targetpoint=roi.getPosition5D();
				targetnotfound=false;
			}
			if (roi.getName().matches("Center")){
				centerpoint=roi.getPosition5D();
				centernotfound=false;
			}
		}
		if (targetnotfound)
		{
			new AnnounceFrame("No roi point named \"Target\", check case as well");
			return;
		}
		if (centernotfound)
		{
			new AnnounceFrame("No roi point named \"Center\", check case as well");
			return;
		}
		ProgressFrame myprogressbar = new ProgressFrame("Computing simulations...");
		myprogressbar.setLength(simulnumber.getValue());
		ArrayList<double[]> mydata =new ArrayList<double[]>();
		for (int mc=1;mc<simulnumber.getValue()+1;mc++){
		for (int n=4;n<Nvalue.getValue()+1; n=n+2){
			for (int fle=10;fle<uFLE.getValue()+1;fle=fle+50){
				CreateSourcePoint(centerpoint,radius.getValue(),n);
				if (n==20){
					DisplayPointRois(sourcepoints);
					RemoveRoibytCenterandTarget();
				}
				myprogressbar.setPosition(mc);
				

				double tre=CheckTRE(targetpoint,sourcepoints,fle);
				double[] datasetelement=new double[4];
				datasetelement[0]=n;
				datasetelement[1]=mc;
				datasetelement[2]=fle;
				datasetelement[3]=tre;
				
				mydata.add(datasetelement);

			}
		}
		}
		myprogressbar.close();
		
		try {
			FileWriter write=new FileWriter(savedfile.getValue().getAbsolutePath());
			write.append("n;mc;fle;tre;\n");
			for (int i=0;i<mydata.size();i++){
				double[] tt= mydata.get(i);
				write.append(tt[0]+";"+tt[1]+";"+tt[2]+";"+tt[3]+"\n");
			}
			write.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Done");
		new AnnounceFrame("Done");
	}




private void CreateSourcePoint(Point5D centerpoint,int radius, int n) {
	this.sourcepoints = new double[n][3];
	//in nm
	
	for (int i=0;i<n;i++) {
		
		sourcepoints[i][0]=(centerpoint.getX()*source.getValue().getPixelSizeX()*1000+(generator.nextGaussian() * (radius/3)));
		sourcepoints[i][1]=(centerpoint.getY()*source.getValue().getPixelSizeY()*1000+(generator.nextGaussian() * (radius/3)));
		sourcepoints[i][2]=0.0;
	}
}
private void DisplayPointRois(double[][] sourcepoints2) {
	double sizex=source.getValue().getPixelSizeX()*1000;// in nm
	double sizez=source.getValue().getPixelSizeZ()*1000;
for (int i=0;i<sourcepoints2.length;i++)
{
	ROI3DPoint newroi=new ROI3DPoint(sourcepoints2[i][0]/sizex,sourcepoints2[i][1]/sizex, sourcepoints2[i][2]/sizez);
	source.getValue().addROI(newroi);
}
}
private void RemoveRoibytCenterandTarget() {
ArrayList<ROI> roilist = source.getValue().getROIs();
for (ROI roi:roilist){
	if (roi.getName().matches("Target"))
		continue;
	if (roi.getName().matches("Center"))
		continue;
	source.getValue().removeROI(roi);
}
}


	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
		EzLabel textinfo1 = new EzLabel(
				"Simulate the influence of different configutation of points centered on Center, on Target point");
		new ToolTipFrame(    			
    			"<html>"+
    			"<br> An Image with a ROI named <b>Center</b> and another named <b>Target</b>"+
    			"<br>have to be openned."+	
    			"<br> The value of N and FLE will be tested up to the number indicated."+
    			" <br> Points will be randomly set within the indicated radius"+
    			"<br> No graphic will be displayed, but all simulations will be saved in the file indicated (CSV format)"+	
    			"</html>"
    			);
		
		source = new EzVarSequence("Select Image to test with Target and ROI ");

		
		
		
		addEzComponent(source);
		
		addEzComponent(uFLE);
		addEzComponent(Nvalue);
		addEzComponent(simulnumber);
		addEzComponent(radius);
		addEzComponent(textinfo1);
		addEzComponent(savedfile);
		//addEzComponent(choiceinputsection);
		
		
	}
	
		
		
/**
 * 
 * @param leftpoint
 * @param name
 * @param datap
 * @return
 */
				
private double CheckTRE(Point5D targetpoint, double[][] sourcepoints2, int fle) {
	//For each ROI

	//Compute FRE and compute TRE
	//return true when one has a tre > observed error
	double predictederror=0; //in nm

	//System.out.println("Left Point: Max localization error FLE "+FLEmax+" nm");
	TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
	ComputeFRE.ReadFiducials(sourcepoints2,source.getValue());
	double[] f = ComputeFRE.PreComputeTRE();
	predictederror = ComputeFRE.ComputeTRE(fle, (int)targetpoint.getX(),  (int)targetpoint.getY(), 0, f); //in nm directly

	return predictederror;
}	
				
			
				
@Override
public void stopExecution(){
	stopflag=true;
}

}
