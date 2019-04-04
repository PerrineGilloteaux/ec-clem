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
 * Author Perrine.Paul-Gilloteaux@curie.fr Method SVD extended to 3D
 * Roger Penroise 1956 (showing that solving a linear system using pseudo inverse was equivalent in solving the equivalent least square problem.
 * From http://www.comp.nus.edu.sg/~cs6240/lecture/rigid.pdf (read on the 06/08/2014)
 * 2014
 * TODO: Check the anisotropy and validate the transform
 */

import icy.util.Random;

import java.util.Enumeration;
import java.util.Vector;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

public class SimilarityRegistrationAnalytic3D {
	
	/**
	 * function apply 1(no pixel size, computation in pixels
	 * @param fiducials
	 * @return
	 */
	public SimilarityTransformation3D apply(Vector<PointsPair3D> fiducials) {
		Enumeration<?> fiducialsE;

		// compute the mean point in both data sets

		double smeanx = 0, smeany = 0, smeanz = 0, tmeanx = 0, tmeany = 0, tmeanz = 0;
		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			smeanx += pair.first.getX();
			smeany += pair.first.getY();
			tmeanx += pair.second.getX();
			tmeany += pair.second.getY();
			smeanz += pair.first.getZ(); 
			tmeanz += pair.second.getZ();
		}
		int numPoints = fiducials.size();
		smeanx /= numPoints;
		smeany /= numPoints;
		tmeanx /= numPoints;
		tmeany /= numPoints;
		smeanz /= numPoints;
		tmeanz /= numPoints;

		// center the data around the means to remove translation

		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			pair.first.setLocation(pair.first.getX() - smeanx, pair.first.getY() - smeany, pair.first.getZ()- smeanz);
			pair.second.setLocation(pair.second.getX() - tmeanx, pair.second.getY() - tmeany, pair.second.getZ()- tmeanz);
		}

		// Determine scaling factor by comparing mean vector length CAREFUL
		// ANISOTROPY here!
		double meanlengthfirst = 0;
		double meanlengthsecond = 0;
		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			double normfirstsquared = (pair.first.getX() * pair.first.getX() + pair.first.getY() * pair.first.getY() + (pair.first.getZ() * pair.first.getZ()));
			double normsecondsquared = (pair.second.getX() * pair.second.getX() + pair.second.getY() * pair.second.getY() +  pair.second.getZ() * pair.second.getZ());
			meanlengthfirst += normfirstsquared;
			meanlengthsecond += normsecondsquared;
		}

		double scale = Math.sqrt(meanlengthsecond / meanlengthfirst);
		//double scalevtk=1.0; // under the assumpution that the pixel size is correct: the 2 images are supposed to represent the same physical object
		Matrix R = Matrix.identity(3, 3);

		if (fiducials.size() > 2) { // otherwise we do not compute any rotation
									// we put it as idendity matrix

			// Compute rotation matrix as follows
			// R=MQ(-1/2)
			// where M=sum of outer product
			fiducialsE = fiducials.elements();
			Matrix M = new Matrix(3, 3, 0);// construct a 3x3 empty matrix

			// in 3D it should be V1.V2=u1u2+v1v2+w1w2 (en repere orthonorme
			// (=when vector norms are one))
			// so divided by the product of norm of vectors
			int i=0;
			
			while (fiducialsE.hasMoreElements()) {
				PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
				Matrix Rs = new Matrix(3, 1);
				Matrix Rp = new Matrix(3, 1);
				// double
				// normfirstsquared=(pair.first.getX()*pair.first.getX()+pair.first.getY()*pair.first.getY());
				// double
				// normsecondsquared=(pair.second.getX()*pair.second.getX()+pair.second.getY()*pair.second.getY());
				Rs.set(0, 0, pair.first.getX());
				Rs.set(1, 0, pair.first.getY());

				Rs.set(2, 0, pair.first.getZ());
				//Rs.set(3, 0, 1);
				Rp.set(0, 0, pair.second.getX());
				Rp.set(1, 0, pair.second.getY());

				Rp.set(2, 0, pair.second.getZ());
				//Rp.set(3, 0, 1);
				System.out.println(i++);
				M.plusEquals(Rp.times(Rs.transpose()));
			}

			Matrix Q = (M.transpose()).times(M);
			EigenvalueDecomposition evd = Q.eig();
			Matrix v = evd.getV();
			Matrix d = evd.getD();

			for (int r = 0; r < d.getRowDimension(); r++) {
				for (int c = 0; c < d.getColumnDimension(); c++) {
					if (d.get(r, c) > 0)
						d.set(r, c, 1 / Math.sqrt(d.get(r, c)));
				}
			}
			Matrix invsquareRootQ = v.times(d.times(v.transpose()));

			R = M.times(invsquareRootQ);

		}

		// T=meantarget-sRmeansource
		double[][] vals = { { tmeanx }, { tmeany }, { tmeanz } };
		Matrix vecmeantarget = new Matrix(vals);
		double[][] valss = { { smeanx }, { smeany }, { smeanz } };
		;
		Matrix vecmeansource = new Matrix(valss);
		Matrix T = vecmeantarget.minus(R.times(vecmeansource).times(scale));
		//double[][] anisotrop={ { 1.0}, {1.0 }, { anisoTargetxoverz } };
		//Matrix vecanisotrop = new Matrix(anisotrop);
		//T.set(2, 0, T.get(2, 0)*anisoTargetxoverz);
		System.out.println("R is :");
		R.print(1, 5);
		System.out.println("T is : ");
		T.print(1, 5);
		System.out.println("Scale is " + scale);
		double[] scalexyz = { scale, scale };
		return (new SimilarityTransformation3D(T, R, scalexyz,1,1,1));
	}
	/**function apply 2
	 * to do later: make it call apply 1 to simplify code
	 * @param fiducials
	 * @param pixelSizeXsource
	 * @param pixelSizeYsource
	 * @param pixelSizeZsource
	 * @param pixelSizeXtarget
	 * @param pixelSizeYtarget
	 * @param pixelSizeZtarget
	 * @return
	 */

	public SimilarityTransformation3D apply(Vector<PointsPair3D> fiducials,
			double pixelSizeXsource, double pixelSizeYsource, double pixelSizeZsource, double pixelSizeXtarget,double pixelSizeYtarget, double pixelSizeZtarget) {
		Enumeration<?> fiducialsE;
		// TODO Auto-generated method stub
		
		// convert the data in physical units
		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			pair.first.setLocation( pair.first.getX()*pixelSizeXsource,pair.first.getY()*pixelSizeYsource, pair.first.getZ()*pixelSizeZsource);
			pair.second.setLocation( pair.second.getX()*pixelSizeXtarget,pair.second.getY()*pixelSizeYtarget, pair.second.getZ()*pixelSizeZtarget);
		}
		
		
		
		// compute the mean point in both data sets

				double smeanx = 0, smeany = 0, smeanz = 0, tmeanx = 0, tmeany = 0, tmeanz = 0;
				
				
				
				fiducialsE = fiducials.elements();
				while (fiducialsE.hasMoreElements()) {
					PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
					smeanx += pair.first.getX();
					smeany += pair.first.getY();
					tmeanx += pair.second.getX();
					tmeany += pair.second.getY();
					smeanz += pair.first.getZ(); 
					tmeanz += pair.second.getZ();
				}
				int numPoints = fiducials.size();
				smeanx /= numPoints;
				smeany /= numPoints;
				tmeanx /= numPoints;
				tmeany /= numPoints;
				smeanz /= numPoints;
				tmeanz /= numPoints;

				// center the data around the means to remove translation

				fiducialsE = fiducials.elements();
				while (fiducialsE.hasMoreElements()) {
					
					PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
					
					pair.first.setLocation(pair.first.getX() - smeanx,
							pair.first.getY() - smeany, pair.first.getZ()- smeanz);
					pair.second.setLocation(pair.second.getX() - tmeanx,
							pair.second.getY() - tmeany, pair.second.getZ()- tmeanz);
					if (pair.second.getZ()==0.0)
						pair.second.setLocation(pair.second.getX() ,
								pair.second.getY() ,(Random.nextDouble()*pixelSizeZtarget)-0.5);
					if (pair.first.getZ()==0.0)
						pair.first.setLocation(pair.first.getX() ,
								pair.first.getY() , (Random.nextDouble()*pixelSizeZsource)-0.5);

				}
				
				// Determine scaling factor by comparing mean vector length 
				double meanlengthfirst = 0;
				double meanlengthsecond = 0;
				fiducialsE = fiducials.elements();

				while (fiducialsE.hasMoreElements()) {

					PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
					double normfirstsquared = (pair.first.getX() * pair.first.getX()
							+ pair.first.getY() * pair.first.getY() + (pair.first.getZ() * pair.first.getZ()));
					double normsecondsquared = (pair.second.getX() * pair.second.getX()
							+ pair.second.getY() * pair.second.getY() +  pair.second.getZ() * pair.second.getZ());
					meanlengthfirst += normfirstsquared;
					meanlengthsecond += normsecondsquared;

				}
				double scale = Math.sqrt(meanlengthsecond / meanlengthfirst);
				//double scalevtk=1.0; // under the assumpution that the pixel size is correct: the 2 images are supposed to represent the same physical object
				Matrix R = Matrix.identity(3, 3);

				if (fiducials.size() > 3) { // otherwise we do not compute any rotation
											// we put it as idendity matrix

					// Compute rotation matrix as follows
					// R=MQ(-1/2)
					// where M=sum of outer product
					fiducialsE = fiducials.elements();
					Matrix M = new Matrix(3, 3, 0);// construct a 3x3 empty matrix

					// in 3D it should be V1.V2=u1u2+v1v2+w1w2 (en repere orthonorme
					// (=when vector norms are one))
					// so divided by the product of norm of vectors
					int i=0;
					
					while (fiducialsE.hasMoreElements()) {
						PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
						Matrix Rs = new Matrix(3, 1);
						Matrix Rp = new Matrix(3, 1);
						// double
						// normfirstsquared=(pair.first.getX()*pair.first.getX()+pair.first.getY()*pair.first.getY());
						// double
						// normsecondsquared=(pair.second.getX()*pair.second.getX()+pair.second.getY()*pair.second.getY());
						Rs.set(0, 0, pair.first.getX());
						Rs.set(1, 0, pair.first.getY());

						Rs.set(2, 0, pair.first.getZ());
						//Rs.set(3, 0, 1);
						Rp.set(0, 0, pair.second.getX());
						Rp.set(1, 0, pair.second.getY());

						Rp.set(2, 0, pair.second.getZ());
						//Rp.set(3, 0, 1);
						System.out.println(i++);
						M.plusEquals(Rp.times(Rs.transpose()));
						
					}

					Matrix Q = (M.transpose()).times(M);
					EigenvalueDecomposition evd = Q.eig();
					Matrix v = evd.getV();
					Matrix d = evd.getD();

					for (int r = 0; r < d.getRowDimension(); r++) {
						for (int c = 0; c < d.getColumnDimension(); c++) {
							if (d.get(r, c) > 0)
								d.set(r, c, 1 / Math.sqrt(d.get(r, c)));
						}
					}
					Matrix invsquareRootQ = v.times(d.times(v.transpose()));

					R = M.times(invsquareRootQ);
					
				}

				// T=meantarget-sRmeansource
				double[][] vals = { { tmeanx }, { tmeany }, { tmeanz } };
				Matrix vecmeantarget = new Matrix(vals);
				double[][] valss = { { smeanx }, { smeany }, { smeanz } };
				
				Matrix vecmeansource = new Matrix(valss);
				Matrix T = vecmeantarget.minus(R.times(vecmeansource).times(scale));
				//double[][] anisotrop={ { 1.0}, {1.0 }, { anisoTargetxoverz } };
				//Matrix vecanisotrop = new Matrix(anisotrop);
				//T.set(2, 0, T.get(2, 0)*anisoTargetxoverz);
				System.out.println("R is :");
				R.print(1, 5);
				System.out.println("T is : ");
				T.print(1, 5);
				System.out.println("Scale is " + scale);
				double[] scalexyz = { scale, scale };
				return (new SimilarityTransformation3D(T, R, scalexyz,pixelSizeXsource,pixelSizeYsource,pixelSizeZsource));
	}
}