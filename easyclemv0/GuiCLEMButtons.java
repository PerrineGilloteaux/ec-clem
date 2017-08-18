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



/**
 * Author: Perrine.Paul-Gilloteaux@curie.fr
 * one second set of button: this one is to call the update transformation
 *  and the clear landmarks methods
 */
package plugins.perrine.easyclemv0;


import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import icy.type.point.Point5D;
import icy.util.XMLUtil;
import plugins.kernel.roi.roi2d.plugin.ROI2DPointPlugin;

import javax.swing.JPanel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import Jama.Matrix;

import javax.swing.JButton;







import java.awt.event.ActionListener;

import java.awt.event.ActionEvent;
import java.util.ArrayList;

import java.util.List;
import java.util.Vector;


public class GuiCLEMButtons extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	EasyCLEMv0 matiteclasse;
	/**
	 * Create the panel.
	 */
	public GuiCLEMButtons(EasyCLEMv0 matiteclasse) {
		this.matiteclasse=matiteclasse;
		/**
		 * Button Update Transformation
		 */
		JButton btnNewButton = new JButton("Update Transformation");
		btnNewButton.setToolTipText("Press this button if you have moved the points, prepared set of points, \n or obtained some black part of the image. This will refresh it");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if ((GuiCLEMButtons.this.matiteclasse.source.getValue()==null)||(GuiCLEMButtons.this.matiteclasse.target.getValue()==null))
				{
					MessageDialog.showDialog("Make sure source and target image are openned and selected");
					return;
				}
				else
				
				{
					
					GuiCLEMButtons.this.matiteclasse.GetSourcePointsfromROI();
					GuiCLEMButtons.this.matiteclasse.GetTargetPointsfromROI();
					if (GuiCLEMButtons.this.matiteclasse.sourcepoints.length!=GuiCLEMButtons.this.matiteclasse.targetpoints.length){
						ArrayList<ROI> listroi = GuiCLEMButtons.this.matiteclasse.source.getValue().getROIs();
						for (ROI roi : listroi){
							if (roi.getName().contains("Point2D")){
								GuiCLEMButtons.this.matiteclasse.source.getValue().removeROI(roi);
							}
						}
						listroi = GuiCLEMButtons.this.matiteclasse.target.getValue().getROIs();
						for (ROI roi : listroi){
							if (roi.getName().contains("Point2D")){
								GuiCLEMButtons.this.matiteclasse.target.getValue().removeROI(roi);
							}
						}
						
						new AnnounceFrame("Warning: not the same number of point on both image. Nothing done",5);
						
					Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
					}
					else{
					if (GuiCLEMButtons.this.matiteclasse.nonrigid==false){
						if (GuiCLEMButtons.this.matiteclasse.sourcepoints.length==0)
						{ 
							MessageDialog.showDialog("No Roi on source image. Create ROI");
							return;
						}
						if (GuiCLEMButtons.this.matiteclasse.targetpoints.length==0)
						{ 
							MessageDialog.showDialog("No Roi on target image. Create ROIs");
							return;
						}
				
						if (GuiCLEMButtons.this.matiteclasse.mode3D==false){
					
					
							GuiCLEMButtons.this.matiteclasse.fiducialsvector = GuiCLEMButtons.this.matiteclasse.createVectorfromdoublearray(GuiCLEMButtons.this.matiteclasse.sourcepoints,
									GuiCLEMButtons.this.matiteclasse.targetpoints);
							GuiCLEMButtons.this.matiteclasse.fiducialsvector3D = new Vector<PointsPair3D>();
						}
						else //mode 3D
						{
							GuiCLEMButtons.this.matiteclasse.fiducialsvector3D = GuiCLEMButtons.this.matiteclasse.createVectorfromdoublearray3D(GuiCLEMButtons.this.matiteclasse.sourcepoints,
							GuiCLEMButtons.this.matiteclasse.targetpoints);
							GuiCLEMButtons.this.matiteclasse.fiducialsvector = new Vector<PointsPair>();
						}
				
						ThreadUtil.bgRun(new Runnable(){
							
								public void run() {
							GuiCLEMButtons.this.matiteclasse.ComputeTransfo();}
						});
					}
					else
					{
						//non rigid
						ThreadUtil.bgRun(new Runnable(){
							
							public void run() {
						NonRigidTranformationVTK nrtransfo=new NonRigidTranformationVTK();
						
						
						
						nrtransfo.setImageSourceandpoints(GuiCLEMButtons.this.matiteclasse.checkgrid,GuiCLEMButtons.this.matiteclasse.source.getValue(), GuiCLEMButtons.this.matiteclasse.sourcepoints);
						nrtransfo.setImageTargetandpoints(GuiCLEMButtons.this.matiteclasse.target.getValue(),GuiCLEMButtons.this.matiteclasse.targetpoints);
						
						nrtransfo.run();
						
						GuiCLEMButtons.this.matiteclasse.updateRoi();
							}});
						}
					
					}
				}
				
				{
					
				}
			
			}
		});
		/**
		 * Button Clear all Lanadmarks Points
		 */
		JButton btnNewButton2 = new JButton("Clear all landmarks points");
		btnNewButton2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if ((GuiCLEMButtons.this.matiteclasse.source.getValue()==null)||(GuiCLEMButtons.this.matiteclasse.target.getValue()==null))
				{
					MessageDialog.showDialog("Make sure source and target image are openned and selected");
					return;
				}
				else{
				new AnnounceFrame("All ROIs have been deleted from images "+GuiCLEMButtons.this.matiteclasse.source.getValue().getName()+" and "+ GuiCLEMButtons.this.matiteclasse.target.getValue().getName(),5);
				deleteROI(GuiCLEMButtons.this.matiteclasse.source.getValue());
				deleteROI(GuiCLEMButtons.this.matiteclasse.target.getValue());
				}
			}

			private void deleteROI(Sequence value) {
				
				value.removeAllROI(true); // true means that this action can be undo from the undo manager
				
			}
		});
		/**
		 * Button Undo 
		 */
		JButton btnButtonUndo = new JButton("Undo last point");
		btnButtonUndo.setToolTipText("Press this button to cancel the last point edition you have done, it will reverse to the previous state of your image");
		btnButtonUndo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if ((GuiCLEMButtons.this.matiteclasse.source.getValue()==null)||(GuiCLEMButtons.this.matiteclasse.target.getValue()==null))
				{
					MessageDialog.showDialog("Make sure source and target image are openned and selected");
					return;
				}
				else{
					boolean sorted=true;
					List<ROI> listRoisource =GuiCLEMButtons.this.matiteclasse.source.getValue().getROIs(sorted);
					// for 2D
					if (((GuiCLEMButtons.this.matiteclasse.mode3D==false)&&listRoisource.size()>2)||((GuiCLEMButtons.this.matiteclasse.mode3D==true)&&listRoisource.size()>3))
					{
					// remove last added ROI, 
					
					
					List<ROI> listRoitarget =GuiCLEMButtons.this.matiteclasse.target.getValue().getROIs(sorted);
					ROI roitoremove = listRoisource.get(listRoisource.size()-1);
					roitoremove.remove();
					if (listRoitarget.size()>=listRoisource.size()){
					ROI roitoremovet = listRoitarget.get(listRoisource.size()-1);
					roitoremovet.remove();
					}
					//read xml doc containing transformations
					Document document = XMLUtil.loadDocument(GuiCLEMButtons.this.matiteclasse.XMLFile);
					// for 3D when we will apply reverse transfo
					double orisizex=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeX();
					double orisizey=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeY();
					double orisizez=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeZ();
					//Reverse RoiPosition and display them on ori source
					GuiCLEMButtons.this.matiteclasse.source.getValue().beginUpdate();
					GuiCLEMButtons.this.matiteclasse.source.getValue().removeAllImages();
					if (GuiCLEMButtons.this.matiteclasse.backupsource == null) {
						MessageDialog
								.showDialog("Argh.");
						return;
					}
					
					try {
						
						for (int t = 0; t < GuiCLEMButtons.this.matiteclasse.backupsource.getSizeT(); t++) {
							for (int z = 0; z < GuiCLEMButtons.this.matiteclasse.backupsource.getSizeZ(); z++) {

								GuiCLEMButtons.this.matiteclasse.source.getValue().setImage(t, z,
										GuiCLEMButtons.this.matiteclasse.backupsource.getImage(t, z));
							}
						}
					}
					//

					finally {

						GuiCLEMButtons.this.matiteclasse.source.getValue().endUpdate();

						// sequence.
					}
					//in order to process the case of first transformation cancelle (in case of big rescaling in particular)
					
							
					//for 2D
					if (GuiCLEMButtons.this.matiteclasse.mode3D==false){
					Matrix correctedtransfo=GuiCLEMButtons.this.matiteclasse.getCombinedTransfo(document);
					Matrix backto_ori=correctedtransfo.inverse();
					updatemyRoi2Dposition(listRoisource,backto_ori);
					// now points are placed on original source file
					// TODO could be used to save the original position of points if asked
					//TODO when exciting: save Roi points on original source file for information (or ask if you ant to do so)
					//remove last computed transfo from xml file
					//transfo2D
					
					}
					else{
					//for 3D
					//update roi positions (in Original sequence size!)
						
						Matrix correctedtransfo=GuiCLEMButtons.this.matiteclasse.getCombinedTransfo3D(document).getMatrix();
						Matrix backto_ori=correctedtransfo.inverse();
						// In that case the orisize is actually the pixel size where it comes from, i.e before that it was reversed to backupsource
						SimilarityTransformation3D reversetransfo=new SimilarityTransformation3D(backto_ori,orisizex,orisizey,orisizez);
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeX(GuiCLEMButtons.this.matiteclasse.bucalibx);
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeY(GuiCLEMButtons.this.matiteclasse.bucaliby);
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeZ(GuiCLEMButtons.this.matiteclasse.bucalibz);
						GuiCLEMButtons.this.matiteclasse.updateSourcePoints3D(reversetransfo); // use source calibration, that's why we make sure that we use the correct one here
						
						GuiCLEMButtons.this.matiteclasse.updateRoi();
					}
					//common
					Element root = XMLUtil.getRootElement(document);
					ArrayList<Element> transfoElementArrayList = XMLUtil.getElements(root,
							"MatrixTransformation");
					int maxorder=0;
					int order=0;
					for (Element transfoElement : transfoElementArrayList) {
						
						

						order = XMLUtil.getAttributeIntValue(transfoElement, "order", 0);
						if (maxorder<order)
							maxorder=order;
						// retire derniere ROI plus eliminer le node de la derniere transfo
						//faire pareil en 3D
						// puis appliquer la transfo pour remettre à jour
					}
					//find element and remove it
					for (Element transfoElement : transfoElementArrayList) {
						
						

						order = XMLUtil.getAttributeIntValue(transfoElement, "order", 0);
						if (maxorder==order){
							org.w3c.dom.Node parent=transfoElement.getParentNode();
							parent.removeChild(transfoElement);
							parent.normalize();
							
						}
						
					}
					transfoElementArrayList = XMLUtil.getElements(root,
							"MatrixTransformation");
					for (Element transfoElement : transfoElementArrayList) {
						order = XMLUtil.getAttributeIntValue(transfoElement, "order", 0);
					}
					XMLUtil.saveDocument(document, GuiCLEMButtons.this.matiteclasse.XMLFile);
					System.out.println("Saved as"+GuiCLEMButtons.this.matiteclasse.XMLFile.getPath());
					
					
					Element newsizeelement = XMLUtil.getElements( root , "TargetSize" ).get(0);

					int width = XMLUtil.getAttributeIntValue( newsizeelement, "width" , -1 );
					int height = XMLUtil.getAttributeIntValue( newsizeelement, "height" , -1 );
					// the following variable will get the default value is the transfrmation was computed in 2D.
					//for 2D
					int nbz = XMLUtil.getAttributeIntValue( newsizeelement, "nz" , -1 );
					Matrix CombinedTransfo=GuiCLEMButtons.this.matiteclasse.getCombinedTransfo(document);
					if (nbz==-1){// it is filled only ion mode 3D, even if the original file was 3D.
						ImageTransformer mytransformer = new ImageTransformer();

						mytransformer.setImageSource(GuiCLEMButtons.this.matiteclasse.source.getValue());
						mytransformer.setParameters(CombinedTransfo);
					
						// warning here: if it was the first transformation , the destination size should be the original size
						// we check this by the number of transf applied (i.e nothing left if it was the first
						
						if (transfoElementArrayList.size()>0)
						{
							mytransformer.setDestinationsize(width,height);
							mytransformer.run();
							// update roiposition accordingly 
							listRoisource =GuiCLEMButtons.this.matiteclasse.source.getValue().getROIs(sorted);
							updatemyRoi2Dposition(listRoisource,CombinedTransfo);
						}
						else // come back to ori:) actually doing nothing, but we still have to set pixelsize
						{
							//mytransformer.setDestinationsize(oriwidth,oriheight);
							//also set back pixel size
							
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeX(GuiCLEMButtons.this.matiteclasse.bucalibx);//TO DO rather by scale
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeY(GuiCLEMButtons.this.matiteclasse.bucaliby);
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeZ(GuiCLEMButtons.this.matiteclasse.bucalibz);
						}
						
						
					}
					
					//for 3D
					else{
						SimilarityTransformation3D transfo = GuiCLEMButtons.this.matiteclasse.getCombinedTransfo3D(document);
						if (transfoElementArrayList.size()>0){
						double targetsx =XMLUtil.getAttributeDoubleValue( newsizeelement, "sx" , -1 );
						double targetsy =XMLUtil.getAttributeDoubleValue( newsizeelement, "sy" , -1 );
						double targetsz =XMLUtil.getAttributeDoubleValue( newsizeelement, "sz" , -1 );
						// write xml file
						Matrix transfomat = transfo.getMatrix();

						Stack3DVTKTransformer transfoimage3D=new Stack3DVTKTransformer();
						transfoimage3D.setImageSource(GuiCLEMButtons.this.matiteclasse.source.getValue(),transfo.getorisizex(),transfo.getorisizey(), transfo.getorisizez());
						transfoimage3D.setDestinationsize(width, height, nbz,
								targetsx, targetsy, targetsz);
						transfoimage3D.setParameters(transfomat,transfo.getscalex(),transfo.getscalez());
						transfoimage3D.run();
						// and also update ROIs
						
						GuiCLEMButtons.this.matiteclasse.updateSourcePoints3D(transfo);
						GuiCLEMButtons.this.matiteclasse.updateRoi();
						}
						else
						//else we do nothing should come back to backupsource just reverse calibration.
						{
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeX(GuiCLEMButtons.this.matiteclasse.bucalibx);//TO DO rather by scale
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeY(GuiCLEMButtons.this.matiteclasse.bucaliby);
							GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeZ(GuiCLEMButtons.this.matiteclasse.bucalibz);
						}
					}
				}
					else{
						new AnnounceFrame("Nothing to undo",5);
					}
				
			}
				
			}
		});
			
					
			
		/**
		 * Button show points on ori source
		 */
		JButton btnButtonshowPoints = new JButton("Show ROIs on original source image");
		btnButtonshowPoints.setToolTipText("Show the original source Image, with the points selected shown (save the source image to save the ROIs)");
		btnButtonshowPoints.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if ((GuiCLEMButtons.this.matiteclasse.source.getValue()==null)||(GuiCLEMButtons.this.matiteclasse.target.getValue()==null))
				{
					MessageDialog.showDialog("Make sure source and target image are openned and selected");
					return;
				}
				else{
					boolean sorted=true;
					List<ROI> listRoisource =GuiCLEMButtons.this.matiteclasse.source.getValue().getROIs(sorted);
					//In 2D
					if (((GuiCLEMButtons.this.matiteclasse.mode3D==false)&&listRoisource.size()>2)||((GuiCLEMButtons.this.matiteclasse.mode3D==true)&&listRoisource.size()>3))
					{
				
					//read xml doc containing transformations
					Document document = XMLUtil.loadDocument(GuiCLEMButtons.this.matiteclasse.XMLFile);
					// for 3D when we will apply reverse transfo
					double orisizex=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeX();
					double orisizey=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeY();
					double orisizez=GuiCLEMButtons.this.matiteclasse.source.getValue().getPixelSizeZ();
					//Reverse RoiPosition and display them on ori source
					GuiCLEMButtons.this.matiteclasse.source.getValue().beginUpdate();
					GuiCLEMButtons.this.matiteclasse.source.getValue().removeAllImages();
					if (GuiCLEMButtons.this.matiteclasse.backupsource == null) {
						MessageDialog
								.showDialog("argh.");
						return;
					}
					
					try {
						
						for (int t = 0; t < GuiCLEMButtons.this.matiteclasse.backupsource.getSizeT(); t++) {
							for (int z = 0; z < GuiCLEMButtons.this.matiteclasse.backupsource.getSizeZ(); z++) {

								GuiCLEMButtons.this.matiteclasse.source.getValue().setImage(t, z,
										GuiCLEMButtons.this.matiteclasse.backupsource.getImage(t, z));
							}
						}
					}
					//

					finally {

						GuiCLEMButtons.this.matiteclasse.source.getValue().endUpdate();

						// sequence.
					}
					
					//for 2D
					if (GuiCLEMButtons.this.matiteclasse.mode3D==false){
					Matrix correctedtransfo=GuiCLEMButtons.this.matiteclasse.getCombinedTransfo(document);
					Matrix backto_ori=correctedtransfo.inverse();
					
					updatemyRoi2Dposition(listRoisource,backto_ori);
					GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeX(GuiCLEMButtons.this.matiteclasse.bucalibx);//TO DO rather by scale
					GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeY(GuiCLEMButtons.this.matiteclasse.bucaliby);
					GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeZ(GuiCLEMButtons.this.matiteclasse.bucalibz);
					}
					else{
					//for 3D
					//update roi positions (in Original sequence size!)
						
						Matrix correctedtransfo=GuiCLEMButtons.this.matiteclasse.getCombinedTransfo3D(document).getMatrix();
						Matrix backto_ori=correctedtransfo.inverse();
						// In that case the orisize is actually the pixel size where it comes from, i.e before that it was reversed to backupsource
						SimilarityTransformation3D reversetransfo=new SimilarityTransformation3D(backto_ori,orisizex,orisizey,orisizez);
						//correct source metadata
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeX(GuiCLEMButtons.this.matiteclasse.bucalibx);//TO DO rather by scale
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeY(GuiCLEMButtons.this.matiteclasse.bucaliby);
						GuiCLEMButtons.this.matiteclasse.source.getValue().setPixelSizeZ(GuiCLEMButtons.this.matiteclasse.bucalibz);
						GuiCLEMButtons.this.matiteclasse.updateSourcePoints3D(reversetransfo); // use source calibration, that's why we make sure that we use the correct one here
						
						GuiCLEMButtons.this.matiteclasse.updateRoi();
						
					}
					
				// Reinitialize XML FILE
					
					Document myXMLdoc = XMLUtil.createDocument(true);
					Element transfoElement = XMLUtil.addElement(
							myXMLdoc.getDocumentElement(), "TargetSize");
					XMLUtil.setAttributeIntValue(transfoElement, "width", GuiCLEMButtons.this.matiteclasse.target.getValue()
							.getWidth());
					XMLUtil.setAttributeIntValue(transfoElement, "height", GuiCLEMButtons.this.matiteclasse.target
							.getValue().getHeight());
					if (GuiCLEMButtons.this.matiteclasse.mode3D){
						XMLUtil.setAttributeIntValue(transfoElement, "nz", GuiCLEMButtons.this.matiteclasse.target.getValue()
								.getSizeZ());
						XMLUtil.setAttributeDoubleValue(transfoElement, "sx" , GuiCLEMButtons.this.matiteclasse.target.getValue()
								.getPixelSizeX() );
						XMLUtil.setAttributeDoubleValue(transfoElement, "sy" , GuiCLEMButtons.this.matiteclasse.target.getValue()
								.getPixelSizeY() );
						XMLUtil.setAttributeDoubleValue(transfoElement, "sz" , GuiCLEMButtons.this.matiteclasse.target.getValue()
								.getPixelSizeZ() );
					}
					XMLUtil.saveDocument(myXMLdoc, GuiCLEMButtons.this.matiteclasse.XMLFile);
					System.out.println("Saved as"+GuiCLEMButtons.this.matiteclasse.XMLFile.getPath());
			}
				}
			}
			});
		
		
		add(btnNewButton);
		add(btnNewButton2);
		add(btnButtonUndo);
		add(btnButtonshowPoints);
		
	}
	protected void updatemyRoi2Dposition(List<ROI> listRoisource, Matrix matrixtobeapplied) {
		// TODO Auto-generated method stub
		for (int i = 0; i < listRoisource.size(); i++) {
			ROI roi = listRoisource.get(i);
			Point5D pnt = roi.getPosition5D();
			double newX = matrixtobeapplied.get(0, 3) + matrixtobeapplied.get(0, 0)
			* pnt.getX() + matrixtobeapplied.get(0, 1) * pnt.getY();
			double newY = matrixtobeapplied.get(1, 3) + matrixtobeapplied.get(1, 0)
			* pnt.getX() + matrixtobeapplied.get(1, 1) * pnt.getY();
			pnt.setX(newX);
			pnt.setY(newY);
	        roi.setPosition5D(pnt);
		}
		GuiCLEMButtons.this.matiteclasse.GetSourcePointsfromROI(); 
	}
		
}