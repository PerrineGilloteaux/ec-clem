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





import java.io.File;
import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import icy.canvas.IcyCanvas;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;


import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
import icy.system.thread.ThreadUtil;
import icy.type.DataType;
import icy.util.XMLUtil;
import plugins.adufour.ezplug.EzLabel;
import plugins.adufour.ezplug.EzPlug;

import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.ezplug.EzVarText;
import plugins.kernel.canvas.VtkCanvas;
import vtk.vtkCamera;
import vtk.vtkDataArray;
import vtk.vtkDataSet;
import vtk.vtkDoubleArray;
import vtk.vtkFloatArray;
import vtk.vtkImageChangeInformation;
import vtk.vtkImageData;
import vtk.vtkImageReslice;
import vtk.vtkIntArray;
import vtk.vtkMatrix4x4;
import vtk.vtkPointData;
import vtk.vtkPoints;
import vtk.vtkPolyData;
import vtk.vtkShortArray;
import vtk.vtkTransform;
import vtk.vtkTransformPolyDataFilter;
import vtk.vtkUnsignedCharArray;
import vtk.vtkUnsignedIntArray;
import vtk.vtkUnsignedShortArray;


public class TransformBasedonCameraView extends EzPlug {
	EzVarSequence source;
	private Sequence sequence;
	private vtkDataSet[] imageData;
	private double InputSpacingx;
	private double InputSpacingy;
	private double InputSpacingz;
	private Runnable transformer;
	private Sequence sequence2;
	EzVarText choiceinputsection = new EzVarText("Output volume:", new String[]{"crop the data to match original dimensions (keep size and metadata)", "keep full output volume (adapt image size but keep metadata"},0, false);
	private boolean modeboundingbox;
	
	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		// TODO Auto-generated method stub
		sequence=source.getValue();
		if (choiceinputsection.getValue() == "crop the data to match original dimensions (keep size and metadata)"){
		
			modeboundingbox =false;
		}
		else
		{
			modeboundingbox=true;
		}
		//for(Viewer v: Icy.getMainInterface().getViewers(sourceseq))
			
			Viewer v=sequence.getFirstViewer();	
			IcyCanvas mycanvas = v.getCanvas();
			
			//VtkCanvas mycanvas=new VtkCanvas(v);
			
