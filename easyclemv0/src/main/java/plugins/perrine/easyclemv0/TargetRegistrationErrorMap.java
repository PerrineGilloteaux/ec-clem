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


import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.ProgressFrame;
//import icy.gui.lut.LUTViewer;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.colormap.FireColorMap;

import icy.image.lut.LUT;

import icy.roi.ROI;

import icy.sequence.Sequence;
import icy.system.thread.ThreadUtil;
import icy.type.point.Point5D;
//import ij.plugin.filter.LutViewer;

import java.util.ArrayList;



//import plugins.kernel.roi.roi2d.ROI2DEllipse;
import Jama.Matrix;
import plugins.kernel.roi.descriptor.measure.ROIMassCenterDescriptorsPlugin;
//import icy.main.Icy;
//import icy.math.Scaler;
import plugins.stef.tools.overlay.ColorBarOverlay;
/** author: Perrine.paul-gilloteaux@curie.fr
* purpose: Compute TRE in each image points from FLE fiducial localisation error
* 
*/  
public class TargetRegistrationErrorMap implements Runnable {

	private double[][] fiducials = null;
	double FLE;// in nm //sending the FRE as an approximation for FLE
	private Matrix eigenVector;
	private double[] bary;
	private ProgressFrame myprogressbar;
	private double c1; // precomputed to speed up
	private double c2;
	private double c3;
	private double principalAxesV1;
	private double principalAxesV2;
	private double principalAxesV3;
	private double principalAxesU1;
	private double principalAxesU2;
	private double principalAxesU3;
	private double principalAxesW1;
	private double principalAxesW2;
	private double principalAxesW3;
	double[][] eigenVectorMatrix;
	private double numc1;
	private double numc2;
	private double numc3;
	private Sequence sequence;
	private IcyBufferedImage image;
	Viewer myviewer;
/**
 * will be run in plugin mode:
 *  set an progress bar,
 *  compute the vector f of distance
 *  and then compute the TRE in each point of the image
 *  recalibrate the image in pixel size to have it coherent with the target image
 *  display a lut where error <100 nm emphasized in green
 */
	@Override
	public void run() {
		//added to avoid a bug report when no ROI or not enough ROI.
		if (this.sequence.getROIs().size()<3){
			MessageDialog.showDialog("You need at least 4 points to compute an error map ");
		}
		ReadFiducials(this.sequence);
		//fiducial is read in nm
		// DisplayFiducials(sequence);

		myprogressbar = new ProgressFrame("EasyCLEM is computing Error Map");
		myprogressbar.setLength(sequence.getSizeX() * sequence.getSizeZ());
		myprogressbar.setPosition(0);
		myprogressbar.setPosition(10);
		myprogressbar
				.setMessage("EasyCLEM was Precomputing Inertia Matrix done");

		double[] f = PreComputeTRE();
		
		final Sequence TREMAP = ComputeTREMAP(sequence, FLE, f, image);

		// addSequence(TREMAP);
		// TREMAP.getFirstViewer().getLutViewer().setAutoBound(false);
		myprogressbar.close();

		/*
		 * final Sequence TREMAP=ComputeTREMAP(sequence,FLE,f,this.image); // to
		 * be used in graphical thread final double
		 * sizex=sequence.getPixelSizeX(); final double
		 * sizey=sequence.getPixelSizeY(); final double
		 * sizez=sequence.getPixelSizeZ();
		 */
		double sizex = sequence.getPixelSizeX();
		double sizey = sequence.getPixelSizeY();
		double sizez = sequence.getPixelSizeZ();

		if (TREMAP == null) {
			MessageDialog.showDialog("No active image");

			return;
		}
		
		TREMAP.setPixelSizeX(sizex);
		TREMAP.setPixelSizeY(sizey);
		TREMAP.setPixelSizeZ(sizez);
		TREMAP.setAutoUpdateChannelBounds(false);
		TREMAP.endUpdate();
		TREMAP.setName("Prediction of registration only error in nanometers (if calibration settings were correct)");
		
		
		ColorBarOverlay mycolorbar=new ColorBarOverlay(null);
		mycolorbar.setDisplayMinMax(true);
		TREMAP.addOverlay(mycolorbar);
		
			
			
		ThreadUtil.invokeLater(new Runnable() {
			public void run() {
				
			
		   Viewer myviewer = new Viewer(TREMAP);
			
			LUT mylut = myviewer.getLut();

			mylut.getLutChannel(0).setColorMap(new FireColorMap(), false);
		
			System.out.println("done");
			}});
		
		// TREMAP.getFirstViewer().getLutViewer().setAutoBound(false);
		// double[] bounds=TREMAP.getChannelBounds(0);
		// bounds[0]=100.0;

		/*
		 * PPutilities thresholded=new PPutilities(); double[]
		 * listofthreshold={0.0,100.0,200.0}; Viewer myviewer2 =new
		 * Viewer(thresholded.ppThreshold(TREMAP,listofthreshold)); LUT mylut2 =
		 * myviewer2.getLut();
		 * 
		 * mylut2.getLutChannel(0).setColorMap(new HSVColorMap(), false);
		 */
		// we changed messagedialog to confirm dialog becvause messagedialog was
		// not blocking.
		/*ConfirmDialog
				.confirm(
						"Accuracy",
						"Color Map will now be changed (but not error values): Green means under 100nm accuracy, blue is above 1000nm \n (If calibration settings were correct)\n You can still change it using the LUT properties of the icy sequence (Right Panel)",
						JOptionPane.OK_OPTION);

		myviewer.getLutViewer().setAutoBound(false);
		mylut.getLutChannel(0).setColorMap(new GlowColorMap(true), false);
		mylut.getLutChannel(0).setMin(100);
		mylut.getLutChannel(0).setMax(1000);*/

	}
/**
 * 
 * @param sequence
 * @param image
 * @param FLE either the resolution in fluorescenec in nm, either the mas FRE distance in nm
 */
	public void apply(Sequence sequence, IcyBufferedImage image, double FLE) {
		// TODO Auto-generated by Icy4Eclipse
		// Parameters
		// Real ones
		// INITIALISATION HARD CODED for now

		// TO DO facile: passer en nm pour les coordonnees pour considere
		// anisotropie en z

		// for testing purposes
		// int nz=10;
		// int Npoints=30;
		// fiducials=new double[Npoints][3];
		// Sequence sequence=new Sequence();
		// IcyBufferedImage image=new
		// IcyBufferedImage(300,500,1,DataType.DOUBLE);
		// for (int i=0;i<nz;i++)
		// sequence.setImage(0,i,image);
		// addSequence(sequence);
		//
		this.FLE = FLE;
		this.sequence = sequence;
		this.image = image;
		new Thread(this).start();
	}
/**
 * 
 * @param sequence
 * @param fLE2
 * @param f
 * @param image
 * @return
 */
	private Sequence ComputeTREMAP(Sequence sequence, double fLE2, double[] f,
			IcyBufferedImage image) {
		// This function is the only one from TRE computation ICY-dependant
		Sequence newsequence = new Sequence();

		if (image == null) {

			return null;
		}

		myprogressbar.setMessage("EasyCLEM is computing Error Map");

		for (int z = 0; z < sequence.getSizeZ(); z++) {
			float[] dataArray = new float[image.getSizeX() * image.getSizeY()];

			for (int x = 0; x < image.getSizeX(); x++) {
				myprogressbar.setPosition(myprogressbar.getPosition() + 1);
				for (int y = 0; y < image.getSizeY(); y++) {

					dataArray[image.getOffset(x, y)] = ComputeTRE(fLE2, x, y,
							z, f);
				}
			}
			myprogressbar.setPosition(myprogressbar.getPosition() + z);

			// to be changed to sequence then
			IcyBufferedImage imageResult = new IcyBufferedImage(
					sequence.getSizeX(), sequence.getSizeY(), dataArray);
			newsequence.setImage(0, z, imageResult);
			imageResult.getType();
		}
		return newsequence;
	}
/**
 * 
 * @param fLE2
 * @param x in pixel
 * @param y in pixel
 * @param z in pixel
 * @param f
 * @return TRE in nm
 */
	float ComputeTRE(double fLE2, int x, int y, int z, double[] f) {
		// Return the TRE RMS in point x given in pixel
		// FLE =measured position vs true position tab of fiducial localisation
		// error, RMS of it
		// N number of fiducial points
		// fiducialsarray list of poisition to compute dk-> distance of point x
		// from
		// principal axis k of fiducials; and fk mean of square distances of the
		// fiducials from that axis.
		// ICI !!!!
		// + penser � recopier et verifier le type pour les points x (cf array
		// http://icy.bioimageanalysis.org/index.php?display=javaCourse Final
		// MY points are in pixels!!
		// TRE=((1/N)+(1/3)*sum(d./f))*FLEsqrexp;
		double[] mypoint = { x*this.sequence.getPixelSizeX()*1000, y*this.sequence.getPixelSizeY()*1000, z*this.sequence.getPixelSizeZ()*1000};
		double[] d = new double[3];

		for (int i = 0; i < fiducials.length; i++) {
			// myprogressbar.setPosition(10+10*(float)i/fiducials.length);
			// for (int j=0;j<3;j++){ // for 3D
			// d[j]+=DistanceSQUAREPointToLine3Dfast(mypoint,eigenVectorMatrix[0][j],eigenVectorMatrix[1][j],eigenVectorMatrix[2][j],bary);
			d[0] += DistanceSQUAREPointToLine3Dfast(mypoint, principalAxesU1,
					principalAxesV1, principalAxesW1, c1, numc1, bary);
			d[1] += DistanceSQUAREPointToLine3Dfast(mypoint, principalAxesU2,
					principalAxesV2, principalAxesW2, c2, numc2, bary);
			if (numc3 != 0)
				d[2] += DistanceSQUAREPointToLine3Dfast(mypoint,
						principalAxesU3, principalAxesV3, principalAxesW3, c3,
						numc3, bary);
			else
				// }
				d[2] += Math.pow(DistancePointtoPoint(fiducials[i], bary), 2); // was
																				// for
																				// 2D
		}

		double doverf = 0;
		double sumdoverf = 0;
		for (int i = 0; i < 3; i++) {
			doverf = d[i] / f[i];
			sumdoverf += doverf;
		}
		//one square root was forgotten here: f been actually f2 dist squared
		//TRE2=FLE2*(1/N+1/3(sum(d2/f2))--> TRE= FLE*sqrt(1/N+1/3(sum(d2/f2))
		double TRE = Math.sqrt((1d / (double)fiducials.length) *(1d+(1 / 3d) * sumdoverf) * Math.pow(fLE2,2));
		return (float) TRE;
	}
/**
 * compute the barycenter, the vector f
 * @return
 */
	double[] PreComputeTRE() {
		// compute Baryventre

		bary = averageposition(fiducials);
		//bary is in nm 

		// compute inerty moments and products
		// the inertia matrix in nm
		double Ixx = moment(1, 2);

		double Iyy = moment(0, 2);
		double Izz = moment(0, 1);

		double Ixy = product(0, 1);
		double Ixz = product(0, 2);
		double Iyz = product(1, 2);
		// InertiaMatrix=[Ixx -Ixy -Ixz; -Ixy Iyy -Iyz; -Ixz -Iyz Izz];
		double[][] matrixd = new double[3][3];
		// To be changed to normal matrix syntax double[] f={0d,0d,0d};
		matrixd[0][0] = Ixx;
		matrixd[0][1] = -Ixy;
		matrixd[0][2] = -Ixz;
		matrixd[1][0] = -Ixy;
		matrixd[1][1] = Iyy;
		matrixd[1][2] = -Iyz;
		matrixd[2][0] = -Ixz;
		matrixd[2][1] = -Iyz;
		matrixd[2][2] = Izz;
		Matrix inertiaMatrix = new Matrix(matrixd);
		// compute principal axes = eigen vectors of principal axes
		eigenVector = inertiaMatrix.eig().getV();
		// compute Fi distance to principal axes
		eigenVectorMatrix = eigenVector.getArray();
		double[] f = new double[3];
		principalAxesU1 = eigenVectorMatrix[0][0];
		principalAxesU2 = eigenVectorMatrix[0][1];
		principalAxesU3 = eigenVectorMatrix[0][2];
		principalAxesV1 = eigenVectorMatrix[1][0];
		principalAxesV2 = eigenVectorMatrix[1][1];
		principalAxesV3 = eigenVectorMatrix[1][2];
		principalAxesW1 = eigenVectorMatrix[1][0];
		principalAxesW2 = eigenVectorMatrix[1][1];
		principalAxesW3 = eigenVectorMatrix[1][2];
		c1 = -(principalAxesV1 * bary[0] - principalAxesU1 * bary[1] + principalAxesW1
				* bary[2]);
		c2 = -(principalAxesV2 * bary[0] - principalAxesU2 * bary[1] + principalAxesW2
				* bary[2]);
		c3 = -(principalAxesV3 * bary[0] - principalAxesU3 * bary[1] + principalAxesW3
				* bary[2]);
		numc1 = Math.pow(principalAxesV1, 2) + Math.pow(principalAxesU1, 2)
				+ Math.pow(principalAxesW1, 2);
		numc2 = Math.pow(principalAxesV2, 2) + Math.pow(principalAxesU2, 2)
				+ Math.pow(principalAxesW2, 2);
		numc3 = Math.pow(principalAxesV3, 2) + Math.pow(principalAxesU3, 2)
				+ Math.pow(principalAxesW3, 2);
		for (int i = 0; i < fiducials.length; i++) {

			f[0] += DistanceSQUAREPointToLine3Dfast(fiducials[i],
					principalAxesU1, principalAxesV1, principalAxesW1, c1,
					numc1, bary);
			f[1] += DistanceSQUAREPointToLine3Dfast(fiducials[i],
					principalAxesU2, principalAxesV2, principalAxesW2, c2,
					numc2, bary);
			if (numc3 != 0)
				f[2] += DistanceSQUAREPointToLine3Dfast(fiducials[i],
						principalAxesU3, principalAxesV3, principalAxesW3, c3,
						numc3, bary);
			else
				f[2] += Math.pow(DistancePointtoPoint(fiducials[i], bary), 2);
		}
		// IN 2D distance to axe Z is distance to bary center

		return f;
	}

