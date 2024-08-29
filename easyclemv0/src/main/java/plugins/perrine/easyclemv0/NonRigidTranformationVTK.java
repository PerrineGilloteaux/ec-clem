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

import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;


import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import icy.type.DataType;
import icy.util.XMLUtil;
import vtk.vtkDataArray;
import vtk.vtkDataSet;
import vtk.vtkDoubleArray;
import vtk.vtkFloatArray;
import vtk.vtkImageContinuousDilate3D;
import vtk.vtkImageData;

import vtk.vtkImageGridSource;
import vtk.vtkImageReslice;
import vtk.vtkIntArray;
import vtk.vtkPointData;
import vtk.vtkPoints;
import vtk.vtkPolyData;
import vtk.vtkShortArray;
import vtk.vtkThinPlateSplineTransform;
import vtk.vtkTransformPolyDataFilter;
import vtk.vtkUnsignedCharArray;
import vtk.vtkUnsignedIntArray;
import vtk.vtkUnsignedShortArray;
import vtk.vtkVertexGlyphFilter;

/**
 * 
 * @author paul-gilloteaux-p
 * Transform non rigidly from 2 sets of points, and update the position of ROIs
 */
public class NonRigidTranformationVTK implements Runnable {
	
	private  double InputSpacingx ;
	private  double InputSpacingy ;
	private  double InputSpacingz ;
	private Sequence imagesource;
	private Sequence imagetarget;
	private vtkDataSet[] imageData;
	private vtkImageReslice ImageReslice;
	double[][] sourcepoints;
	double[][] targetpoints;
	//private DataType oriType;
	private int extentx;
	private int extenty;
	private int extentz;
	private double spacingx;
	private double spacingy;
	private double spacingz;
	private Runnable transformer;
	 private boolean checkgrid;
	public void run() {
		
	
	
	
	vtkPoints lmsource=createvtkpoints(sourcepoints,this.InputSpacingx,this.InputSpacingy,this.InputSpacingz); // TODO check if sourcepoint i easyclemv0 is in um or pixels, for now assume in um
	vtkPoints lmtarget=createvtkpoints(targetpoints,this.spacingx,this.spacingy,this.spacingz);
	SaveNonRigidTransform(lmsource,lmtarget);
    final vtkThinPlateSplineTransform myvtkTransform= new vtkThinPlateSplineTransform();
    myvtkTransform.SetSourceLandmarks(lmsource);
    myvtkTransform.SetTargetLandmarks(lmtarget);
    if (extentz<=1){
    myvtkTransform.SetBasisToR2LogR();
    }
    else{
    	myvtkTransform.SetBasisToR();	
    }
    final int nbc = imagesource.getSizeC();
	imageData=new vtkDataSet[nbc];
	
	final DataType datatype = imagesource.getDataType_();
	transformer = new Runnable() {
       

		@Override
        public void run()
        {
            
        	ProgressFrame progress = new ProgressFrame("Applying the NON RIGID transformation...");	
        	progress.setLength(10);
        	System.out.println("Starting to non rigidly register " + imagesource.getFilename()+ " on " +imagetarget.getFilename());
        	int nbt = imagesource.getSizeT();
        	int nbz = extentz+1;

        	int w = extentx+1;
        	int h = extenty+1;
        	vtkImageGridSource sourcegrid =new  vtkImageGridSource();
        	if (checkgrid){
        		progress.setPosition(1);
        	sourcegrid.SetDataExtent(0, extentx, 0,  extenty, 0,  extentz);
        	sourcegrid.SetLineValue(255);
        	sourcegrid.SetFillValue(0.0);
        	sourcegrid.SetDataScalarType(icy.vtk.VtkUtil.VTK_UNSIGNED_CHAR);
        	sourcegrid.SetDataSpacing(InputSpacingx,InputSpacingy,InputSpacingz);
        	sourcegrid.SetGridSpacing(Math.round(extentx/10),Math.round( extenty/10), Math.round(extentz));
        	sourcegrid.Update();
        	vtkImageData imagedatagrid=new vtkImageData();
        	progress.setPosition(1.5);
        	if (extentz<=1){
        	vtkImageContinuousDilate3D dilate=new vtkImageContinuousDilate3D();
        	dilate.SetInputData(sourcegrid.GetOutput());
        	
        	dilate.SetKernelSize(extentx/400,extenty/400, 1);
        	
        	dilate.Update();
        	
        	imagedatagrid = dilate.GetOutput();
        	
        	}
        	else{
        	imagedatagrid = sourcegrid.GetOutput();
        	}
        	progress.setPosition(3);
        	 vtkImageReslice ImageReslicegrid = new vtkImageReslice();

        	 ImageReslicegrid.SetInputData(imagedatagrid);
     		
        	 ImageReslicegrid.SetOutputDimensionality(3);
        	 ImageReslicegrid.SetOutputOrigin(0, 0, 0);
        	 ImageReslicegrid.SetOutputSpacing(spacingx, spacingy, spacingz);
        	 ImageReslicegrid.SetOutputExtent(0, extentx, 0, extenty, 0, extentz); // to be checked: transform is applied twice?
        	 ImageReslicegrid.SetResliceTransform(myvtkTransform.GetInverse());
     		
        	 ImageReslicegrid.SetInterpolationModeToLinear();

        	 ImageReslicegrid.Update();
        	 
        	 progress.setPosition(4);
        	 imagedatagrid = ImageReslicegrid.GetOutput();
        	
       	 progress.setPosition(5);
        	 final Sequence grid=new Sequence();
        	 
        	 grid.beginUpdate();
        		grid.removeAllImages();
        		try{
        			
        				for (int z = 0; z < nbz; z++) {
        					IcyBufferedImage image = new IcyBufferedImage(w, h,1,
        							DataType.UBYTE);
        					progress.setPosition(5+z*2.5/extentz);
        						vtkDataArray myvtkarray = imagedatagrid.GetPointData().GetScalars();
        						final byte[] inData=((vtkUnsignedCharArray) myvtkarray).GetJavaArray();
        						
        						byte[] outData=new byte[w*h];
        						for (int i = 0; i < h; i++) {
        							for (int j = 0; j < w; j++) {

        								outData[i * w + j] =  inData[z * w * h + i * w + j];

        							}
        						}


        						image.setDataXYAsByte(0, outData);
        						

        					
        					grid.setImage(0, z, image);

        				}

        			}
        		
        	finally{
        		grid.endUpdate();
        		
        		
        	}
        		
        		grid.setName("Deformed source grid");
        		
				
        		grid.setPixelSizeX(spacingx);
        		grid.setPixelSizeY(spacingy);
        		grid.setPixelSizeZ(spacingz);
        		ThreadUtil.invokeLater(new Runnable() {
        			public void run() {
        				new Viewer(grid);
        				
        			}});
        			}
        	
        	 progress.setPosition(5);
	for (int c=0;c<imagesource.getSizeC();c++){

		converttoVtkImageData(c);

		
	    
		
		ImageReslice = new vtkImageReslice();
		
		ImageReslice.SetInputData(imageData[c]);
		
		ImageReslice.SetOutputDimensionality(3);
		ImageReslice.SetOutputOrigin(0, 0, 0);
		ImageReslice.SetOutputSpacing(spacingx, spacingy, spacingz);
		ImageReslice.SetOutputExtent(0, extentx, 0, extenty, 0, extentz); // to be checked: transform is applied twice?
		ImageReslice.SetResliceTransform(myvtkTransform.GetInverse());
		
		ImageReslice.SetInterpolationModeToLinear();

		ImageReslice.Update();
	

		imageData[c] = ImageReslice.GetOutput();
	}

	
	

	
	imagesource.beginUpdate();
	imagesource.removeAllImages();
	
	try {// here finally we convert all 3D images to unsigned 8 bits
		// final ArrayList<IcyBufferedImage> images =
		// sequence.getAllImage();
		
		switch (datatype) {
		case UBYTE:
		for (int t = 0; t < nbt; t++) {
			for (int z = 0; z < nbz; z++) {
				IcyBufferedImage image = new IcyBufferedImage(w, h, nbc,
						datatype);
				progress.setPosition(7.5+z*2.5/extentz);
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
				imagesource.setImage(t, z, image);

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
				imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

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
					imagesource.setImage(t, z, image);

				}

			}
			break;
		default:
			System.err.println("unknown data format");
			break;
		}
	
	imagesource.setPixelSizeX(spacingx);
	imagesource.setPixelSizeY(spacingy);
	imagesource.setPixelSizeZ(spacingz);
	//
} finally {

	imagesource.endUpdate();
	
	progress.setPosition(10);
	progress.close();
	new AnnounceFrame("Non Rigid Transformation Updated",5);
	// sequence.
}
        }
        };
    ThreadUtil.bgRunSingle(transformer);
	
	// apply the transform to points as well
    vtkTransformPolyDataFilter tr=new  vtkTransformPolyDataFilter();

    vtkPolyData mypoints = new vtkPolyData();
	  
	mypoints.SetPoints(lmsource); 
	vtkVertexGlyphFilter vertexfilter=new vtkVertexGlyphFilter(); 
	vertexfilter.SetInputData(mypoints);
	vtkPolyData sourcepolydata=new vtkPolyData();
	  
	vertexfilter.Update();
	sourcepolydata.ShallowCopy(vertexfilter.GetOutput());
	tr.SetInputData(sourcepolydata);
	  
	tr.SetTransform(myvtkTransform);
	tr.Update(); 
	
	vtkPolyData modifiedpoints = tr.GetOutput();
	for (int p=0;p< modifiedpoints.GetNumberOfPoints();p++){
		double[] newpos=modifiedpoints.GetPoint(p);
		sourcepoints[p][0]=newpos[0]/this.spacingx;
		sourcepoints[p][1]=newpos[1]/this.spacingy;
		sourcepoints[p][2]=newpos[2]/this.spacingz;
	}

System.out.println("have been applied");
return;  
}
	/*protected void addSequence(Sequence grid) {
		// TODO Auto-generated method stub
		addSequence(grid);
	}*/
	private void SaveNonRigidTransform(vtkPoints lmsource, vtkPoints lmtarget) {
		
				String name = imagesource.getFilename() + "_NONRIGIDtransfo.xml";
				File XMLFile = new File(name);
				Document document = XMLUtil.createDocument(true);
				Element transfoElement = XMLUtil.addElement(
						document.getDocumentElement(), "transfoelements");
				XMLUtil.setAttributeIntValue(transfoElement, "extentx", this.extentx);
				XMLUtil.setAttributeIntValue(transfoElement, "extenty", this.extenty);
				XMLUtil.setAttributeIntValue(transfoElement, "extentz", this.extentz);
				
					XMLUtil.setAttributeDoubleValue(transfoElement, "sx" , this.spacingx );
					XMLUtil.setAttributeDoubleValue(transfoElement, "sy" , this.spacingy );
					XMLUtil.setAttributeDoubleValue(transfoElement, "sz" , this.spacingz  );
					XMLUtil.setAttributeDoubleValue(transfoElement, "ix" , this.InputSpacingx );
					XMLUtil.setAttributeDoubleValue(transfoElement, "iy" , this.InputSpacingy );
					XMLUtil.setAttributeDoubleValue(transfoElement, "iz" , this.InputSpacingz  );
					XMLUtil.setAttributeIntValue(transfoElement, "Npoints" ,(int) lmsource.GetNumberOfPoints()  );
					XMLUtil.setAttributeValue(transfoElement, "process_date",
							new Date().toString());
				for (int idx=0;idx<lmsource.GetNumberOfPoints();idx++){
				Element PointPairElement = XMLUtil.addElement(
						document.getDocumentElement(), "pointspairsinphysicalcoordinates");
				XMLUtil.setAttributeIntValue(PointPairElement, "pairnumber", idx);
				XMLUtil.setAttributeDoubleValue(PointPairElement, "xsource",
						lmsource.GetPoint(idx)[0]);
				XMLUtil.setAttributeDoubleValue(PointPairElement, "ysource",
						lmsource.GetPoint(idx)[1]);
				XMLUtil.setAttributeDoubleValue(PointPairElement, "zsource",
						lmsource.GetPoint(idx)[2]);
				XMLUtil.setAttributeDoubleValue(PointPairElement, "xtarget",
						lmtarget.GetPoint(idx)[0]);

				XMLUtil.setAttributeDoubleValue(PointPairElement, "ytarget",
						lmtarget.GetPoint(idx)[1]);
				XMLUtil.setAttributeDoubleValue(PointPairElement, "ztarget",
						lmtarget.GetPoint(idx)[2]);
				}
				
				
				XMLUtil.saveDocument(document, XMLFile);
				System.out.println("Elements to reapply non rigid transfo as been saved as "+XMLFile.getPath());
				System.out.println("If there is no path indicated, it means it is in your ICY installation path");
	}
	public void setImageSourceandpoints(boolean checkgrid, Sequence imagesourceseq, double[][] sourcepoints) {
		this.checkgrid=checkgrid;
		
		this.imagesource=imagesourceseq;
		
		//this.oriType = imagesourceseq.getDataType_();
		this.InputSpacingx=this.imagesource.getPixelSizeX(); // by default
		this.InputSpacingy=this.imagesource.getPixelSizeY(); // by default
		this.InputSpacingz=this.imagesource.getPixelSizeZ(); 
		
		this.sourcepoints=sourcepoints; //in pixels
		
	}
public void setImageTargetandpoints(Sequence imageseq, double[][] sourcepoints) {
		
		
		this.imagetarget=imageseq;
		int w=imageseq.getSizeX();
		int h=imageseq.getSizeY();
		int z=imageseq.getSizeZ();
		this.extentx=w-1;
		this.extenty=h-1;
		this.extentz=z-1;
		this.spacingx=imageseq.getPixelSizeX();
		this.spacingy=imageseq.getPixelSizeY();
		this.spacingz=imageseq.getPixelSizeZ();
		
		this.targetpoints=sourcepoints;//in pixels
		
	}