			if (v.getCanvas().getClass().getName()=="plugins.kernel.canvas.VtkCanvas")
			{
				//MessageDialog.showDialog("here we go");
				VtkCanvas test=(VtkCanvas) mycanvas;
				
				vtkCamera mycam=test.getCamera();
				//vtkMatrix4x4 viewtransform=new vtkMatrix4x4();
				//test.getRenderer().ResetCamera();
				//mycam.SetViewAngle(180.0);
				
				
				System.out.println("View plane normal: "+mycam.GetViewPlaneNormal()[0]);
				System.out.println(mycam.GetViewPlaneNormal()[1]);
				System.out.println(mycam.GetViewPlaneNormal()[2]);
				
			
				vtkMatrix4x4 viewmatrix=mycam.GetViewTransformMatrix();
				
				
				vtkMatrix4x4 newmatrix=new vtkMatrix4x4();
				for (int i=0;i<4;i++)
					for (int j=0;j<4;j++)
					{
						newmatrix.SetElement(i, j, viewmatrix.GetElement(i, j));
					}
				
				/*newmatrix.Identity();
				newmatrix.SetElement(0, 0,0);
				newmatrix.SetElement(0, 1,-1);
				newmatrix.SetElement(1, 0,1);
				newmatrix.SetElement(1, 1,0);*/
				newmatrix.SetElement(0, 3, 0);
				newmatrix.SetElement(1, 3,0);
				newmatrix.SetElement(2, 3,0);
				
				vtkMatrix4x4 correctionMatrix=new vtkMatrix4x4();
				correctionMatrix.Identity();
				
				correctionMatrix.SetElement(1, 1,-1);
				correctionMatrix.SetElement(2, 2,-1);
				//Rx(180)=[1 0 0, 0 -1 0, 0 0 -1]
				
				final vtkTransform viewtransform=new vtkTransform();
				viewtransform.SetMatrix(newmatrix);
		
				viewtransform.PostMultiply(); // if T is the current transform, concantenate will then compute T=T*R, where R is the new matrix
				// Here for conveninece of user, we want to say vtk that first the data view from below, we transform accordingly the view first
				
				viewtransform.Concatenate(correctionMatrix);
				
				
				//writeTransfo(viewtransform.GetMatrix(),order);
				this.InputSpacingx=this.sequence.getPixelSizeX();
				this.InputSpacingy=this.sequence.getPixelSizeY();
				this.InputSpacingz=this.sequence.getPixelSizeZ();
				final DataType oriType = sequence.getDataType_();
				try {
				sequence2=SequenceUtil.getCopy(sequence);
				
				transformer = new Runnable() {
					@Override
			        public void run()
			        {
				System.out.println("I will apply transfo now");
				ProgressFrame progress = new ProgressFrame("Applying the transformation...");
				int nbc = sequence.getSizeC();
				imageData=new vtkDataSet[nbc];
				writeTransfo3D(viewtransform.GetMatrix());
				int nbt = sequence.getSizeT();
				int nbz = sequence.getSizeZ();

				int w = sequence.getSizeX();
				int h = sequence.getSizeY();
				for (int c=0;c<sequence.getSizeC();c++){

					converttoVtkImageData(c);

					vtkImageChangeInformation change=new vtkImageChangeInformation();
					change.SetInputData(imageData[c]);
					
					change.CenterImageOn();
					change.Update();
					
					vtkImageReslice ImageReslice = new vtkImageReslice();
					ImageReslice.SetInputData(change.GetOutput());
					
					ImageReslice.SetOutputDimensionality(3);
					//ImageReslice.SetOutputOrigin(0,0,0);
					
					ImageReslice.SetOutputSpacing(InputSpacingx, InputSpacingy, InputSpacingz);
					
					if (modeboundingbox){
						
						
						// then we rotate the bounding box by creating a polydata
						   vtkTransformPolyDataFilter tr=new  vtkTransformPolyDataFilter();

						    vtkPoints mypoints = new vtkPoints();
						    mypoints.SetNumberOfPoints(2);
							
						    mypoints.SetPoint(0,0, 0,0);
						    mypoints.SetPoint(1,sequence.getSizeX()*InputSpacingx, sequence.getSizeY()*InputSpacingy,sequence.getSizeZ()*InputSpacingz);
						   
							vtkPolyData boundspolydata=new vtkPolyData();
							  
							boundspolydata.SetPoints(mypoints);
							tr.SetInputData(boundspolydata);
							  
							tr.SetTransform(viewtransform);
							tr.Update(); 
							
							vtkPolyData modifiedboundpoints = tr.GetOutput();
							double [] bounds= new double[6];
								double[] newpos=modifiedboundpoints.GetPoint(0);
								bounds[0]=Math.abs(newpos[0]/InputSpacingx);
								bounds[2]=Math.abs(newpos[1]/InputSpacingy);
								bounds[4]=Math.abs(newpos[2]/InputSpacingz);
								newpos=modifiedboundpoints.GetPoint(1);
								bounds[1]=Math.abs(newpos[0]/InputSpacingx);
								bounds[3]=Math.abs(newpos[1]/InputSpacingy);
								bounds[5]=Math.abs(newpos[2]/InputSpacingz);
								 w = 1+(int)bounds[1]-(int)bounds[0];
								 h = 1+(int)bounds[3]-(int)bounds[2];
								 nbz=1+(int)bounds[5]-(int)bounds[4];
							
						ImageReslice.SetOutputExtent((int)bounds[0],(int)bounds[1],(int)bounds[2],(int)bounds[3],(int)bounds[4],(int)bounds[5]) ;	
						
						
					}
					
					else{
						ImageReslice.SetOutputExtent(0, sequence.getSizeX()-1, 0, sequence.getSizeY()-1, 0, sequence.getSizeZ()-1); // to be checked: transform is applied twice?
						nbt = sequence.getSizeT();
						nbz = sequence.getSizeZ();

						w = sequence.getSizeX();
						h = sequence.getSizeY();
					}
					ImageReslice.SetResliceTransform(viewtransform.GetInverse());
					System.out.println(viewtransform.GetInverse());
					ImageReslice.SetInterpolationModeToLinear();

					ImageReslice.Update();
					//vtkTransform mytransfo2 = new vtkTransform();
					//mytransfo2.Scale(this.scalexy, this.scalexy, this.scalez);
					//this.ImageReslice.SetResliceTransform(mytransfo2);
					//this.ImageReslice.Update();

					imageData[c] = ImageReslice.GetOutput();
					
				}

			
				DataType datatype = sequence.getDataType_();
				
				sequence2.beginUpdate();
				sequence2.removeAllImages();
				progress.setLength(nbz);
				try {// here finally we convert all 3D images to unsigned 8 bits
					// final ArrayList<IcyBufferedImage> images =
					// sequence.getAllImage();

				switch(oriType){
				case UBYTE:
					for (int t = 0; t < nbt; t++) {
						for (int z = 0; z < nbz; z++) {
							IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
									datatype);
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
							progress.setPosition(z);
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
				sequence2.setPixelSizeX(InputSpacingx);
				sequence2.setPixelSizeY(InputSpacingy);
				sequence2.setPixelSizeZ(InputSpacingz);
			} finally {

				sequence2.endUpdate();
				
				// sequence.
			}
			
				
				progress.close();
				
				sequence2.setName("rotated");
			
			System.out.println("have been applied");
			}
				};
				ThreadUtil.bgRunSingle(transformer);
				
				   
				addSequence(sequence2);
				  
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}}
			else
			{
				MessageDialog.showDialog("Please switch to 3D view");
			}
	}

	protected void writeTransfo3D(vtkMatrix4x4 transfo) {
		String name = source.getValue().getFilename() + "_3D_MANUAL_ROTATE_transfo.xml";
		File XMLFile = new File(name);
		Document document = XMLUtil.createDocument(true);
		/*vtkMatrix4x4 newtransfo=new vtkMatrix4x4();
		vtkMatrix4x4 translate=new vtkMatrix4x4();
		translate.SetElement(0, 3, -source.getValue().getPixelSizeX()*(double)source.getValue().getSizeX()/2);
		translate.SetElement(1, 3, -source.getValue().getPixelSizeY()*(double)source.getValue().getSizeY()/2);
		translate.SetElement(2, 3, -source.getValue().getPixelSizeZ()*(double)source.getValue().getSizeZ()/2);
	    newtransfo.Multiply4x4(transfo,translate,newtransfo);*/
		Element transfoElement1 = XMLUtil.addElement(
				document.getDocumentElement(), "TargetSize");
		XMLUtil.setAttributeIntValue(transfoElement1, "width", sequence.getSizeX());
		XMLUtil.setAttributeIntValue(transfoElement1, "height", sequence.getSizeY());
	
			XMLUtil.setAttributeIntValue(transfoElement1, "nz", sequence.getSizeZ());
					
			XMLUtil.setAttributeDoubleValue(transfoElement1, "sx" , InputSpacingx );
			XMLUtil.setAttributeDoubleValue(transfoElement1, "sy" , InputSpacingy );
			XMLUtil.setAttributeDoubleValue(transfoElement1, "sz" , InputSpacingz );
			XMLUtil.setAttributeIntValue(transfoElement1, "recenter", 1);
		
		
		Element transfoElement = XMLUtil.addElement(
				document.getDocumentElement(), "MatrixTransformation");

		XMLUtil.setAttributeIntValue(transfoElement, "order", 0);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m00",
				transfo.GetElement(0, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m10",
				transfo.GetElement(0, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m20",
				transfo.GetElement(0, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m30",
				transfo.GetElement(0, 3));
		//XMLUtil.setAttributeDoubleValue(transfoElement, "m30",
			//	source.getValue().getPixelSizeX()*(double)source.getValue().getSizeX()/2);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m01",
				transfo.GetElement(1, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m11",
				transfo.GetElement(1, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m21",
				transfo.GetElement(1, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m31",
				transfo.GetElement(1, 3));
		//XMLUtil.setAttributeDoubleValue(transfoElement, "m31",
		//		source.getValue().getPixelSizeY()*(double)source.getValue().getSizeY()/2);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m02",
				transfo.GetElement(2, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m12",
				transfo.GetElement(2, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m22",
				transfo.GetElement(2, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m32",
				transfo.GetElement(2, 3));
		//XMLUtil.setAttributeDoubleValue(transfoElement, "m32",
		//		source.getValue().getPixelSizeZ()*(double)source.getValue().getSizeZ()/2);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m03",transfo.GetElement(3, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m13", transfo.GetElement(3, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m23", transfo.GetElement(3, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m33", transfo.GetElement(3, 3));
		XMLUtil.setAttributeDoubleValue(transfoElement,"formerpixelsizeX",InputSpacingx);
		XMLUtil.setAttributeDoubleValue(transfoElement,"formerpixelsizeY",InputSpacingy);
		XMLUtil.setAttributeDoubleValue(transfoElement,"formerpixelsizeZ",InputSpacingz);
		XMLUtil.setAttributeValue(transfoElement, "process_date",
				new Date().toString());
		XMLUtil.saveDocument(document, XMLFile);
		System.out.println("Saved as"+XMLFile.getPath());
		new AnnounceFrame("Transfo Saved as"+XMLFile.getPath());
	}

	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
		
		
		EzLabel textinfo1=new EzLabel("Manual Prealignment:"); 
		EzLabel textinfo3=new EzLabel("Please select the stack you want to transform,\n select 3D view canvas and then,\n turn in the direction you want to reslice it. \nWhen ready, press play.The transform will be saved \n and can be reapplied through Ec-CLEM later if needed. ");
		
		source=new EzVarSequence("Select Source Stack ");
		addEzComponent(textinfo1);
		addEzComponent(source);
		
		addEzComponent(textinfo3);
		addEzComponent(choiceinputsection);
	
		
	
		
	}
	/**
	 * this part is a copy and paste from canvas3D Icy
	 * there is a big limitation for now: it will apply only on one channel, one time frame
	 * @param posC 
	 */
	void converttoVtkImageData(int posC) {
		
		if (this.sequence == null)
			return;

		final int sizeX = sequence.getSizeX();
		final int sizeY = sequence.getSizeY();
		final int sizeZ = sequence.getSizeZ();
		final DataType dataType = sequence.getDataType_();
		final int posT = sequence.getFirstViewer().getPositionT();
		//final int posC = sequence2.getFirstViewer().getPositionC(); // question: whu did I bother with posC? vtk imageData have only 3 dimensions X Y Z

		// create a new image data structure
		final vtkImageData newImageData = new vtkImageData();

		newImageData.SetDimensions(sizeX, sizeY, sizeZ);
		newImageData.SetSpacing(this.InputSpacingx, this.InputSpacingy, this.InputSpacingz);
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
				((vtkUnsignedCharArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsByte(posT));
			else
				((vtkUnsignedCharArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsByte(posT, posC));
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
				((vtkUnsignedCharArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsByte(posT));
			else
				((vtkUnsignedCharArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsByte(posT, posC));
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
				((vtkUnsignedShortArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsShort(posT));
			else
				((vtkUnsignedShortArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsShort(posT, posC));
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
				((vtkShortArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsShort(posT));
			else
				((vtkShortArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsShort(posT, posC));
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
				((vtkUnsignedIntArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsInt(posT));
			else
				((vtkUnsignedIntArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsInt(posT, posC));
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
				((vtkIntArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsInt(posT));
			else
				((vtkIntArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsInt(posT, posC));
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
				((vtkFloatArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsFloat(posT));
			else
				((vtkFloatArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsFloat(posT, posC));
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
				((vtkDoubleArray) array).SetJavaArray(sequence
						.getDataCopyCXYZAsDouble(posT));
			else
				((vtkDoubleArray) array).SetJavaArray(sequence
						.getDataCopyXYZAsDouble(posT, posC));
			break;

		default:
			// we probably have an empty sequence
			newImageData.SetDimensions(1, 1, 1);
			newImageData.SetSpacing(sequence.getPixelSizeX(), sequence.getPixelSizeY(), sequence.getPixelSizeZ());
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

		imageData[posC] = newImageData;
	}
	
	
	/*private void writeTransfo(vtkMatrix4x4 vtkMatrix4x4, int order) {

		// Matrix transfo = newtransfo.getMatrix();
		
		Document document = XMLUtil.loadDocument(XMLFile);
		Element transfoElement = XMLUtil.addElement(
				document.getDocumentElement(), "MatrixTransformation");

		XMLUtil.setAttributeIntValue(transfoElement, "order", order);
		XMLUtil.setAttributeDoubleValue(transfoElement, "m00",
				vtkMatrix4x4.GetElement(0, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m01",
				vtkMatrix4x4.GetElement(0, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m02",
				vtkMatrix4x4.GetElement(0, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m03",
				vtkMatrix4x4.GetElement(0, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m10",
				vtkMatrix4x4.GetElement(1, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m11",
				vtkMatrix4x4.GetElement(1, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m12",
				vtkMatrix4x4.GetElement(1, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m13",
				vtkMatrix4x4.GetElement(1, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m20",
				vtkMatrix4x4.GetElement(2, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m21",
				vtkMatrix4x4.GetElement(2, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m22",
				vtkMatrix4x4.GetElement(2, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m23",
				vtkMatrix4x4.GetElement(2, 3));

		XMLUtil.setAttributeDoubleValue(transfoElement, "m30", vtkMatrix4x4.GetElement(3, 0));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m31", vtkMatrix4x4.GetElement(3, 1));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m32", vtkMatrix4x4.GetElement(3, 2));
		XMLUtil.setAttributeDoubleValue(transfoElement, "m33", vtkMatrix4x4.GetElement(3, 3));
		XMLUtil.setAttributeValue(transfoElement, "process_date",
				new Date().toString());
		XMLUtil.saveDocument(document, XMLFile);
		System.out.println("Transformation matrix as been saved as "+XMLFile.getPath());
		System.out.println("If there is no path indicated, it means it is in your ICY installation path");
	}*/
}