	private double DistancePointtoPoint(double[] ds, double[] bary) {

		return Math.sqrt(Math.pow(ds[0] - bary[0], 2)
				+ Math.pow(ds[1] - bary[1], 2));
	}

	/*
	 * private double DistancePointToLine(double[] fiduciaires, double
	 * principalAxesU, double principalAxesV, double principalAxesW, double[]
	 * bary) { // FOR NOW ONLY 2D, so W is not used...Compute the distance from
	 * point A to line defined by the vector //v. the distance is defined as the
	 * distance between point A and the //intersection betwen v-line and its
	 * perpandicular going trhough A. // equation of the line from is vector v
	 * (vx,vy): ax+by+c=0 //b=-vx; a=vy; c=-(a*baryx+b*baryy); //b=-v(1);
	 * //a=v(2); //c=-(a*bary(1)+b*bary(2));
	 * //distance=abs(a*A(1)+b*A(2)+c)/sqrt(a^2+b^2); double
	 * c=-(principalAxesV*bary[0]-principalAxesU*bary[1]); double
	 * distance=Math.abs
	 * (principalAxesV*fiduciaires[0]-principalAxesU*fiduciaires
	 * [1]+c)/Math.sqrt(Math.pow(principalAxesV, 2)+Math.pow(principalAxesU,
	 * 2)); return distance; }
	 */

