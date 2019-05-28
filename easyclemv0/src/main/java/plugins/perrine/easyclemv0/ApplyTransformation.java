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
 * AUthor: Perrine.Paul-Gilloteaux@curie.fr
 * Main Class can be used alone or call from another plugin: 
 * will apply the transform content in an xml file as in easyclem
 */

package plugins.perrine.easyclemv0;

import java.io.File;
import org.w3c.dom.Document;
import Jama.Matrix;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.vars.lang.VarSequence;
import plugins.perrine.easyclemv0.image_transformer.ImageTransformer;
import plugins.perrine.easyclemv0.image_transformer.NonRigidTranformationVTK;
import plugins.perrine.easyclemv0.image_transformer.Stack3DVTKTransformer;
import plugins.perrine.easyclemv0.model.FiducialSet;
import plugins.perrine.easyclemv0.model.SequenceSize;
import plugins.perrine.easyclemv0.storage.xml.SequenceSizeXmlReader;
import plugins.perrine.easyclemv0.storage.xml.XmlFileReader;
import plugins.perrine.easyclemv0.storage.xml.XmlFileWriter;
import plugins.perrine.easyclemv0.storage.xml.non_rigid.NonRigidTransformationXmlReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlWriter;
import vtk.vtkDataSet;
import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.file.Saver;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.ProgressFrame;
import icy.preferences.ApplicationPreferences;
import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzLabel;

public class ApplyTransformation extends EzPlug implements Block {
	
	private EzVarSequence source = new EzVarSequence("Select Source Image (will be transformed from xml file)");
	private EzVarFile xmlFile=new EzVarFile("Xml file containing list of transformation", ApplicationPreferences.getPreferences().node("frame/imageLoader").get("path", "."));;
	private int extentx;
	private int extenty;
	private int extentz;
	private double spacingx;
	private double spacingy;
	private double spacingz;
	private vtkDataSet[] imageData;
	private double Inputspacingx;
	private double Inputspacingy;
	private double Inputspacingz;
	private Runnable transformer;
	private VarSequence out= new VarSequence("output sequence", null);
	private int auto;

	private XmlFileReader xmlFileReader = new XmlFileReader();
	private XmlFileWriter xmlFileWriter = new XmlFileWriter();
	private RigidTransformationXmlWriter xmlWriter;

	private SequenceSizeXmlReader sequenceSizeXmlReader = new SequenceSizeXmlReader();
	private NonRigidTransformationXmlReader nonRigidTransformationXmlReader;
	private RigidTransformationXmlReader rigidTransformationXmlReader = new RigidTransformationXmlReader();
	
	
	@Override
	protected void initialize() {
		EzLabel textinfo = new EzLabel("Please select the image on which you want to apply a transformation, and the xml file containing the transformations (likely your file name _transfo.xml)");
		String varName ="Xml file containing list of transformation";
		if (source.getValue()!=null)
			xmlFile=new EzVarFile(varName, source.getValue().getFilename());
		else
			xmlFile=new EzVarFile(varName, ApplicationPreferences.getPreferences().node("frame/imageLoader").get("path", "."));
		
		addEzComponent(textinfo);
		addEzComponent(source);
		addEzComponent(xmlFile);
	}

