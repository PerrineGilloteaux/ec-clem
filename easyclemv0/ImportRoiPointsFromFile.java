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

import java.io.BufferedReader;

import java.io.FileNotFoundException;



import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.ToolTipFrame;
import icy.preferences.ApplicationPreferences;
import icy.roi.ROI;
import plugins.kernel.roi.roi2d.ROI2DPoint;
import icy.sequence.Sequence;
import icy.type.point.Point5D;

import plugins.adufour.ezplug.EzPlug;

import plugins.adufour.ezplug.EzVarFile;

import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;

import java.io.FileReader;
import java.io.IOException;
/**
 * TODO: read a csv, xls? or txt file and import ROI to the selected sequence, in trhe same order
 * TODO generate the non rigid landmarks registration based on ITK
 * @author paul-gilloteaux-p
 *
 */
public class ImportRoiPointsFromFile extends EzPlug{

	private EzVarSequence source;
	private EzVarFile csvfile;
	
	private double converttopixelXY;
	private double converttopixelZ;
	private EzVarText choiceinputsection;

	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		Sequence sourceseq=source.getValue();
		String unit=choiceinputsection.getValue();
		if (unit=="millimeters"){
			converttopixelXY=(1000*sourceseq.getPixelSizeX());
			converttopixelZ=(1000*sourceseq.getPixelSizeZ());
		}
		if (unit=="nanometers"){
			converttopixelXY=sourceseq.getPixelSizeX()/1000;
			converttopixelZ=sourceseq.getPixelSizeZ()/1000;
		}
		if (unit=="micrometers"){
			converttopixelXY=sourceseq.getPixelSizeX();
			converttopixelZ=sourceseq.getPixelSizeZ();
		}
		if (unit=="pixels"){
			converttopixelXY=100;
			converttopixelZ=100;
		}
		if (sourceseq==null){
			MessageDialog.showDialog("Please make sure that your image is opened");
			return;
		}
		BufferedReader br=null;
		try {

			 br = new BufferedReader(new FileReader(csvfile.getValue()));
			String line;
			String cvsSplitBy = ";";
			int index=1;
			//converttopixelZ=0;
			while ((line = br.readLine()) != null) {

			        // use comma as separator
				String[] coordinates = line.split(cvsSplitBy);

				System.out.println("x= " + coordinates[0] 
	                                 + "  y=" + coordinates[1]  + " z="+coordinates[2] );
				double x=Double.parseDouble(coordinates[0])*converttopixelXY;
				double y=Double.parseDouble(coordinates[1])*converttopixelXY;
				double z=Double.parseDouble(coordinates[2])*converttopixelZ;
				ROI roi =new ROI2DPoint();
					
					Point5D position = roi.getPosition5D();
					position.setX(x);
					position.setY(y);
					position.setZ(z);
					roi.setPosition5D(position);
					roi.setName("Point "+ index);
					source.getValue().addROI(roi);
				index=index+1;

			}
			index=index-1;
			MessageDialog.showDialog("Number of Roi added: "+index );
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

	@Override
	protected void initialize() {
		
		new ToolTipFrame(    			
    			"<html>"+
    			"<br>This will load Rois to the image and numbered them in line order. "+
    			"<br>File should be in ascii format (txt or csv for example), with one point per line and "+
    			"<br>a semi column separator. No header."+
    			"<br> x1;y1;z1"+
    			"<br> x2;y2;z2"+
    			" <br>Do the same with source and target image and you are ready to apply the transform of your choice"+
    			"</html>"
    			);
		
		source=new EzVarSequence("Please select the sequence on which you want to create the ROIs");
		String varName ="Please select the ROI file (csv format)";
		if (source.getValue()!=null)
			csvfile=new EzVarFile(varName, source.getValue().getFilename());
		else
			csvfile=new EzVarFile(varName, ApplicationPreferences.getPreferences().node("frame/imageLoader").get("path", "."));
		choiceinputsection = new EzVarText("Unit of the points in csv file",
				new String[] { "millimeters", "micrometers","nanometers" ,"pixels" }, 0, false);
		
		addEzComponent(csvfile);
		addEzComponent(choiceinputsection);
		addEzComponent(source);
		// we will express everything in pixel, by using a converttopixel factor
		
		
		
		

		
	}

}