	private double DistanceSQUAREPointToLine3Dfast(double[] fiduciaires,
			double principalAxesU, double principalAxesV,
			double principalAxesW, double c, double numc, double[] bary) {
		// Compute the distance from point A in pixels
		// to line defined by the vector
		// v. the distance is defined as the distance between point A and the
		// intersection betwen v-line and its perpandicular going trhough A.
		// equation of the line from is vector v (vx,vy): ax+by+c=0
		// b=-vx; a=vy; c=-(a*baryx+b*baryy);
		// b=-v(1);
		// a=v(2);
		// c=-(a*bary(1)+b*bary(2));
		// distance=abs(a*A(1)+b*A(2)+c)/sqrt(a^2+b^2);
		// double a=principalAxesV;
		// double b=-principalAxesU;
		// double bz=principalAxesW;

		// double
		// distance=Math.pow(principalAxesV*fiduciaires[0]-principalAxesU*fiduciaires[1]+principalAxesW*fiduciaires[2]+c,2)/(Math.pow(principalAxesV,
		// 2)+Math.pow(principalAxesU, 2)+Math.pow(principalAxesW,2));
		double distance = Math.pow(principalAxesV * fiduciaires[0]
				- principalAxesU * fiduciaires[1] + principalAxesW
				* fiduciaires[2] + c, 2)
				/ numc;
		return distance;
	}