	@Override
	protected void execute() {
		final Sequence sourceseq = source.getValue();
		if (sourceseq == null) {
			MessageDialog.showDialog("Please make sure that your image is opened");
			return;
		}

		transformer = new Runnable() {

	        @Override
	        public void run() {

				Document document = xmlFileReader.loadFile(xmlFile.getValue());
				boolean rigid = xmlFileReader.isRigid(document);

				if (!rigid) {
					ProgressFrame progress = new ProgressFrame("Applying the NON RIGID transformation...");
					FiducialSet fiducialSet = nonRigidTransformationXmlReader.read(document);
					SequenceSize sequenceSize = sequenceSizeXmlReader.readSize(document);
					NonRigidTranformationVTK nonRigidTranformationVTK = new NonRigidTranformationVTK();
					nonRigidTranformationVTK.setSourceSequence(source.getValue());
					nonRigidTranformationVTK.setTargetSize(sequenceSize);
					nonRigidTranformationVTK.run(fiducialSet);
					MessageDialog.showDialog("Non rigid transform as been applied");
					progress.close();
				} else {
					Matrix read = rigidTransformationXmlReader.read(document);
					if(read.getColumnDimension() == 2) {
						ProgressFrame progress = new ProgressFrame("Applying 2D RIGID transformation...");
						ImageTransformer mytransformer = new ImageTransformer();
						SequenceSize sequenceSize = sequenceSizeXmlReader.readSize(document);
						mytransformer.setSourceSequence(source.getValue());
						mytransformer.setParameters(read);
						mytransformer.setTargetSize(sequenceSize);
						mytransformer.run();
						progress.close();
					} else if(read.getColumnDimension() == 3) {
						ProgressFrame progress = new ProgressFrame("Applying 3D RIGID transformation...");
						Stack3DVTKTransformer transfoimage3D = new Stack3DVTKTransformer();
						SequenceSize sequenceSize = sequenceSizeXmlReader.readSize(document);
						transfoimage3D.setSourceSequence(source.getValue());
//						transfoimage3D.setRecenter(recenter);
						transfoimage3D.setParameters(read);
						transfoimage3D.run();
						progress.close();
					}
				}


//				Element newsizeelement = XMLUtil.getElements( root , "TargetSize" ).get(0);
//				int width = XMLUtil.getAttributeIntValue( newsizeelement, "width" , -1 );
//				int height = XMLUtil.getAttributeIntValue( newsizeelement, "height" , -1 );
//				int recenter= XMLUtil.getAttributeIntValue( newsizeelement, "recenter" , 0 );
//				double targetsx =XMLUtil.getAttributeDoubleValue( newsizeelement, "sx" , -1 );
//				double targetsy =XMLUtil.getAttributeDoubleValue( newsizeelement, "sy" , -1 );
//				double targetsz =XMLUtil.getAttributeDoubleValue( newsizeelement, "sz" , -1 );


//				int nbz = XMLUtil.getAttributeIntValue( newsizeelement, "nz" , -1 );
//				auto= XMLUtil.getAttributeIntValue( newsizeelement, "auto" , 0 );
//				Matrix CombinedTransfo=getCombinedTransfo(document);
//				// check if it comes from the autofinder (tag auto set to 1, 0 otherwise)
//				if (auto==1){
//					ProgressFrame progress = new ProgressFrame("Applying transform from AUTOFINDER");
//					Inputspacingx=	source.getValue().getPixelSizeX();
//					Inputspacingy=	source.getValue().getPixelSizeY();
//					Inputspacingz=	source.getValue().getPixelSizeZ();
//					ApplyautoTransform(CombinedTransfo,width,height,nbz,targetsx,targetsy,targetsz);
//
//					progress.close();
//					return;
//				}

//				if (nbz==-1){// it is filled only in mode 3D, even if the original file was 3D.
//					ProgressFrame progress = new ProgressFrame("Applying 2D RIGID transformation...");
//					ImageTransformer mytransformer = new ImageTransformer();
//					mytransformer.setSourceSequence(source.getValue());
//					mytransformer.setParameters(CombinedTransfo);
//					// mytransformer.setParameters(0,0,0,0,scale);
//					mytransformer.setTargetSize(width, height);
//					mytransformer.run();
//					progress.close();
//					if (targetsx!=-1) //xml fie generated with oldest version for 2D if -1, do nothing
//					{
//						source.getValue().setPixelSizeX(targetsx);
//						source.getValue().setPixelSizeY(targetsy);
//						source.getValue().setPixelSizeZ(targetsz);
//					}
//				} else {
//					ProgressFrame progress = new ProgressFrame("Applying 3D RIGID transformation...");
//
//					Matrix transfomat = xmlFileWriter.read();
//
//					Stack3DVTKTransformer transfoimage3D=new Stack3DVTKTransformer();
//					transfoimage3D.setSourceSequence(source.getValue());
//					transfoimage3D.setTargetSize(width, height, nbz, targetsx, targetsy, targetsz);
//					transfoimage3D.setRecenter(recenter);
//					transfoimage3D.setParameters(transfomat);
//					transfoimage3D.run();
//					progress.close();
//				}


				if (!isHeadLess()) {
					IcyCanvas sourcecanvas = source.getValue().getFirstViewer().getCanvas();
					if (sourcecanvas instanceof IcyCanvas2D) {
						((IcyCanvas2D) sourcecanvas).fitCanvasToImage();
					}
				}
				sourceseq.setFilename(sourceseq.getFilename() + " (transformed)");
				sourceseq.setName(sourceseq.getName() + " (transformed)");
				File file = new File(sourceseq.getFilename());
				boolean multipleFiles = false;
				boolean showProgress = true;
				System.out.println("Transformed Image will be saved as " +sourceseq.getFilename());
				Saver.save(sourceseq, file, multipleFiles, showProgress);

				if (!ApplyTransformation.this.isHeadLess()) {
					MessageDialog.showDialog("Transformation have been applied. Image has been renamed and saved, use this one for going on with your alignments");
				}

			}
		};

		if (!this.isHeadLess()){
			ThreadUtil.bgRun(transformer);
		} else {
			ThreadUtil.invokeNow(transformer);
			if (auto!=1) {
				out.setValue(sourceseq);
			}
		}
	}

	@Override
	public void clean() {
	}

	@Override
	public void declareInput(VarList inputMap) {
		inputMap.add("Input Image",source.getVariable());
		inputMap.add("Imput XML File",xmlFile.getVariable());
	}

	@Override
	public void declareOutput(VarList outputMap) {
		outputMap.add("Transformed Sequence", out);
	}
}
