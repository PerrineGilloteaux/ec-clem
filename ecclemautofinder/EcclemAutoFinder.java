/**
 * Copyright 2010-2017 Perrine Paul-Gilloteaux, CNRS.
 * Perrine.Paul-Gilloteaux@univ-nantes.fr
 * 
 * This file is part of EC-CLEM AUTOFINDER
 * 
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 **/

package plugins.perrine.ecclemautofinder;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import icy.canvas.IcyCanvas;
import icy.gui.dialog.MessageDialog;

import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.frame.progress.ToolTipFrame;

import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.image.lut.LUT;
import icy.painter.Overlay;
import icy.resource.ResourceUtil;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
import icy.type.DataType;
import icy.type.point.Point5D;
import icy.util.XMLUtil;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVar;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarListener;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;

import plugins.adufour.vars.lang.VarInteger;
import plugins.adufour.vars.lang.VarSequence;
import plugins.kernel.roi.descriptor.measure.ROIMassCenterDescriptorsPlugin;
import plugins.kernel.roi.roi2d.ROI2DPoint;

import vtk.vtkAbstractTransform;
import vtk.vtkCenterOfMass;
import vtk.vtkDataArray;
import vtk.vtkDataSet;
import vtk.vtkDoubleArray;
import vtk.vtkFloatArray;
import vtk.vtkIdList;
import vtk.vtkImageData;
import vtk.vtkImageReslice;
import vtk.vtkIntArray;
import vtk.vtkIterativeClosestPointTransform;
import vtk.vtkLinearTransform;
import vtk.vtkMath;
import vtk.vtkMatrix4x4;
import vtk.vtkPCAStatistics;
import vtk.vtkPointData;
import vtk.vtkPointLocator;
import vtk.vtkPoints;
import vtk.vtkPolyData;
import vtk.vtkShortArray;
import vtk.vtkTable;
import vtk.vtkTransform;
import vtk.vtkTransformPolyDataFilter;
import vtk.vtkUnsignedCharArray;
import vtk.vtkUnsignedIntArray;
import vtk.vtkUnsignedShortArray;
import vtk.vtkVertexGlyphFilter;

/**
 * TODO: add the option Scale deformation (Affine transfo)
 * @author paul-gilloteaux-p
 *
 */

public class EcclemAutoFinder extends EzPlug implements Block, EzStoppable {

	private EzVarSequence source=new EzVarSequence("Source");
	private EzVarSequence target= new EzVarSequence("Target");
	private EzVarDouble pixelsizexysource=new EzVarDouble("Pixel size x,y of source in nanometers");
	private EzVarDouble pixelsizexytarget=new EzVarDouble("Pixel size x,y of target in nanometers");
	private EzVarDouble pixelsizezsource=new EzVarDouble("Slice spacing of source in nanometers");
	
	private EzVarDouble pixelsizeztarget=new EzVarDouble("Slice spacing of target in nanometers");
	private EzLabel versioninfo = new EzLabel("Version " +this.getDescriptor().getVersion());
	
	private EzVarText choicemode = new EzVarText("Transform Mode:",
			new String[] { "About the same content in both n-D images","Find small part in bigger field of view","Find small part in bigger field of view Reverse"}, 0, false);
	EzVarBoolean showtarget = new EzVarBoolean(" Also show the transformed target on source",false);
	EzVarDouble distvar=new EzVarDouble("Max error allowed for testing in microns",1,0.1,100,1);
	EzVarInteger proportion=new EzVarInteger("Percentage of target point to keep for test",70,1,100,20);
	
	private double InputSpacingx;
	private double InputSpacingy;
	private double InputSpacingz;
	private Sequence imagesource;
	private Sequence imagetarget;
	private vtkDataSet[] imageData;
	private vtkImageReslice ImageReslice;
	private vtkPolyData sourcepoint;
	private vtkPolyData targetpoint;