	/*
	 * private double DistanceSQUAREPointToLine3D(double[] fiduciaires, double
	 * principalAxesU, double principalAxesV, double principalAxesW, double[]
	 * bary) { // FOR NOW ONLY 2D, so W is not used...Compute the distance from
	 * point A to line defined by the vector //v. the distance is defined as the
	 * distance between point A and the //intersection betwen v-line and its
	 * perpandicular going trhough A. // equation of the line from is vector v
	 * (vx,vy): ax+by+c=0 //b=-vx; a=vy; c=-(a*baryx+b*baryy); //b=-v(1);
	 * //a=v(2); //c=-(a*bary(1)+b*bary(2));
	 * //distance=abs(a*A(1)+b*A(2)+c)/sqrt(a^2+b^2); //double a=principalAxesV;
	 * //double b=-principalAxesU; //double bz=principalAxesW; double
	 * c=-(principalAxesV
	 * *bary[0]-principalAxesU*bary[1]+principalAxesW*bary[2]); double
	 * distance=Math
	 * .pow(principalAxesV*fiduciaires[0]-principalAxesU*fiduciaires
	 * [1]+principalAxesW*fiduciaires[2]+c,2)/(Math.pow(principalAxesV,
	 * 2)+Math.pow(principalAxesU, 2)+Math.pow(principalAxesW,2)); return
	 * distance; }
	 */
	// Inertia product
	private double product(int c1, int c2) {
		// sum(fiducialarray(:,1).*fiducialarray(:,2));
		double P = 0;
		for (int i = 0; i < fiducials.length; i++)
			P += fiducials[i][c1] * fiducials[i][c2];

		return P;
	}