	private vtkPoints createvtkpoints(double[][] points,double sizex,double sizey,double sizez) {
		// points in pixels
		vtkPoints mypoints=new vtkPoints();
		
		mypoints.SetNumberOfPoints((int)points.length);
		 for (int i=0;i<points.length;i++){
			 mypoints.SetPoint(i,points[i][0]*sizex, points[i][1]*sizey, points[i][2]*sizez);
		 }

		return mypoints;

	}
	void converttoVtkImageData(int posC) {
		final Sequence sequence2 = this.imagesource;
		if (this.imagesource == null)
			return;

		final int sizeX = sequence2.getSizeX();
		final int sizeY = sequence2.getSizeY();
		final int sizeZ = sequence2.getSizeZ();
		final DataType dataType = sequence2.getDataType_();
		final int posT = 0;
		

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
				((vtkUnsignedCharArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsByte(posT));
			else
				((vtkUnsignedCharArray) array).SetJavaArray(imagesource
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
				((vtkUnsignedCharArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsByte(posT));
			else
				((vtkUnsignedCharArray) array).SetJavaArray(imagesource
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
				((vtkUnsignedShortArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsShort(posT));
			else
				((vtkUnsignedShortArray) array).SetJavaArray(imagesource
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
				((vtkShortArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsShort(posT));
			else
				((vtkShortArray) array).SetJavaArray(imagesource
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
				((vtkUnsignedIntArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsInt(posT));
			else
				((vtkUnsignedIntArray) array).SetJavaArray(imagesource
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
				((vtkIntArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsInt(posT));
			else
				((vtkIntArray) array).SetJavaArray(imagesource
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
				((vtkFloatArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsFloat(posT));
			else
				((vtkFloatArray) array).SetJavaArray(imagesource
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
				((vtkDoubleArray) array).SetJavaArray(imagesource
						.getDataCopyCXYZAsDouble(posT));
			else
				((vtkDoubleArray) array).SetJavaArray(imagesource
						.getDataCopyXYZAsDouble(posT, posC));
			break;

		default:
			// we probably have an empty sequence
			newImageData.SetDimensions(1, 1, 1);
			newImageData.SetSpacing(imagesource.getPixelSizeX(), imagesource.getPixelSizeY(), imagesource.getPixelSizeZ());
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
	

}
	


	