	private int extentx;
	private int extenty;
	private int extentz;
	private double spacingx;
	private double spacingy;
	private double spacingz;
	private double radius;
	private int sourcenbpointsradius1;
	private int sourcenbpointsradius2;
	private DataType oriType;
	double distance=Double.POSITIVE_INFINITY;
	private int nbpoints;
	private VarSequence tseqtarget= new VarSequence("Target transformed on source", null);;
	private VarSequence tseqsource=new VarSequence("Source transformed on Target sequence", null);
	private VarInteger scalepercent = new VarInteger("Scale", 100);
	private EzVarBoolean affine=new EzVarBoolean("Physical scaling",false);
	boolean stopFlag;
	// only point at a distance less than minspacing to test will be tested
	private double minspacingtotest=4;
	private double minscore=Double.POSITIVE_INFINITY;
	private int keptpoint=0;
	private double percent;
	private int nbtasks;
	@Override
	public void stopExecution()
	{
		// this method is from the EzStoppable interface
		// if this interface is implemented, a "stop" button is displayed
		// and this method is called when the user hits the "stop" button
		stopFlag = true;
	}
	public class MyCandidatesOverlay extends Overlay
	{
		double candidatex;
		double candidatey;
		double radius;
		Color color;
		public MyCandidatesOverlay(int number){
			super("candidate "+number );
		}
		@Override
		public void paint(Graphics2D g, Sequence seq, IcyCanvas canvas){
			g.setColor(this.color);
			g.setStroke(new BasicStroke(5));
			g.drawOval((int)(this.candidatex-(double)radius/2),(int) (this.candidatey-(double)radius/2), (int)radius,(int)radius);
		}
		public void setParameters(double candidatex,double candidatey,double radius){
			this.candidatex=candidatex;
			this.candidatey=candidatey;
			this.color=Color.CYAN;
			this.radius=radius;
		}
		public void setHot() {
			// TODO Auto-generated method stub
			this.color=Color.ORANGE;
			
		}
	};
	@Override
	public void clean() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void execute() {
		stopFlag=false;
		if (source.getValue()==target.getValue())
		{
			new AnnounceFrame("You have selected the same source and target image!");
			return;
		}
		if (source.getValue()==null)
		{
			new AnnounceFrame("Source image was closed!");
			return;
		}
		if (target.getValue()==null)
		{
			new AnnounceFrame("Target image was closed!");
			return;
		}
		if (source.getValue().getROIs().size()==0){
			new AnnounceFrame("There is no Roi on "+source.getValue().getName());
			return;
		}
		if (target.getValue().getROIs().size()==0){
			new AnnounceFrame("There is no Roi on "+target.getValue().getName());
			return;
		}
		if (source.getValue().getROIs().size()<8){
			new AnnounceFrame("You need at least 8 Roi points on "+source.getValue().getName());
			new AnnounceFrame("Give a try to BinarytoPointRoi, from Rois with small sampling");
			return;
		}
		if (target.getValue().getROIs().size()<8){
			new AnnounceFrame("You need at least 8 Roi points on "+target.getValue().getName());
			new AnnounceFrame("Give a try to BinarytoPointRoi, from Rois with small sampling");
			return;
		}
		//remove old circle overlays
		List<Overlay> oldoverlays = target.getValue().getOverlays();
		for (int i=0; i<oldoverlays.size();i++){
			if (oldoverlays.get(i).getCanBeRemoved())
				target.getValue().removeOverlay(oldoverlays.get(i));
		}
		//nbtasks = Runtime.getRuntime().availableProcessors();
		vtkTransform tobewritten=new vtkTransform();
		boolean showtargetTransformed=showtarget.getValue();
		// Transform ROI in VTK points (in nm)
		sourcepoint = getRoifromsequence(source.getValue(), 0, source.getValue().getSizeX(), 0,
				source.getValue().getSizeY(), -1, source.getValue().getSizeZ());

		targetpoint = getRoifromsequence(target.getValue(), 0, Math.round(target.getValue().getSizeX()), 0,
				Math.round(target.getValue().getSizeY()), -1, target.getValue().getSizeZ());
		System.out.println("# source points" + sourcepoint.GetNumberOfPoints());
		System.out.println("# target points" + targetpoint.GetNumberOfPoints());
		// Read datapixel etc and save it

		setData(); //will set imagetraget etc..
		distance=Double.POSITIVE_INFINITY;
		minscore=Double.POSITIVE_INFINITY;
		keptpoint=0;
		percent=(double)proportion.getValue()/100; // proportion of points from number of source points to be randomly selected in target points
		vtkTransform myvtktransform=new vtkTransform();
		vtkMatrix4x4 mybesttransform=new vtkMatrix4x4();
		//double dist = 10*Math.max(imagetarget.getPixelSizeX(),imagesource.getPixelSizeX());//in um?
		double dist=distvar.getValue();
		System.out.println("Distance max "+dist+ "microns");
		System.out.println("Proportion "+proportion.getValue()+ "%");
		// if mode 1 : actually not use
		if (choicemode.getValue()=="SimpleICPRANSAC"){
			
			// just do ICP and Ransac where candidates and ROI is the whole image
			//TODO an option such as Points placed very roughly against accurately detection
			//double dist = 10*Math.max(imagetarget.getPixelSizeX(),imagesource.getPixelSizeX());//in um?
			mybesttransform=AutoFinder(targetpoint,dist); 
			System.out.println( "The distance now is:"+this.distance);
			myvtktransform.SetMatrix(mybesttransform);
			applyTransformtosequenceandROI(myvtktransform, this.distance, myvtktransform);
			tobewritten.DeepCopy(myvtktransform);
			if ((showtargetTransformed)||(this.isHeadLess())){
				applyInverseTransformtoTarget(myvtktransform);
			}

		}
		else{
			// ICP RANSAC with prealignment
			if (choicemode.getValue().contains("About the same content in both n-D images")){
				//one way
				vtkTransform aligned=ReorientSourcepointandComputeRadius(false);
				vtkTransform reorientingTargetPoint=ReorientCandidatesTargetpoints(targetpoint);
				//vtkTransform reorientingTargetPoint=new vtkTransform();
				vtkTransformPolyDataFilter trup=new  vtkTransformPolyDataFilter();
				trup.SetInputData(targetpoint);

				trup.SetTransform(reorientingTargetPoint);
				trup.Update(); 
				
				//dist = 10*imagetarget.getPixelSizeX();
				mybesttransform=AutoFinder(trup.GetOutput(),dist); 
				double back_updistance=distance;
				// and the other the choose BEST!!! //one axe only
				sourcepoint = getRoifromsequence(source.getValue(), 0, source.getValue().getSizeX(), 0,
						source.getValue().getSizeY(), -1, source.getValue().getSizeZ());
				vtkTransform aligned2=ReorientSourcepointandComputeRadius(true);
				vtkMatrix4x4 mybesttransform2=new vtkMatrix4x4();
				mybesttransform2=AutoFinder(trup.GetOutput(),dist); 
				
				if (distance<back_updistance){
					mybesttransform.DeepCopy(mybesttransform2);
					aligned.DeepCopy(aligned2);
					System.out.println("Reverse kept");
				}
				else{
						distance=back_updistance;
						sourcepoint = getRoifromsequence(source.getValue(), 0, source.getValue().getSizeX(), 0,
								source.getValue().getSizeY(), -1, source.getValue().getSizeZ());
						ReorientSourcepointandComputeRadius(false);
						System.out.println("not reverse kept");
				}
				vtkTransform myvtktransformtmp=new vtkTransform();
				myvtktransformtmp.SetMatrix(mybesttransform);
				
				myvtktransformtmp.PostMultiply();

				myvtktransformtmp.Concatenate((vtkLinearTransform) reorientingTargetPoint.GetInverse());
				myvtktransformtmp.Update();
				
				myvtktransform.DeepCopy(myvtktransformtmp);
			
				aligned.PostMultiply();
				aligned.Concatenate(myvtktransform);
				aligned.Update();

				
				if (distance<Double.POSITIVE_INFINITY){
					System.out.println("I will apply transfo now"); 
					applyTransformtosequenceandROI(aligned, distance, myvtktransform);
					tobewritten.DeepCopy(aligned);
					if ((showtargetTransformed)||(this.isHeadLess())){
						applyInverseTransformtoTarget(aligned);
					}
				}
				else{
					new AnnounceFrame("notransform found");
				}
				
			}	else{
				if (choicemode.getValue()=="Find small part in bigger field of view"){
				//if mode 3: pattern search
				//This is the transform going from image source to PCA aligned Image source
				vtkTransform aligned=ReorientSourcepointandComputeRadius(false);

				// create candidates vector
				vtkPoints candidates=new vtkPoints(); 
				candidates.Initialize();
				vtkPoints other=FindCandidatesAreasinTarget();
				candidates.ShallowCopy(other); 
				//
				if  (candidates.GetNumberOfPoints()==0){ //candidates.Initialize();
					System.out.println("no candidates based on the number of point were found, testing the other method, based on density ratio");
					vtkPoints other2=FindCandidatesAreasinTargetMethod2();
					candidates.ShallowCopy(other2); 
				}
				// for each candidate area: create a new image based on transform from the subselection 
				// get the score
				// apply the best one
				
				//this.distance=100;
				double distance_max=10;
				myvtktransform=new vtkTransform();
				ProgressFrame  progressc = new ProgressFrame("Analyzing Candidates..."); 
				progressc.setLength(candidates.GetNumberOfPoints());
				for (int cand=0;cand<candidates.GetNumberOfPoints();cand++){
					if (stopFlag==true){
						progressc.close();
						return;
					}
					progressc.setPosition(cand);
					
					vtkPointLocator plocator=new vtkPointLocator();
					plocator.SetDataSet(targetpoint); 
					vtkIdList listofpoints=new  vtkIdList();
					plocator.FindPointsWithinRadius(radius,candidates.GetPoint(cand),listofpoints); 
					vtkPoints test=new vtkPoints(); 
					test.Initialize();
					test.SetDataTypeToDouble();


					for (int ip=0;ip<listofpoints.GetNumberOfIds();ip++) {

						test.InsertNextPoint(targetpoint.GetPoint(listofpoints.GetId(ip)));

					}
					vtkPolyData mypoints = new vtkPolyData();
					mypoints.SetPoints(test); 
					vtkVertexGlyphFilter vertexfilter=new vtkVertexGlyphFilter(); 
					vertexfilter.SetInputData(mypoints);
					vtkPolyData newsetoftargetpoints=new vtkPolyData();
					vertexfilter.Update();
					newsetoftargetpoints.ShallowCopy(vertexfilter.GetOutput());
					vtkTransform reorientingTargetPoint=ReorientCandidatesTargetpoints(newsetoftargetpoints);
					
					vtkTransformPolyDataFilter trup=new  vtkTransformPolyDataFilter();
					trup.SetInputData(newsetoftargetpoints);

					trup.SetTransform(reorientingTargetPoint);
					trup.Update(); 
					//CreateRoifromPoints(target.getValue(),trup.GetOutput()); 
					// now reorient newsetofTargetpoints
					mybesttransform=new vtkMatrix4x4(); //To test reorientation 
					MyCandidatesOverlay mycandidate=new MyCandidatesOverlay(cand);
					mycandidate.setParameters(candidates.GetPoint(cand)[0] / imagetarget.getPixelSizeX(), candidates.GetPoint(cand)[1] / imagetarget.getPixelSizeY(), (radius*2)/ imagetarget.getPixelSizeY());
					
					target.getValue().addOverlay(mycandidate);
					//double dist = 20*imagetarget.getPixelSizeX();
					
					mybesttransform=AutoFinder(trup.GetOutput(),dist); 
					//System.out.println( "The distance now is:"+this.distance);

					vtkTransform myvtktransformtmp=new vtkTransform();
					myvtktransformtmp.SetMatrix(mybesttransform);

					myvtktransformtmp.PostMultiply();

					myvtktransformtmp.Concatenate((vtkLinearTransform) reorientingTargetPoint.GetInverse());
					myvtktransformtmp.Update();
					if (distance<distance_max){
						distance_max=distance;
						myvtktransform.DeepCopy(myvtktransformtmp);
						mycandidate.setHot();
					}
				}

				progressc.close();
				if (distance_max<10){
					aligned.PostMultiply();
					aligned.Concatenate(myvtktransform);
					aligned.Update();


					System.out.println("I will apply transfo now"); 
					applyTransformtosequenceandROI(aligned, distance_max, myvtktransform);
					tobewritten.DeepCopy(aligned);
					if ((showtargetTransformed)||(this.isHeadLess())){
						applyInverseTransformtoTarget(aligned);
					}

				}
				else{
					System.err.println("No transform was found, Check Your Calibration Settings, and the detection of points");
					new AnnounceFrame("No transform was found: you can augment the tolerance on error or play on the proportion of point to be tested for example");
				}
			}
			else{// "Find small part in bigger field of view WITH FLIP"
			//if mode 3: pattern search
				//Find small part in bigger field of view WITH FLIP
			//This is the transform going from image source to PCA aligned Image source
			vtkTransform aligned=ReorientSourcepointandComputeRadius(true);

			// create candidates vector
			vtkPoints candidates=new vtkPoints(); 
			candidates.Initialize();
			vtkPoints other=FindCandidatesAreasinTarget();
			candidates.ShallowCopy(other); 
			//
			if  (candidates.GetNumberOfPoints()==0){ //candidates.Initialize();
				System.out.println("no candidates based on the number of point were found, testing the other method, based on density ratio");
				vtkPoints other2=FindCandidatesAreasinTargetMethod2();
				candidates.ShallowCopy(other2); 
			}
			// for each candidate area: create a new image based on transform from the subselection 
			// get the score
			// apply the best one
			
			this.distance=Double.POSITIVE_INFINITY;
			double distance_max=10;
			myvtktransform=new vtkTransform();
			ProgressFrame  progressc = new ProgressFrame("Analyzing Candidates..."); 
			progressc.setLength(candidates.GetNumberOfPoints());
			for (int cand=0;cand<candidates.GetNumberOfPoints();cand++){
				if (stopFlag==true){
					progressc.close();
					return;
				}
				progressc.setPosition(cand);
				vtkPointLocator plocator=new vtkPointLocator();
				plocator.SetDataSet(targetpoint); 
				vtkIdList listofpoints=new  vtkIdList();
				plocator.FindPointsWithinRadius(radius,candidates.GetPoint(cand),listofpoints); 
				vtkPoints test=new vtkPoints(); 
				test.Initialize();
				test.SetDataTypeToDouble();


				for (int ip=0;ip<listofpoints.GetNumberOfIds();ip++) {

					test.InsertNextPoint(targetpoint.GetPoint(listofpoints.GetId(ip)));

				}
				vtkPolyData mypoints = new vtkPolyData();
				mypoints.SetPoints(test); 
				vtkVertexGlyphFilter vertexfilter=new vtkVertexGlyphFilter(); 
				vertexfilter.SetInputData(mypoints);
				vtkPolyData newsetoftargetpoints=new vtkPolyData();
				vertexfilter.Update();
				newsetoftargetpoints.ShallowCopy(vertexfilter.GetOutput());
				vtkTransform reorientingTargetPoint=ReorientCandidatesTargetpoints(newsetoftargetpoints);
				
				vtkTransformPolyDataFilter trup=new  vtkTransformPolyDataFilter();
				trup.SetInputData(newsetoftargetpoints);

				trup.SetTransform(reorientingTargetPoint);
				trup.Update(); 
				//CreateRoifromPoints(target.getValue(),trup.GetOutput()); 
				// now reorient newsetofTargetpoints
				mybesttransform=new vtkMatrix4x4(); //To test reorientation 
				MyCandidatesOverlay mycandidate=new MyCandidatesOverlay(cand);
				mycandidate.setParameters(candidates.GetPoint(cand)[0] / imagetarget.getPixelSizeX(), candidates.GetPoint(cand)[1] / imagetarget.getPixelSizeY(), (radius*2)/ imagetarget.getPixelSizeY());
				
				target.getValue().addOverlay(mycandidate);
				//double dist = 20*imagetarget.getPixelSizeX();
				mybesttransform=AutoFinder(trup.GetOutput(),dist); 
				//System.out.println( "The distance now is:"+this.distance);

				vtkTransform myvtktransformtmp=new vtkTransform();
				myvtktransformtmp.SetMatrix(mybesttransform);

				myvtktransformtmp.PostMultiply();

				myvtktransformtmp.Concatenate((vtkLinearTransform) reorientingTargetPoint.GetInverse());
				myvtktransformtmp.Update();
				if (distance<distance_max){
					distance_max=distance;
					myvtktransform.DeepCopy(myvtktransformtmp);
					mycandidate.setHot();
				}
			}

			progressc.close();
			if (distance_max<10){
				aligned.PostMultiply();
				aligned.Concatenate(myvtktransform);
				aligned.Update();


				System.out.println("I will apply transfo now"); 
				applyTransformtosequenceandROI(aligned, distance_max, myvtktransform);
				tobewritten.DeepCopy(aligned);
				if ((showtargetTransformed)||(this.isHeadLess())){
					applyInverseTransformtoTarget(aligned);
				}

			}
			else{
				System.err.println("No transform was found, Check Your Calibration Settings, and the detection of points");
				new AnnounceFrame("No transform found, try to augment the distance or decrease the proportion of point to be tested");
			}
		}}
		}
writeTransfo(tobewritten);
	}
	/**
	 * write xml file (sourcefilename suffixed with _AUTOTRANSFORM.xml with the transfo from source file so it can be applied back by applytransform
	 * @param myvtktransform
	 */
private void writeTransfo(vtkTransform myvtktransform) {
	vtkMatrix4x4 transfo = myvtktransform.GetMatrix();
	String name = source.getValue().getFilename() + "_TRANSFOAUTO.xml";
	File XMLFile = new File(name);
			
			Document document = XMLUtil.createDocument(true);
			Element transfoElement = XMLUtil.addElement(document.getDocumentElement(), "TargetSize");
			XMLUtil.setAttributeIntValue(transfoElement, "width", target.getValue().getWidth());
			XMLUtil.setAttributeIntValue(transfoElement, "height", target.getValue().getHeight());
			XMLUtil.setAttributeDoubleValue(transfoElement, "sx", target.getValue().getPixelSizeX());
			XMLUtil.setAttributeDoubleValue(transfoElement, "sy", target.getValue().getPixelSizeY());
			XMLUtil.setAttributeDoubleValue(transfoElement, "sz", target.getValue().getPixelSizeZ());
			XMLUtil.setAttributeIntValue(transfoElement, "nz", target.getValue().getSizeZ());
			XMLUtil.setAttributeIntValue(transfoElement, "auto", 1);
			/*if (mode3D) {
				XMLUtil.setAttributeIntValue(transfoElement, "nz", target.getValue().getSizeZ());
				XMLUtil.setAttributeDoubleValue(transfoElement, "sx", target.getValue().getPixelSizeX());
				XMLUtil.setAttributeDoubleValue(transfoElement, "sy", target.getValue().getPixelSizeY());
				XMLUtil.setAttributeDoubleValue(transfoElement, "sz", target.getValue().getPixelSizeZ());
			}*/
			
			System.out.println("Transformation will be saved as " + XMLFile.getPath());
			transfoElement = XMLUtil.addElement(document.getDocumentElement(), "MatrixTransformation");

			XMLUtil.setAttributeIntValue(transfoElement, "order",0);
			XMLUtil.setAttributeDoubleValue(transfoElement, "m00", transfo.GetElement(0, 0));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m01", transfo.GetElement(0, 1));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m02", transfo.GetElement(0, 2));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m03", transfo.GetElement(0, 3));