	// Inertia Moment
	private double moment(int c1, int c2) {
		// sum(fiducialarray(:,2).^2+fiducialarray(:,3).^2)
		double I = 0;
		for (int i = 0; i < fiducials.length; i++)
			I += Math.pow(fiducials[i][c1], 2) + Math.pow(fiducials[i][c2], 2);
		return I;
	}

	private double[] averageposition(double[][] fiducials2) {
		double[] average = new double[3];
		average[0] = 0;
		average[1] = 0;
		for (int i = 0; i < fiducials2.length; i++) {
			average[0] += fiducials2[i][0];
			average[1] += fiducials2[i][1];
			average[2] += fiducials2[i][2];
		}
		average[0] /= fiducials2.length;
		average[1] /= fiducials2.length;
		average[2] /= fiducials2.length;
		return average;
	}

	/*
	 * private void DisplayFiducials(Sequence sequence) { ROI2DEllipse[]
	 * fiducialsarray=new ROI2DEllipse[fiducials.length]; for (int
	 * i=0;i<fiducials.length;i++) {
	 * 
	 * fiducialsarray[i]=new
	 * ROI2DEllipse(fiducials[i][0]-1,fiducials[i][1]-1,fiducials
	 * [i][0]+1,fiducials[i][1]+1);
	 * fiducialsarray[i].setZ((int)(fiducials[i][2]));
	 * sequence.addROI(fiducialsarray[i]); }
	 * 
	 * 
	 * }
	 */
	
/**
 * 	
 * @param points in nanometers
 * @param seq
 */
void ReadFiducials(double[][] points, Sequence seq)	{
	fiducials = new double[points.length][3];
	for(int i=0;i<points.length;i++){
		for (int j=0;j<3;j++)
			fiducials[i][j]=points[i][j];
	}
	this.sequence=seq;
}
/**
 * read fiducial and express them in nm to be in the same unit and deal with anisotropy
 * @param seq
 */
	void ReadFiducials(Sequence seq) {
		this.sequence=seq;
		ArrayList<ROI> listfiducials = seq.getROIs();
		
		fiducials = new double[listfiducials.size()][3];
		// fiducials=new double[10][3];
		int i = -1;
		for (ROI roi : listfiducials) {

			// if (roi instanceof ROI3DArea) //ROID2D marche pas
			// {
			// i++;
			// Point5D p3D=new ROIUtil().getMassCenter(roi);
			// //if nan (mass center point ne pas rajouter
			// fiducials[i][0]=p3D.getX();
			// fiducials[i][1]=p3D.getY();
			// // Attendre le feu vert Stephane dans prochaine version ICY >
			// 1.5.0
			// fiducials[i][2]=p3D.getZ()-roi.getPosition5D().getZ();
			// }
			// else//if (roi instanceof ROI3DArea) ROID2D marche pas
			// {
			i++;

			Point5D p3D = ROIMassCenterDescriptorsPlugin.computeMassCenter(roi);
			if (roi.getClassName() == "plugins.kernel.roi.roi3d.ROI3DPoint")
				p3D = roi.getPosition5D();
			if (Double.isNaN(p3D.getX()))
				p3D = roi.getPosition5D(); // some Roi does not have gravity
											// center such as points
			fiducials[i][0] = p3D.getX()*seq.getPixelSizeX()*1000;// in nm
			fiducials[i][1] = p3D.getY()*seq.getPixelSizeY()*1000;

			fiducials[i][2] = p3D.getZ()*seq.getPixelSizeZ()*1000;
			// }

		}
	}
	
	/**
	 * create an accessor to principal axes 
	 * 
	 */
	double[] getPrincipalAxe1(){
		double[] pu=new double[3];
		pu[0]=principalAxesU1;
		pu[1]=principalAxesV1;
		pu[2]=principalAxesW1;
		return pu;
		
	}
	double[] getPrincipalAxe2(){
		double[] pu=new double[3];
		pu[0]=principalAxesU2;
		pu[1]=principalAxesV2;
		pu[2]=principalAxesW2;
		return pu;
		
	}
	double[] getPrincipalAxe3(){
		double[] pu=new double[3];
		pu[0]=principalAxesU3;
		pu[1]=principalAxesV3;
		pu[2]=principalAxesW3;
		return pu;
		
	}

}
