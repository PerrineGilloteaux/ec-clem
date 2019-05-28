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
 * will apply the transform content in an xml file as in easyclem,
 * but specifying if the orginal scale has changed (both for source and target)
 */

package plugins.perrine.easyclemv0;

import java.io.File;
import icy.sequence.DimensionId;
import Jama.Matrix;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.file.Saver;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.ToolTipFrame;
import icy.preferences.ApplicationPreferences;
import icy.sequence.Sequence;
import plugins.perrine.easyclemv0.image_transformer.ImageTransformerFactory;
import plugins.perrine.easyclemv0.image_transformer.RigidImageTransformerInterface;
import plugins.perrine.easyclemv0.model.SequenceSize;
import plugins.perrine.easyclemv0.storage.xml.SequenceSizeXmlReader;
import plugins.perrine.easyclemv0.storage.xml.XmlFileReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlWriter;

public class ApplyTransfotoScaledImage extends EzPlug {
	
	private EzVarSequence source;
	private EzVarFile xmlFile;
	private EzVarInteger sourcebinning;
	private EzVarInteger targetbinning;

	private ImageTransformerFactory imageTransformerFactory = new ImageTransformerFactory();
	private XmlFileReader xmlFileReader = new XmlFileReader();
	private SequenceSizeXmlReader sequenceSizeXmlReader = new SequenceSizeXmlReader();
	private RigidTransformationXmlReader rigidTransformationXmlReader;
	private RigidTransformationXmlWriter rigidTransformationXmlWriter;
	
	@Override
	protected void initialize() {
		new ToolTipFrame(
    			"<html>"+
    			"<br>If you have worked on a resized (reduced) version of source and "+
    			"<br> of target image for time and memory purpose, "+
    			"<br> the transfo computed from this reduced image will be rescale to fit the original size" +
    			"<br> of your source and target images"+
    			"<br><b> Source binning </b> is the reduced scale for source that you used to compute the transfo, "+
    			"<br><b> Target binning </b> is the reduced scale for target that you used to compute the transfo, "+
    			" <br>Example : you have processed reduced size source of 512*512 of a 2048x2048 original source file"+
    			" <br>and a reduced size target of 512*512 of a 4096x4096 original target file"+
    			" <br>Source binning will be 4 (2048/ 512) and target binning will be 8 (4096 / 512)"+
    			"</html>"
		);

		source = new EzVarSequence("Select Source Image Full Size (will be transformed from xml file)");
		String varName = "Xml file containing list of transformation (computed on reduced images)";
		if (source.getValue() != null) {
			xmlFile = new EzVarFile(varName, source.getValue().getFilename());
		} else {
			xmlFile = new EzVarFile(varName, ApplicationPreferences.getPreferences().node("frame/imageLoader").get("path", "."));
		}
		sourcebinning = new EzVarInteger("Source Binning ",1, 100, 1);
		targetbinning = new EzVarInteger("Target Binning ",1, 100, 1);

		addEzComponent(source);
		addEzComponent(xmlFile);
		addEzComponent(sourcebinning);
		addEzComponent(targetbinning);
	}

	@Override
	protected void execute() {

		Sequence sourceseq = source.getValue();

		if (sourceseq==null){
			MessageDialog.showDialog("Please make sure that your image is opened");
			return;
		}

		rigidTransformationXmlWriter = new RigidTransformationXmlWriter(xmlFileReader.loadFile(xmlFile.getValue()));

		SequenceSize sequenceSize = sequenceSizeXmlReader.readSize(xmlFileReader.loadFile(xmlFile.getValue()));
		Matrix read = rigidTransformationXmlReader.read(xmlFileReader.loadFile(xmlFile.getValue()));
		RigidImageTransformerInterface imageTransformer = imageTransformerFactory.createImageTransformer(read.getColumnDimension());
		imageTransformer.setSourceSequence(sourceseq);
		imageTransformer.setTargetSize(sequenceSize);
		imageTransformer.setParameters(read);
		imageTransformer.run();

		IcyCanvas sourcecanvas = source.getValue().getFirstViewer().getCanvas();
		if (sourcecanvas instanceof IcyCanvas2D) {
			((IcyCanvas2D) sourcecanvas).fitCanvasToImage();
		}

		sourceseq.setFilename(sourceseq.getFilename() + " (transformed)");
		sourceseq.setName(sourceseq.getName() + " (transformed)");
		sourceseq.setPixelSizeX(sequenceSize.get(DimensionId.X).getPixelSizeInNanometer() * 1.0 / targetbinning.getValue());
		sourceseq.setPixelSizeY(sequenceSize.get(DimensionId.Y).getPixelSizeInNanometer() * 1.0 / targetbinning.getValue());
		sourceseq.setPixelSizeZ(sequenceSize.get(DimensionId.Z).getPixelSizeInNanometer());
		File file = new File(sourceseq.getFilename());
		boolean multipleFiles = false;
		boolean showProgress = true;
		System.out.println("Save as" + sourceseq.getFilename());
		Saver.save(sourceseq, file, multipleFiles, showProgress);
		MessageDialog.showDialog("Transformation have been applied. Image has been renamed and saved, use this one for going on with your alignments");
	}

	@Override
	public void clean() {
	}
}