			XMLUtil.setAttributeDoubleValue(transfoElement, "m10", transfo.GetElement(1, 0));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m11", transfo.GetElement(1, 1));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m12", transfo.GetElement(1, 2));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m13", transfo.GetElement(1, 3));

			XMLUtil.setAttributeDoubleValue(transfoElement, "m20", transfo.GetElement(2, 0));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m21", transfo.GetElement(2, 1));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m22", transfo.GetElement(2, 2));
			XMLUtil.setAttributeDoubleValue(transfoElement, "m23", transfo.GetElement(2, 3));

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
 * apply the aligned transform to the source image (to take into account any prealignment, and my vtk transform to roi
 * @param aligned
 * @param distance_max given by distance after closest point after ICP
 * @param myvtktransform
 */

		private void applyTransformtosequenceandROI(vtkTransform aligned, double distance_max, vtkAbstractTransform myvtktransform) {
			ProgressFrame  progress = new ProgressFrame("Applying the transformation..."); 
			//convert each channel to vtk imageand apply the transformation
			int		  nbc = imagesource.getSizeC(); 
			imageData=new vtkDataSet[nbc]; 
			for (int c=0;c<nbc;c++)
			{

				converttoVtkImageData(c,this.imagesource,true);

				this.ImageReslice = new vtkImageReslice();

				this.ImageReslice.SetInputData(imageData[c]);
				this.ImageReslice.SetOutputDimensionality(3);
				this.ImageReslice.SetOutputOrigin(0, 0, 0);
				this.ImageReslice.SetOutputSpacing(this.spacingx, this.spacingy,  this.spacingz); 
				this.ImageReslice.SetOutputExtent(0, this.extentx, 0,		  this.extenty, 0, this.extentz); 

				this.ImageReslice.SetResliceTransform(aligned.GetInverse());

				this.ImageReslice.SetInterpolationModeToLinear();

				this.ImageReslice.Update(); 


				imageData[c] = ImageReslice.GetOutput();

			}
			// convert back to icy image
			int nbt = imagesource.getSizeT();
			int nbz = this.extentz+1;

			int w = this.extentx+1; 
			int h = this.extenty+1; 
			DataType datatype =  imagesource.getDataType_(); 
			Sequence  sequence2=SequenceUtil.getCopy(imagesource); 
			sequence2.beginUpdate();
			sequence2.removeAllImages(); 
			try {
				switch (datatype){
				case UBYTE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final byte[] inData=((vtkUnsignedCharArray) myvtkarray).GetJavaArray();

								byte[] outData=new byte[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsByte(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}

					break;
				case BYTE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final byte[] inData=((vtkUnsignedCharArray) myvtkarray).GetJavaArray();

								byte[] outData=new byte[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsByte(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case USHORT:

					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final short[] inData=((vtkUnsignedShortArray) myvtkarray).GetJavaArray();

								short[] outData=new short[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsShort(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case SHORT:

					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final short[] inData=((vtkShortArray) myvtkarray).GetJavaArray();

								short[] outData=new short[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsShort(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case INT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final int[] inData=((vtkIntArray) myvtkarray).GetJavaArray();

								int[] outData=new int[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsInt(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case UINT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final int[] inData=((vtkUnsignedIntArray) myvtkarray).GetJavaArray();

								int[] outData=new int[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsInt(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case FLOAT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final float[] inData=((vtkFloatArray) myvtkarray).GetJavaArray();

								float[] outData=new float[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsFloat(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case DOUBLE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageData[c].GetPointData().GetScalars();
								final double[] inData=((vtkDoubleArray) myvtkarray).GetJavaArray();

								double[] outData=new double[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsDouble(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				default:
					break;
					//
				}

				sequence2.setPixelSizeX(this.spacingx);
				sequence2.setPixelSizeY(this.spacingy);
				sequence2.setPixelSizeZ(this.spacingz); 
			} 
			finally {

				sequence2.endUpdate();
			}
			// display the sequence
			
			if (!this.isHeadLess()) //called in Block protocol
				
				addSequence(sequence2);
			sequence2.setName("Distance: "+distance_max+  " nbpoints: "+ nbpoints); 
			if (affine.getValue()==true)
				new AnnounceFrame("Your source image has been isotropically rescaled at "+Math.round(aligned.GetScale()[0]*100)+"%");
			if (this.isHeadLess())
				scalepercent.setValue((int) Math.round(aligned.GetScale()[0]*100));
			// APPLY the point transform 
			vtkTransformPolyDataFilter tr=new  vtkTransformPolyDataFilter();
			tr.SetInputData(sourcepoint);
			
			tr.SetTransform(myvtktransform);
			tr.Update(); 
			CreateRoifromPoints(sequence2,tr.GetOutput(),Color.RED);
			CreateRoifromPoints(sequence2,targetpoint,Color.GREEN);
			// create an overlay
			if (!this.isHeadLess()){
			Sequence Result1 = SequenceUtil.extractSlice(
					sequence2, source.getValue().getFirstViewer().getPositionZ());
			Result1 = SequenceUtil.extractFrame(Result1, source.getValue().getFirstViewer().getPositionT());
			LUT sourcelut = source.getValue().getFirstViewer().getLut();
			
			LUT targetlut = target.getValue().getFirstViewer().getLut();
			
			int targetnchannel = target.getValue().getSizeC();
			int sourcenchannel = sequence2.getSizeC();
			// we want to merge the same z, arbitrarily the one of source
			Sequence Result2=null;
			if (target.getValue().getSizeZ()>=source.getValue().getSizeZ()){
				Result2 = SequenceUtil.extractSlice(
						target.getValue(), source.getValue()
						.getFirstViewer().getPositionZ());
			}
			else {
				Result2 = SequenceUtil.extractSlice(
						target.getValue(), target.getValue() 
						.getFirstViewer().getPositionZ());
			}

			Result2 = SequenceUtil.extractFrame(Result2, target.getValue().getFirstViewer().getPositionT());
			Result2.dataChanged();
		
			if (Result1.getDataType_()!=Result2.getDataType_())
				Result2 = SequenceUtil.convertToType(Result2,Result1.getDataType_(), true); // warning: bug in icy if converttotype to the same type. should be ok here
			Result2.dataChanged();
			

			Sequence[] sequences = { Result1, Result2 };
			Sequence Result = SequenceUtil.concatC(sequences);
			
			addSequence(Result);

			Result.setName("Overlayed");
			
			
			for (int c = 0; c < sourcenchannel; c++)
				Result.getUserLUT()
				.getLutChannel(c)
				.getColorMap()
				.copyFrom(
						sourcelut.getLutChannel(c)
						.getColorMap());

			for (int c = 0; c < targetnchannel; c++)
				Result.getUserLUT()
				.getLutChannel(sourcenchannel + c)
				.getColorMap()
				.copyFrom(
						targetlut.getLutChannel(c).getColorMap());

			}
			
			if (this.isHeadLess())
			{ //called in Block protocol
				tseqsource.setValue(sequence2);
			}
			progress.close(); 
			System.out.println("have been applied"); 


		}
		/**apply the reverse transformation target to source
		 * 
		 * @param aligned the transform to be apply to the image source toward target, , to be inversed 
		 */
		private void applyInverseTransformtoTarget(vtkTransform aligned) {
			ProgressFrame  progress = new ProgressFrame("Applying the reverse transformation to target..."); 
			
			int		  nbc = imagetarget.getSizeC(); 
			vtkDataSet[] imageDatat=new vtkDataSet[nbc]; 
			for (int c=0;c<nbc;c++)
			{

				imageDatat[c]=converttoVtkImageData(c, this.imagetarget,false);

				this.ImageReslice = new vtkImageReslice();

				this.ImageReslice.SetInputData(imageDatat[c]);
				this.ImageReslice.SetOutputDimensionality(3);
				this.ImageReslice.SetOutputOrigin(0, 0, 0);
				this.ImageReslice.SetOutputSpacing(this.InputSpacingx, this.InputSpacingy,  this.InputSpacingz); 
				this.ImageReslice.SetOutputExtent(0, this.source.getValue().getSizeX()-1, 0,		 this.source.getValue().getSizeY()-1, 0, this.source.getValue().getSizeZ()-1); 

				this.ImageReslice.SetResliceTransform(aligned);// and not aligned.getinverse

				this.ImageReslice.SetInterpolationModeToLinear();

				this.ImageReslice.Update(); 


				imageDatat[c] = ImageReslice.GetOutput();

			}
			// convert back to icy image
			int nbt = imagetarget.getSizeT();
			int nbz = this.source.getValue().getSizeZ();

			int w = this.source.getValue().getSizeX(); 
			int h = this.source.getValue().getSizeY(); 
			DataType datatype =  imagetarget.getDataType_(); 
			Sequence  sequence2=SequenceUtil.getCopy(imagetarget); 
			sequence2.beginUpdate();
			sequence2.removeAllImages(); 
			try {
				switch (datatype){
				case UBYTE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final byte[] inData=((vtkUnsignedCharArray) myvtkarray).GetJavaArray();

								byte[] outData=new byte[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsByte(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}

					break;
				case BYTE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final byte[] inData=((vtkUnsignedCharArray) myvtkarray).GetJavaArray();

								byte[] outData=new byte[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsByte(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case USHORT:

					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final short[] inData=((vtkUnsignedShortArray) myvtkarray).GetJavaArray();

								short[] outData=new short[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsShort(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case SHORT:

					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final short[] inData=((vtkShortArray) myvtkarray).GetJavaArray();

								short[] outData=new short[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsShort(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case INT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final int[] inData=((vtkIntArray) myvtkarray).GetJavaArray();

								int[] outData=new int[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsInt(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case UINT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final int[] inData=((vtkUnsignedIntArray) myvtkarray).GetJavaArray();

								int[] outData=new int[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsInt(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case FLOAT:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final float[] inData=((vtkFloatArray) myvtkarray).GetJavaArray();

								float[] outData=new float[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsFloat(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				case DOUBLE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							for (int c=0;c<nbc;c++){
								vtkDataArray myvtkarray = imageDatat[c].GetPointData().GetScalars();
								final double[] inData=((vtkDoubleArray) myvtkarray).GetJavaArray();

								double[] outData=new double[w*h];
								for (int i = 0; i < h; i++) {
									for (int j = 0; j < w; j++) {

										outData[i * w + j] =  inData[z * w * h + i * w + j];

									}
								}


								image.setDataXYAsDouble(c, outData);

							}
							sequence2.setImage(t, z, image);

						}

					}
					break;
				default:
					break;
					//
				}

				sequence2.setPixelSizeX(this.InputSpacingx);
				sequence2.setPixelSizeY(this.InputSpacingy);
				sequence2.setPixelSizeZ(this.InputSpacingz); 
			} 
			finally {

				sequence2.endUpdate();
			}
			// display the sequence
			if (!this.isHeadLess())
				addSequence(sequence2);
			sequence2.setName("Target aligned on source"); 
			
			if (this.isHeadLess()){ //called in Block protocol
				if (this.isHeadLess()){ //called in Block protocol
					tseqtarget.setValue(sequence2);
				}
			}
			new AnnounceFrame("Use the Merge Channel Plugin if needed, or link both viewers",5);
			progress.close(); 
			System.out.println(" target transfo have been applied also"); 


		}
		private void setData() {
			imagesource=source.getValue(); 
			imagetarget=target.getValue();
			this.InputSpacingx=source.getValue().getPixelSizeX();
			this.InputSpacingy=source.getValue().getPixelSizeY();
			this.InputSpacingz=source.getValue().getPixelSizeZ(); 
			this.oriType = source.getValue().getDataType_(); 
			this.extentx=target.getValue().getSizeX()-1;
			this.extenty=target.getValue().getSizeY()-1;
			this.extentz=target.getValue().getSizeZ()-1;
			this.spacingx=target.getValue().getPixelSizeX();
			this.spacingy=target.getValue().getPixelSizeY();
			this.spacingz=target.getValue().getPixelSizeZ();

		}

		private vtkPoints FindCandidatesAreasinTargetMethod2() {
			// for each target point, find number of neighbors and compare to target
			// point.
			vtkPointLocator plocator = new vtkPointLocator();

			plocator.SetDataSet(targetpoint);

			vtkPoints tmpcandidates = new vtkPoints();
			tmpcandidates.Initialize();
			tmpcandidates.SetDataTypeToDouble();
			for (int ip = 0; ip < targetpoint.GetNumberOfPoints(); ip++) {
				vtkIdList listofpoints = new vtkIdList();
				vtkIdList listofpoints2 = new vtkIdList();
				plocator.FindPointsWithinRadius(radius, targetpoint.GetPoint(ip), listofpoints);
				plocator.FindPointsWithinRadius((radius) / 2, targetpoint.GetPoint(ip), listofpoints2);
				int ctarget1 = listofpoints.GetNumberOfIds();
				int ctarget2 = listofpoints2.GetNumberOfIds();
				//System.out.println(
				//		(double) (ctarget1) / ctarget2 + " " + (double) sourcenbpointsradius1 / sourcenbpointsradius2);
				boolean tobeadded = true;
				if (((double) (ctarget1) / ctarget2 <= 1.20 * (double) sourcenbpointsradius1 / sourcenbpointsradius2)
						&& ((double) ctarget1 / ctarget2 >= 0.80 * (double) sourcenbpointsradius1 / sourcenbpointsradius2))

				{
					// check that another point in the same area was not given
					if (tmpcandidates.GetNumberOfPoints()>0){
					vtkPolyData polydata = new vtkPolyData();
					vtkPoints candidates2 = new vtkPoints();
					
					candidates2.ShallowCopy(tmpcandidates);
					polydata.SetPoints(candidates2);
					vtkPointLocator plocatorcandidates = new vtkPointLocator();
					
					plocatorcandidates.SetDataSet(polydata);
					vtkIdList listofpointsalreadycandidates = new vtkIdList();
					plocatorcandidates.FindPointsWithinRadius(radius/this.minspacingtotest, targetpoint.GetPoint(ip), listofpointsalreadycandidates);
					if (listofpointsalreadycandidates.GetNumberOfIds()>0)
						tobeadded=false;
					}
					if (tobeadded) {
						// we have a candidate
						tmpcandidates.InsertNextPoint(targetpoint.GetPoint(ip));
						//System.out.println(" Candidate: " + targetpoint.GetPoint(ip)[0] + " " + targetpoint.GetPoint(ip)[1]);
					}

				}

			}
			vtkPoints candidates2 = new vtkPoints();

			candidates2.ShallowCopy(tmpcandidates);
			return candidates2;

		}
/**
 * add a candidate point if nb points in R in target at 50% the same than source, AND nb point in R/2 at +/-50% the same also; AND no point in a radius bigeer than radius /3 was added
 */
		private vtkPoints FindCandidatesAreasinTarget() {
			// for each target point, find number of neighbors and compare to target
			// point.
			vtkPointLocator plocator = new vtkPointLocator();

			plocator.SetDataSet(targetpoint);

			vtkPoints tmpcandidates = new vtkPoints();
			tmpcandidates.Initialize();
			tmpcandidates.SetDataTypeToDouble();
			for (int ip = 0; ip < targetpoint.GetNumberOfPoints(); ip++) {
				vtkIdList listofpoints = new vtkIdList();
				vtkIdList listofpoints2 = new vtkIdList();
				plocator.FindPointsWithinRadius(radius, targetpoint.GetPoint(ip), listofpoints);
				plocator.FindPointsWithinRadius(radius / 2, targetpoint.GetPoint(ip), listofpoints2);
				int ctarget1 = listofpoints.GetNumberOfIds();
				int ctarget2 = listofpoints2.GetNumberOfIds();

				//System.out.println(ctarget1 + " " + ctarget2);
				boolean tobeadded = true;
				if ((ctarget1 <= 1.3* sourcenbpointsradius1) && (ctarget1 >= 0.7* sourcenbpointsradius1))
					if ((ctarget2 <= 1.3 * sourcenbpointsradius2) && (ctarget2 >= 0.7* sourcenbpointsradius2))

					{
						// check that another point in the same area was not given
						// check that another point in the same area was not given
						if (tmpcandidates.GetNumberOfPoints()>0){
						vtkPolyData polydata = new vtkPolyData();
						vtkPoints candidates2 = new vtkPoints();
						
						candidates2.ShallowCopy(tmpcandidates);
						polydata.SetPoints(candidates2);
						vtkPointLocator plocatorcandidates = new vtkPointLocator();
						
						plocatorcandidates.SetDataSet(polydata);
						vtkIdList listofpointsalreadycandidates = new vtkIdList();
						plocatorcandidates.FindPointsWithinRadius(radius/this.minspacingtotest, targetpoint.GetPoint(ip), listofpointsalreadycandidates);
						if (listofpointsalreadycandidates.GetNumberOfIds()>0)
							tobeadded=false;
						}
						if (tobeadded) {
							// we have a candidate
							tmpcandidates.InsertNextPoint(targetpoint.GetPoint(ip));
							//System.out.println(" Candidate: " + targetpoint.GetPoint(ip)[0] + " " + targetpoint.GetPoint(ip)[1]);
						}

					}
			}
			vtkPoints candidates = new vtkPoints();

			candidates.ShallowCopy(tmpcandidates);
			return candidates;
		}
		
		/**
		 * 
		 * @param reverse if reverse, turn the other way arround?
		 * @return
		 */
		private vtkTransform ReorientSourcepointandComputeRadius(boolean reverse) {
			vtkPoints test = new vtkPoints();
			for (int i = 0; i < sourcepoint.GetNumberOfPoints(); i++) {
				test.InsertNextPoint(sourcepoint.GetPoint(i));
				
			}

			
			vtkDoubleArray xArray = new vtkDoubleArray();
			
			vtkDoubleArray yArray = new vtkDoubleArray();
			
			vtkDoubleArray zArray = new vtkDoubleArray();
			
			xArray.SetName("x");
			yArray.SetName("y");
			zArray.SetName("z");

			for (int i = 0; i < test.GetNumberOfPoints(); i++) {
				double[] p = new double[3];
				test.GetPoint(i, p);
				xArray.InsertNextValue(p[0]);
				yArray.InsertNextValue(p[1]);
				zArray.InsertNextValue(p[2]);
			}
			vtkTable datasetTable = new vtkTable();
			datasetTable.AddColumn(xArray);
			datasetTable.AddColumn(yArray);
			datasetTable.AddColumn(zArray);
			vtkPCAStatistics pcaStatistics = new vtkPCAStatistics();
			pcaStatistics.SetInputData(datasetTable);
			pcaStatistics.SetColumnStatus("x", 1);
			pcaStatistics.SetColumnStatus("y", 1);
			pcaStatistics.RequestSelectedColumns();
			pcaStatistics.SetDeriveOption(true);
			pcaStatistics.Update();

			// Get the main eigen value
			vtkDoubleArray eigenValues = new vtkDoubleArray();

			pcaStatistics.GetEigenvalues(eigenValues);
			/*for (int i = 0; i < eigenValues.GetNumberOfTuples(); i++) {
				System.out.println(" EV source " + eigenValues.GetValue(i));
			}*/

			// Get the Eigne Vectors
			vtkDoubleArray evec1 = new vtkDoubleArray();

			pcaStatistics.GetEigenvector(0, evec1);

			double[] vectoraxe = new double[3];
			double[] vectoraxe2 = new double[3];
			double[] axeref = new double[3];
		

			for (int i = 0; i < 3; i++) {
				vectoraxe[i] = evec1.GetValue(i);
				vectoraxe2[i] = -evec1.GetValue(i);
				//System.out.print("v" + vectoraxe[i]);
			}
			//System.out.println(" EVratio " + eigenValues.GetValue(0)/eigenValues.GetValue(1));
			axeref[0] = 100;
			

			vtkMath mymath = new vtkMath();
			double angle = mymath.AngleBetweenVectors(vectoraxe, axeref);
			angle = Math.toDegrees(angle);
			// why did I do that??
			/*if (angle>180)
				angle=0;
			if (angle<-180)
				angle=0;*/
			
			if (reverse){
				angle=mymath.AngleBetweenVectors(vectoraxe2, axeref);
				angle = Math.toDegrees(angle);
				System.out.println("Reverse angle "+angle);
			}
			else{
				System.out.println("Not reverse angle "+angle);
			}
				
			/*if (angle>180)
				angle=180;
			if (angle<-180)
				angle=180;*/
			vtkCenterOfMass centerOfMassFilter = new vtkCenterOfMass();
			centerOfMassFilter.SetInputData(sourcepoint);

			centerOfMassFilter.SetUseScalarsAsWeights(false);
			centerOfMassFilter.Update();

			double[] center = new double[3];
			center = centerOfMassFilter.GetCenter();

			vtkTransformPolyDataFilter tr = new vtkTransformPolyDataFilter();
			tr.SetInputData(sourcepoint);
			vtkTransform tmptr = new vtkTransform();
			tmptr.Translate(center[0], center[1], 0);

			// tmptr.Translate(center);
			tmptr.RotateZ(-angle);
			tmptr.Translate(-center[0], -center[1], 0);
			tr.SetTransform(tmptr);
			tr.Update();

			sourcepoint = tr.GetOutput();

			vtkPolyData mypoints = new vtkPolyData();
			// mypoints.SetLines(lines);
			// mypoints.Initialize();
			// mypoints.SetNumberofPoints();
			mypoints.SetPoints(test);
			mypoints.ComputeBounds();
			double[] bounds = mypoints.GetBounds(); // minx,maxx,miny,maxy,minz,maxz
			double dimx = bounds[1] - bounds[0];
			double dimy = bounds[3] - bounds[2];
			double dimz = bounds[5] - bounds[4];
			radius = 0;
			if (dimx > radius)
				radius = dimx;
			if (dimy > radius)
				radius = dimy;
			if (dimz > radius)
				radius = dimz;
			if (Math.sqrt(dimx * dimx + dimy * dimy + dimz * dimz) > radius)
				radius = Math.sqrt(dimx * dimx + dimy * dimy + dimz * dimz);
			radius = (radius) /2;
			System.out.println("Search Radius in um "+radius);
			System.out.println("corrected rotation source:"+angle);
			vtkPointLocator plocator = new vtkPointLocator();

			vtkCenterOfMass centerOfMassFilter2 = new vtkCenterOfMass();
			centerOfMassFilter2.SetInputData(sourcepoint);
			plocator.SetDataSet(sourcepoint);
			double mytotald = 0;
			centerOfMassFilter2.SetUseScalarsAsWeights(false);
			centerOfMassFilter2.Update();

			double[] center2 = new double[3];
			center2 = centerOfMassFilter2.GetCenter();

			vtkIdList listofpoints = new vtkIdList();
			vtkIdList listofpoints2 = new vtkIdList();
			int mypointcenter = plocator.FindClosestPoint(center2);
			plocator.FindPointsWithinRadius(radius, sourcepoint.GetPoint(mypointcenter), listofpoints);
			plocator.FindPointsWithinRadius(radius / 2, sourcepoint.GetPoint(mypointcenter), listofpoints2);
			sourcenbpointsradius1 = listofpoints.GetNumberOfIds();
			sourcenbpointsradius2 = listofpoints2.GetNumberOfIds();
			//System.out.println("Ref Source Pattern " + sourcenbpointsradius1 + " " + sourcenbpointsradius2);
			

			return tmptr;
		}
		
		
/**
 * Compute the principal axes from the eigen values, the center of mass of the point, then rotate the point set to be aligned with the main principal axe
 * TODO there is a 180 degree possible error here!!! use also second axe
 * @param newset the set of point to be studied
 * @return the vtkTransform (old point set-> realigned point set)
 */
		private vtkTransform ReorientCandidatesTargetpoints(vtkPolyData newset) {
			vtkPoints test = new vtkPoints();
			for (int i = 0; i < newset.GetNumberOfPoints(); i++) {
				test.InsertNextPoint(newset.GetPoint(i));

			}

			vtkDoubleArray xArray = new vtkDoubleArray();

			vtkDoubleArray yArray = new vtkDoubleArray();

			vtkDoubleArray zArray = new vtkDoubleArray();

			xArray.SetName("x");
			yArray.SetName("y");
			zArray.SetName("z");

			for (int i = 0; i < test.GetNumberOfPoints(); i++) {
				double[] p = new double[3];
				test.GetPoint(i, p);
				xArray.InsertNextValue(p[0]);
				yArray.InsertNextValue(p[1]);
				zArray.InsertNextValue(p[2]);
			}
			vtkTable datasetTable = new vtkTable();
			datasetTable.AddColumn(xArray);
			datasetTable.AddColumn(yArray);
			datasetTable.AddColumn(zArray);
			vtkPCAStatistics pcaStatistics = new vtkPCAStatistics();
			pcaStatistics.SetInputData(datasetTable);
			pcaStatistics.SetColumnStatus("x", 1);
			pcaStatistics.SetColumnStatus("y", 1);
			pcaStatistics.RequestSelectedColumns();
			pcaStatistics.SetDeriveOption(true);
			pcaStatistics.Update();
			
			// Get the main eigen value
			vtkDoubleArray eigenValues = new vtkDoubleArray();

			pcaStatistics.GetEigenvalues(eigenValues);
			/*for (int i = 0; i < eigenValues.GetNumberOfTuples(); i++) {
				System.out.println(" EV " + eigenValues.GetValue(i));
			}
			System.out.println(" EVratio " + eigenValues.GetValue(0)/eigenValues.GetValue(1));*/
			// Get the Eigen Vector main 1
			vtkDoubleArray evec1 = new vtkDoubleArray();

			pcaStatistics.GetEigenvector(0, evec1);

			double[] vectoraxe = new double[3];
			double[] axeref = new double[3];

			for (int i = 0; i < 3; i++) {
				vectoraxe[i] = evec1.GetValue(i);
				//System.out.print(" " + vectoraxe[i]);
			}
			//System.out.println(" ");
			axeref[0] = 100;
			vtkMath mymath = new vtkMath();
			double angle = mymath.AngleBetweenVectors(vectoraxe, axeref);
			angle = Math.toDegrees(angle);
			//System.out.println("Angle: " + angle);
			// Compute the center of mass
			vtkCenterOfMass centerOfMassFilter = new vtkCenterOfMass();
			centerOfMassFilter.SetInputData(newset);

			centerOfMassFilter.SetUseScalarsAsWeights(false);
			centerOfMassFilter.Update();

			double[] center = new double[3];
			center = centerOfMassFilter.GetCenter();

			//vtkTransformPolyDataFilter tr = new vtkTransformPolyDataFilter();
			//tr.SetInputData(newset);
			vtkTransform tmptr = new vtkTransform();
			tmptr.Translate(center[0], center[1], 0);

			// tmptr.Translate(center);
			tmptr.RotateZ(-angle);
			tmptr.Translate(-center[0], -center[1], 0);
			//tr.SetTransform(tmptr);
			//tr.Update();

			//newset = tr.GetOutput();

			//System.out.println("corrected rotation target:"+angle);
			return tmptr;
		}
		
/**
 * compute the transform between a set of target point and all points in source
 * 		
 * @param subtargetpoint
 * @param dist the max distance allowed in RANSAC procedure for a point to be considered well aligned
 * @return a 4x4 matrix containing the transform
 */
		private vtkMatrix4x4 AutoFinder(vtkPolyData subtargetpoint, double dist) {
			// initialize vtk objects
			vtkIterativeClosestPointTransform myicp = new vtkIterativeClosestPointTransform();
			vtkMatrix4x4 transform = new vtkMatrix4x4();
			vtkMatrix4x4 mybesttransform = new vtkMatrix4x4();
			
			

			// IMPLEMENT pseudo RANSAC PROCEDURE (on subblock of the image)
			int max_iter_icp = 100;
			int max_iter_ransac = Math.max(100,Math.min(3*sourcepoint.GetNumberOfPoints(), 1000));
			if (sourcepoint.GetNumberOfPoints()<20){
				max_iter_ransac=500;
				max_iter_icp = 500;
			}
			
			double mybesttotald=distance;
			int nbtarget=Math.max((int) Math.round(percent*(double)(subtargetpoint.GetNumberOfPoints())),8);
			int nbsource= Math.max((int)Math.round(percent*(double)sourcepoint.GetNumberOfPoints()),8);
			int nbpointransac=Math.min(nbsource, nbtarget);
			int minnbpoint=Math.min(nbpointransac,keptpoint);
			if (keptpoint==0)
				minnbpoint=nbpointransac;
			
			if (subtargetpoint.GetNumberOfPoints()<0.5*keptpoint)
				return mybesttransform;
			
			/*vtkMaskPoints mymask = new vtkMaskPoints();
			mymask.SetInputData(sourcepoint);

			mymask.SetMaximumNumberOfPoints(subtargetpoint.GetNumberOfPoints());
			//mymask.SetRandomModeType(2);
			mymask.RandomModeOff();

			mymask.GenerateVerticesOn();
			mymask.Update();*/
			


			//System.out.println(nbsource+" "+mymask.GetOutput().GetNumberOfPoints()+" "+nbtarget);
			
			for (int iter = 0; iter < max_iter_ransac; iter++) {
				if (distance<Math.min(imagesource.getPixelSizeX(),imagetarget.getPixelSizeX()))
					break; // stop condition I put for Ransac: average distance inferior to min pixelsize
				

				// Compute an ICP between selected target points and all source points
				// we need to do an inversed icp (we need less point in source that target and that the contrary?)
				myicp.SetTarget(sourcepoint);
				//myicp.SetTarget(mymask.GetOutput());
				
				//List<Integer> listidx=generateRandomPointsList(nbpointransac,subtargetpoint.GetNumberOfPoints());
				List<Integer> listidx=generateSmartRandomPointsList(subtargetpoint,nbpointransac,subtargetpoint.GetNumberOfPoints());
				vtkPoints mypointsransac = new vtkPoints();
				mypointsransac.Initialize();
				mypointsransac.SetDataTypeToDouble();
				for (int pp=0;pp<listidx.size();pp++){
					mypointsransac.InsertNextPoint(subtargetpoint.GetPoint(listidx.get(pp)));
				}
				vtkPolyData copytmp = new vtkPolyData();
				copytmp.SetPoints(mypointsransac);
				vtkVertexGlyphFilter vertexfiltertmp = new vtkVertexGlyphFilter();
				vertexfiltertmp.SetInputData(copytmp);
				vertexfiltertmp.Update();
				vtkPolyData maskedset = new vtkPolyData();
				maskedset.ShallowCopy(vertexfiltertmp.GetOutput());

				myicp.SetSource(maskedset);

				myicp.GetLandmarkTransform().SetModeToRigidBody();

				if (affine.getValue()==true)
					myicp.GetLandmarkTransform().SetModeToSimilarity();	

				myicp.SetMaximumNumberOfIterations(max_iter_icp);
				//myicp.SetMaximumNumberOfIterations(20);
				myicp.StartByMatchingCentroidsOn();

				myicp.SetCheckMeanDistance(1);
				
				myicp.SetMaximumMeanDistance(0.001);

				myicp.Modified();
				myicp.Update();

				transform.DeepCopy(myicp.GetMatrix());

				if (transform.GetElement(3, 3)!=1){
					int tmp=maskedset.GetNumberOfPoints();
					int tmp2= sourcepoint.GetNumberOfPoints();	
					System.out.println(tmp+" " +tmp2);
					System.out.println("problem singular transform "+subtargetpoint.GetNumberOfPoints()+" selected "+maskedset.GetNumberOfPoints());

					continue;
				}
				// Compute the distance between matched point only
				// myicp.GetLandmarkTransform().GetSourceLandmarks()
				// apply the computed transform to all taregt  points

				vtkTransformPolyDataFilter tr = new vtkTransformPolyDataFilter();
				tr.SetInputData(subtargetpoint);
				vtkTransform tmptr = new vtkTransform();
				tmptr.SetMatrix(transform);
				tr.SetTransform(tmptr);
				tr.Update();
				vtkPointLocator plocator = new vtkPointLocator();
				plocator.SetDataSet(sourcepoint);

				double mytotald = 0;

				vtkPoints test = new vtkPoints();
				test.Initialize();
				test.SetDataTypeToDouble();
				vtkPoints testsource = new vtkPoints();
				testsource.Initialize();
				testsource.SetDataTypeToDouble();
				vtkMath mat = new vtkMath();
				//test how many point and how well they matched now
				double mybestd = 0;
				//System.out.println("Below I should find partly the same than landmark target");
				for (int ip = 0; ip < subtargetpoint.GetNumberOfPoints(); ip++) {
					int closestpointid = plocator.FindClosestPoint(tr.GetOutput().GetPoint(ip));

					double myd = Math.sqrt(mat.Distance2BetweenPoints(sourcepoint.GetPoint(closestpointid),
							tr.GetOutput().GetPoint(ip)));
					if (myd < dist) {
						test.InsertNextPoint(subtargetpoint.GetPoint(ip));
						testsource.InsertNextPoint(sourcepoint.GetPoint(closestpointid));//hoping they keep the same order...
						mybestd += myd;
					}

				}
				
				

				if (test.GetNumberOfPoints() >= minnbpoint) 
				{



					//System.out.println(" Nb points selected" + test.GetNumberOfPoints());
					mybestd = mybestd / test.GetNumberOfPoints();
					vtkPolyData mypoints = new vtkPolyData();

					mypoints.SetPoints(test);
					vtkVertexGlyphFilter vertexfilter = new vtkVertexGlyphFilter();
					vertexfilter.SetInputData(mypoints);
					vertexfilter.Update();
					vtkPolyData copy = new vtkPolyData();
					copy.ShallowCopy(vertexfilter.GetOutput());

					vtkPolyData mypoints2 = new vtkPolyData();
					mypoints2.SetPoints(testsource);
					vtkVertexGlyphFilter vertexfilter2 = new vtkVertexGlyphFilter();
					vertexfilter2.SetInputData(mypoints2);
					vertexfilter2.Update();
					vtkPolyData testsourcepl = new vtkPolyData();
					testsourcepl.ShallowCopy(vertexfilter2.GetOutput());
					myicp.SetTarget(copy);
					myicp.SetSource(testsourcepl);
					myicp.SetMaximumNumberOfIterations(max_iter_icp);
					myicp.Modified();
					myicp.Update();


					// Compute the error on pertinent Data:
					mytotald=0;
					for (int ip = 0; ip < myicp.GetLandmarkTransform().GetSourceLandmarks().GetNumberOfPoints(); ip++) {

						double myd = Math.sqrt(mat.Distance2BetweenPoints(myicp.GetLandmarkTransform().GetSourceLandmarks().GetPoint(ip),
								myicp.GetLandmarkTransform().GetTargetLandmarks().GetPoint(ip)));

						mytotald += myd;

					}

					mytotald = mytotald / myicp.GetLandmarkTransform().GetSourceLandmarks().GetNumberOfPoints();


					if (mytotald<mybesttotald){
						System.out.println(" Score" + " bestd "+mybestd+ "totald"+ mytotald);
						System.out.println(" Nb subtargettotal " + subtargetpoint.GetNumberOfPoints());
						System.out.println(" Nb points kept" + myicp.GetLandmarkTransform().GetSourceLandmarks().GetNumberOfPoints()+ " d="+mytotald);
						/*myicp.GetLandmarkTransform().SetModeToSimilarity();
						myicp.Modified();
						myicp.Update();*/
						keptpoint=Math.max(keptpoint,myicp.GetLandmarkTransform().GetSourceLandmarks().GetNumberOfPoints());
						mybesttransform.DeepCopy(myicp.GetMatrix());
						mybesttotald=mytotald;
						//minscore=score;
						nbpoints=test.GetNumberOfPoints();
						System.out.println(" Nb points kept" + nbpoints);


						//}
					}


				}
			}



			distance = mybesttotald;
			myicp.Delete();
			return mybesttransform;
		}
		/**
		 * 
		 * @param subtargetpoint
		 * @param nbpointransac
		 * @param getNumberOfPoints
		 * @return list of point to test
		 */
		private List<Integer> generateSmartRandomPointsList(vtkPolyData subtargetpoint, int nbpointransac,
		int orinbpoint) {
			List<Integer> myCoords = new ArrayList<Integer>();
			if (nbpointransac>=orinbpoint){
				for (int i=0;i<orinbpoint;i++){
					myCoords.add(i);
				}
			}
			else{
			Random rn = new Random();
			
			vtkPointLocator targetlocator=new vtkPointLocator();
			targetlocator.SetDataSet(subtargetpoint);
			while ( myCoords.size()<nbpointransac){
				Integer idx=rn.nextInt(orinbpoint);
				if (myCoords.contains(idx)){
					continue;
				}
				else
				{
					vtkIdList result = new vtkIdList();
					targetlocator.FindClosestNPoints ((nbpointransac/10)+1, subtargetpoint.GetPoint(idx), result);
					
					myCoords.add(idx);
					for (int i=0;i<result.GetNumberOfIds();i++){
						if (myCoords.size()<nbpointransac){
							if (myCoords.contains(result.GetId(i))){
								continue;
							}
						
							myCoords.add(result.GetId(i));
						}
					}
				}
				
			}}
			return myCoords;
}

		/**
		 * 
		 * @param nbpointransac
		 * @return list of indexed of point to be kept
		 */
private List<Integer> generateRandomPointsList( int nbpointransac, int orinbpoint) {
	Random rn = new Random();
	List<Integer> myCoords = new ArrayList<Integer>();
	
	
	while ( myCoords.size()<nbpointransac){
		Integer idx=rn.nextInt(orinbpoint);
		if (myCoords.contains(idx)){
			continue;
		}
		else
		{
			myCoords.add(idx);
		}
		
	}
	return myCoords;
}

/**
 * Function called at the EZ plug initialization.
 */
		@Override
		protected void initialize() {
			new ToolTipFrame(    			
					"<html>"+
							"<br> You need to first have identified ROI on both images, only their center will be considered."+
							"<br> The plugin <b>Spot detector</b>  is a good choice here,  " +
							"<br> just make sure to have activated <b>Export to Roi</b> as output;"+
							"<br>or eC-CLEM tool <b>ConvertBinaryToPointRoi</b>"+
							"<br><b>IMPORTANT: Check your metadata </b> first as it will be used by MyAutoFinder."+
							"<br> <li> <b>About the same content in both n-D images </b> :"+
							"<br> This option will try to fit the full content of the image, assuming you have similar detections"+
							"<br>  </li>"+
							"<br> <li> <b>Find Small Part in Bigger View (reverse or not)</b> : "+
							"<br> The purpose here is to find an image position (typically EM) "+
							"<br> on a larger field of view (typically LM). The prealignment will be different </li>"+
							"<br> <li> <b>Max Error in microns:  </b> :"+
							"<br> A pointshould have a distance to its closest matching point below this value"+
							"<br> in order not to be considered as an outlier. Increase if no transformation was found. "+
							"<br> Rule of Thumb: about 10 pixels </li>"+
							
							"<br> <li> <b>Percentage of target point to keep:  </b> :"+
							"<br> This is the minimum percentage of point that have to match: 90% means almost no outliers"+
							"<br> 50% or less if the number of detection are very different. 70% is usually a good trade off"+
							"<br>  </li>"+
							"<br> See the online tutorials for further example or the wizard (I need help). </li>"+
							"<br>"+
							"</html>"
					);
			
			addEzComponent(versioninfo);
			target= new EzVarSequence("Select Target Image with Rois on it");
			source=new EzVarSequence("Select Source Image with Rois on it");
			source.setToolTipText("will be tentatively positionned on target image");
			
			addComponent(new helpButton(this));
			addEzComponent(source);
			addEzComponent(target);
			pixelsizezsource.setToolTipText("Z voxel size");
			pixelsizeztarget.setToolTipText("Z voxel size");
			EzGroup grp = new EzGroup("Metadata to check",pixelsizexysource,pixelsizezsource,pixelsizexytarget,pixelsizeztarget);
			addEzComponent(grp);
			source.addVarChangeListener(new EzVarListener<Sequence>()
	        {
	            @Override
	            public void variableChanged(EzVar<Sequence> source, Sequence newValue)
	            {
	            	if (newValue!=null){
	            		// always correct z metada if <=0
	            		if (newValue.getPixelSizeZ()<=0){
	            			newValue.setPixelSizeZ(1);
	            		}
	            		pixelsizexysource.setValue(newValue.getPixelSizeX()*1000);
	            		pixelsizezsource.setValue(newValue.getPixelSizeZ()*1000);
	            		distvar.setValue(Math.max(10.0/1000*pixelsizexysource.getValue(), 10.0/1000*pixelsizexytarget.getValue()));
	    			
	    			
	            	}
	            }

				
	        });
			pixelsizexysource.addVarChangeListener(new EzVarListener<Double>()
	        {
				@Override
				public void variableChanged(EzVar<Double> sourced, Double newValue) {
					if (source.getValue()!=null){
						source.getValue().setPixelSizeX(newValue/1000); // come back to um
						source.getValue().setPixelSizeY(newValue/1000);	
						distvar.setValue(Math.max(10.0/1000*pixelsizexysource.getValue(), 10.0/1000*pixelsizexytarget.getValue()));
					}
				}
	        });
			pixelsizexytarget.addVarChangeListener(new EzVarListener<Double>()
	        {
				@Override
				public void variableChanged(EzVar<Double> sourced, Double newValue) {
					if (target.getValue()!=null){
						target.getValue().setPixelSizeX(newValue/1000); // come back to um
						target.getValue().setPixelSizeY(newValue/1000);	
						distvar.setValue(Math.max(10.0/1000*pixelsizexysource.getValue(), 10.0/1000*pixelsizexytarget.getValue()));
					}
				}
	        });
			pixelsizezsource.addVarChangeListener(new EzVarListener<Double>()
	        {
				@Override
				public void variableChanged(EzVar<Double> sourced, Double newValue) {
					if (source.getValue()!=null)
						source.getValue().setPixelSizeZ(newValue/1000); // come back to um
				
				}
	        });
			pixelsizeztarget.addVarChangeListener(new EzVarListener<Double>()
	        {
				@Override
				public void variableChanged(EzVar<Double> sourced, Double newValue) {
					if (target.getValue()!=null)
					target.getValue().setPixelSizeZ(newValue/1000); // come back to um
					
				}
	        });
			
			target.addVarChangeListener(new EzVarListener<Sequence>()
	        {
	            @Override
	            public void variableChanged(EzVar<Sequence> source, Sequence newValue)
	            {
	            	if (newValue!=null){
	            		// always correct z metada if <=0
	            		if (newValue.getPixelSizeZ()<=0){
	            			newValue.setPixelSizeZ(1);
	            		}
	            		pixelsizexytarget.setValue(newValue.getPixelSizeX()*1000);
	            		pixelsizeztarget.setValue(newValue.getPixelSizeZ()*1000);
	            		distvar.setValue(Math.max(10.0/1000*pixelsizexysource.getValue(), 10.0/1000*pixelsizexytarget.getValue()));
	            	}
	            }

				
	        });
			
			addEzComponent(choicemode);
			addEzComponent(showtarget);
			
			/*addComponent( GuiUtil.createLineBoxPanel( 	
					Box.createHorizontalGlue(),
					getIcon("plugins/perrine/ecclemautofinder/test.png"),
					getIcon("C:\\Users\\paul-gilloteaux-p\\workspace\\ecclemAutoFinder\\test.png"),
					Box.createHorizontalGlue(),
					getIcon("C:\\Users\\paul-gilloteaux-p\\workspace\\ecclemAutoFinder\\test.png"),
					
					Box.createHorizontalGlue()));
			addComponent( GuiUtil.createLineBoxPanel( 	
					Box.createHorizontalGlue(),
					detectNegativeRadio,
					Box.createHorizontalGlue(),
					detectposRadio,
					
					Box.createHorizontalGlue()));*/
			//distvar.setValue(1.0);
			distvar.setToolTipText("This distance will the one used to test the candidates");
			proportion.setToolTipText("This is the proportion of target point that will be tested at each iteration");
			//addComponent(new JSeparator(JSeparator.VERTICAL));
			addEzComponent(distvar);
			//proportion.setValue(50);
			addEzComponent(proportion);
			addEzComponent(affine);

		}
		private JLabel getIcon( String name )
		{
			
			ImageIcon icon = ResourceUtil.getImageIcon( ImageUtil.load(  new File(name)  ) );		
			JLabel label = new JLabel( icon );
			label.setSize( 128 , 128 );
			label.setPreferredSize( new Dimension( 128, 128) );
			
			return label;
		}
		/**
		 *  Read roi in the bounding box (mix,ax x)(miny,maxy) (minz,max z)
		 * @param seq sequence with ROI
		 * @param minx
		 * @param maxx
		 * @param miny
		 * @param maxy
		 * @param minz
		 * @param maxz
		 * @return a list of ROI point in micrometers as vtkpolydata
		 * TODO COPY THE CLASS NAME OF ROI3d IN CASE
		 */
		protected vtkPolyData getRoifromsequence(Sequence seq, int minx, int maxx, int miny, int maxy, int minz, int maxz) { // in
			// um
			vtkPoints test = new vtkPoints();
			test.SetDataTypeToDouble();

			if (seq == null) {
				MessageDialog.showDialog("Make sure source image is openned");
				return null;
			}
			ArrayList<ROI> listfiducials = seq.getROIs();
			// test.SetNumberOfPoints(listfiducials.size());

			int i = -1;
			for (ROI roi : listfiducials) {
				i++;
				/*
				 * BooleanMask2D mymask = roi.getBooleanMask2D(0, 0, 0, true);
				 * Point[][] pt = mymask.getComponentsPoints(true); for (int
				 * j=0;j<pt[0].length;j++){
				 * 
				 * Point point = pt[0][j];
				 * test.InsertNextPoint(point.getX()*seq.getPixelSizeX(),point.getY(
				 * )*seq.getPixelSizeY(),0*seq.getPixelSizeZ()); } }
				 */
				Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
				if (roi.getClass().getName() == "plugins.kernel.roi.roi2d.ROI2DPoint")// because point have no ROI
					p3D = roi.getPosition5D();
				if (roi.getClass().getName() == "plugins.perrine.easyclemv0.myRoi3D")// because point have no ROI
					p3D = roi.getPosition5D();
				if (p3D.getZ() < 0) {
					p3D.setZ(0);
				}
				// some error on the last roi?
				if (testinside(p3D, minx, maxx, miny, maxy, minz, maxz))
					test.InsertNextPoint(p3D.getX() * seq.getPixelSizeX(), p3D.getY() * seq.getPixelSizeY(),
							p3D.getZ() * seq.getPixelSizeZ());
				// }
				// System.out.println(p3D.getX()*seq.getPixelSizeX());

			}

			/*
			 * for (int j=0;j<test.GetNumberOfPoints();j++){
			 * System.out.println(test.GetPoint(j)[0]+ " " + test.GetPoint(j)[1]+
			 * " " +test.GetPoint(j)[2]); }
			 */
			vtkPolyData mypoints = new vtkPolyData();
			// mypoints.SetLines(lines);
			// mypoints.Initialize();
			// mypoints.SetNumberofPoints();
			mypoints.SetPoints(test);

			vtkVertexGlyphFilter vertexfilter = new vtkVertexGlyphFilter();
			vertexfilter.SetInputData(mypoints);
			vtkPolyData copy = new vtkPolyData();
			vertexfilter.Update();
			copy.ShallowCopy(vertexfilter.GetOutput());
			//System.out.println("Nb source points" + copy.GetNumberOfPoints());

			return copy;

		}

		/**
		 *  Check if a ROI is inside a bounding box
		 * @param p3d
		 * @param minx
		 * @param maxx
		 * @param miny
		 * @param maxy
		 * @param minz
		 * @param maxz
		 * @return true if inside, false if not inside
		 */
		private boolean testinside(Point5D p3d, int minx, int maxx, int miny, int maxy, int minz, int maxz) {
			if ((p3d.getX() >= minx) && (p3d.getX() <= maxx))
				if ((p3d.getY() >= miny) && (p3d.getY() <= maxy))
					if ((p3d.getZ() >= minz) && (p3d.getZ() <= maxz))
						return true;

			return false;
		}

		vtkImageData converttoVtkImageData(int posC, Sequence seq,boolean affectfield) {
			final Sequence sequence2 = seq;
			if (seq == null)
				return null;

			final int sizeX = sequence2.getSizeX();
			final int sizeY = sequence2.getSizeY();
			final int sizeZ = sequence2.getSizeZ();
			final DataType dataType = sequence2.getDataType_();
			final int posT;
			if (!this.isHeadLess()){
				 posT = sequence2.getFirstViewer().getPositionT();
			}
			else{
				posT=0;
			}
			

			// create a new image data structure
			final vtkImageData newImageData = new vtkImageData();

			newImageData.SetDimensions(sizeX, sizeY, sizeZ);
			newImageData.SetSpacing(seq.getPixelSizeX(), seq.getPixelSizeY(), seq.getPixelSizeZ());
			// all component ?
			// if (posC == -1)
			// newImageData.SetNumberOfScalarComponents(sequence.getSizeC(), null);
			// else
			// newImageData.SetNumberOfScalarComponents(1, null);
			// newImageData.SetExtent(0, sizeX - 1, 0, sizeY - 1, 0, sizeZ - 1);

			vtkDataArray array;

			switch (dataType) {
			case UBYTE:

				// newImageData.SetScalarTypeToUnsignedChar();
				// pre-allocate data
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_UNSIGNED_CHAR, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkUnsignedCharArray) array).SetJavaArray(seq.getDataCopyCXYZAsByte(posT));
				else
					((vtkUnsignedCharArray) array).SetJavaArray(seq.getDataCopyXYZAsByte(posT, posC));
				break;

			case BYTE:

				// newImageData.SetScalarTypeToUnsignedChar();
				// pre-allocate data
				// newImageData.AllocateScalars();
				// pre-allocate data
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_UNSIGNED_CHAR, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkUnsignedCharArray) array).SetJavaArray(seq.getDataCopyCXYZAsByte(posT));
				else
					((vtkUnsignedCharArray) array).SetJavaArray(seq.getDataCopyXYZAsByte(posT, posC));
				break;

			case USHORT:
				// newImageData.SetScalarTypeToUnsignedShort();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_UNSIGNED_SHORT, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkUnsignedShortArray) array).SetJavaArray(seq.getDataCopyCXYZAsShort(posT));
				else
					((vtkUnsignedShortArray) array).SetJavaArray(seq.getDataCopyXYZAsShort(posT, posC));
				break;

			case SHORT:
				// newImageData.SetScalarTypeToShort();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_SHORT, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkShortArray) array).SetJavaArray(seq.getDataCopyCXYZAsShort(posT));
				else
					((vtkShortArray) array).SetJavaArray(seq.getDataCopyXYZAsShort(posT, posC));
				break;

			case UINT:
				// newImageData.SetScalarTypeToUnsignedInt();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_UNSIGNED_INT, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkUnsignedIntArray) array).SetJavaArray(seq.getDataCopyCXYZAsInt(posT));
				else
					((vtkUnsignedIntArray) array).SetJavaArray(seq.getDataCopyXYZAsInt(posT, posC));
				break;

			case INT:
				// newImageData.SetScalarTypeToInt();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_INT, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkIntArray) array).SetJavaArray(seq.getDataCopyCXYZAsInt(posT));
				else
					((vtkIntArray) array).SetJavaArray(seq.getDataCopyXYZAsInt(posT, posC));
				break;

			case FLOAT:
				// newImageData.SetScalarTypeToFloat();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_FLOAT, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkFloatArray) array).SetJavaArray(seq.getDataCopyCXYZAsFloat(posT));
				else
					((vtkFloatArray) array).SetJavaArray(seq.getDataCopyXYZAsFloat(posT, posC));
				break;

			case DOUBLE:
				// newImageData.SetScalarTypeToDouble();
				// pre-allocate data
				// newImageData.AllocateScalars();
				newImageData.AllocateScalars(icy.vtk.VtkUtil.VTK_DOUBLE, 1);
				// get array structure
				array = newImageData.GetPointData().GetScalars();
				// set frame sequence data in the array structure
				if (posC == -1)
					((vtkDoubleArray) array).SetJavaArray(seq.getDataCopyCXYZAsDouble(posT));
				else
					((vtkDoubleArray) array).SetJavaArray(seq.getDataCopyXYZAsDouble(posT, posC));
				break;

			default:
				// we probably have an empty sequence
				newImageData.SetDimensions(1, 1, 1);
				newImageData.SetSpacing(seq.getPixelSizeX(), seq.getPixelSizeY(),
						seq.getPixelSizeZ());
				newImageData.SetNumberOfScalarComponents(1, null);
				newImageData.SetExtent(0, 0, 0, 0, 0, 0);
				// newImageData.SetScalarTypeToUnsignedChar();
				// pre-allocate data
				newImageData.AllocateScalars(null);
				break;
			}

			// set connection
			// volumeMapper.SetInput(newImageData);
			// mark volume as modified
			// volume.Modified();

			// release previous volume data memory
			if (affectfield){
			if (imageData[posC] != null) {
				final vtkPointData pointData = imageData[posC].GetPointData();
				if (pointData != null) {
					final vtkDataArray dataArray = pointData.GetScalars();
					if (dataArray != null)
						dataArray.Delete();
					pointData.Delete();
					imageData[posC].ReleaseData();
					imageData[posC].Delete();
				}
			}

			imageData[posC] = newImageData;}
			return  newImageData;
		}
/**
 * 
 * @param seq (sequence on whick created ROI2Dpoints
 * @param points vtkpolydata (unit of position in micrometers)
 * TODO: chnage ROI2Dpoint to my own myROI3D
 */
		protected void CreateRoifromPoints(Sequence seq, vtkPolyData points,Color mycolor) {
			vtkPoints listofpoints = points.GetPoints();
			// seq.removeAllROI();
			for (int i = 0; i < points.GetNumberOfPoints(); i++) {

				ROI roi = new ROI2DPoint();

				Point5D position = roi.getPosition5D();
				position.setX(listofpoints.GetPoint(i)[0] / seq.getPixelSizeX());
				position.setY(listofpoints.GetPoint(i)[1] / seq.getPixelSizeY());
				position.setZ(listofpoints.GetPoint(i)[2] / seq.getPixelSizeZ());
				roi.setPosition5D(position);
				roi.setName("ICP Point " + i);
				roi.setColor(mycolor);
				seq.addROI(roi);

			}
			return;
		}
		public void declareInput(VarList inputMap) {
			// TODO Auto-generated method stub
			inputMap.add("Source Image",source.getVariable());
			inputMap.add("Target Image",target.getVariable());
			inputMap.add("Transformation Mode",choicemode.getVariable());
			distvar.setValue(1.0);
			inputMap.add("Distance max",distvar.getVariable());
			inputMap.add("Proportion",proportion.getVariable());
			inputMap.add("Affine",affine.getVariable());

		}
		@Override
		public void declareOutput(VarList outputMap) {
			// TODO Auto-generated method stub
			outputMap.add("Source Transformed on Target", tseqsource);
			outputMap.add("Target Transformed on Source", tseqtarget);
			outputMap.add("Corrected scale",scalepercent);
			
		}

	}
