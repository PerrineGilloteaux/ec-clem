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
 * Author : Perrine.Paul-Gilloteaux@curie.fr Method SVD
 * Roger Penroise 1956 (showing that solving a linear system using pseudo inverse was equivalent in solving the equivalent least square problem.
 * From http://www.comp.nus.edu.sg/~cs6240/lecture/rigid.pdf (read on the 06/08/2014)
 * 2014
 */

import java.util.Enumeration;
import java.util.Vector;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

public class SimilarityRegistrationAnalytic {
	public SimilarityTransformation2D apply(Vector<PointsPair> fiducials) {
		Enumeration<?> fiducialsE;

		// compute the mean point in both data sets

		double smeanx = 0, smeany = 0, tmeanx = 0, tmeany = 0;
		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			smeanx += pair.first.getX();
			smeany += pair.first.getY();
			tmeanx += pair.second.getX();
			tmeany += pair.second.getY();
		}
		int numPoints = fiducials.size();
		smeanx /= numPoints;
		smeany /= numPoints;
		tmeanx /= numPoints;
		tmeany /= numPoints;

		// center the data around the means to remove translation

		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			pair.first.setLocation(pair.first.getX() - smeanx,
					pair.first.getY() - smeany);
			pair.second.setLocation(pair.second.getX() - tmeanx,
					pair.second.getY() - tmeany);

		}

		// Determine scaling factor by comparing mean vector length
		double meanlengthfirst = 0;
		double meanlengthsecond = 0;
		fiducialsE = fiducials.elements();

		while (fiducialsE.hasMoreElements()) {

			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			double normfirstsquared = (pair.first.getX() * pair.first.getX() + pair.first
					.getY() * pair.first.getY());
			double normsecondsquared = (pair.second.getX() * pair.second.getX() + pair.second
					.getY() * pair.second.getY());
			meanlengthfirst += normfirstsquared;
			meanlengthsecond += normsecondsquared;

		}
		double scale = Math.sqrt(meanlengthsecond / meanlengthfirst);

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
			while (fiducialsE.hasMoreElements()) {
				PointsPair pair = (PointsPair) fiducialsE.nextElement();
				Matrix Rs = new Matrix(3, 1);
				Matrix Rp = new Matrix(3, 1);
				// double
				// normfirstsquared=(pair.first.getX()*pair.first.getX()+pair.first.getY()*pair.first.getY());
				// double
				// normsecondsquared=(pair.second.getX()*pair.second.getX()+pair.second.getY()*pair.second.getY());
				Rs.set(0, 0, pair.first.getX());
				Rs.set(1, 0, pair.first.getY());
				// rajouter un test sur mode 3d et passer en z!! RAJOUTER AUSSI
				// DANS pair (PointsPair)
				Rs.set(2, 0, 1.);
				Rp.set(0, 0, pair.second.getX());
				Rp.set(1, 0, pair.second.getY());
				// rajouter un test sur mode 3d et passer en z!!
				Rp.set(2, 0, 1.);

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
		double[][] vals = { { tmeanx }, { tmeany }, { 1. } };
		Matrix vecmeantarget = new Matrix(vals);
		double[][] valss = { { smeanx }, { smeany }, { 1. } };
		;
		Matrix vecmeansource = new Matrix(valss);
		Matrix T = vecmeantarget.minus(R.times(vecmeansource).times(scale));
		System.out.println("R is :");
		R.print(1, 5);
		System.out.println("T is : ");
		T.print(1, 5);
		System.out.println("Scale is " + scale);

		return (new SimilarityTransformation2D(T, R, scale));
	}
	public SimilarityTransformation2D applynomessage(Vector<PointsPair> fiducials) {
		Enumeration<?> fiducialsE;

		// compute the mean point in both data sets

		double smeanx = 0, smeany = 0, tmeanx = 0, tmeany = 0;
		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			smeanx += pair.first.getX();
			smeany += pair.first.getY();
			tmeanx += pair.second.getX();
			tmeany += pair.second.getY();
		}
		int numPoints = fiducials.size();
		smeanx /= numPoints;
		smeany /= numPoints;
		tmeanx /= numPoints;
		tmeany /= numPoints;

		// center the data around the means to remove translation

		fiducialsE = fiducials.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			pair.first.setLocation(pair.first.getX() - smeanx,
					pair.first.getY() - smeany);
			pair.second.setLocation(pair.second.getX() - tmeanx,
					pair.second.getY() - tmeany);

		}

		// Determine scaling factor by comparing mean vector length
		double meanlengthfirst = 0;
		double meanlengthsecond = 0;
		fiducialsE = fiducials.elements();

		while (fiducialsE.hasMoreElements()) {

			PointsPair pair = (PointsPair) fiducialsE.nextElement();
			double normfirstsquared = (pair.first.getX() * pair.first.getX() + pair.first
					.getY() * pair.first.getY());
			double normsecondsquared = (pair.second.getX() * pair.second.getX() + pair.second
					.getY() * pair.second.getY());
			meanlengthfirst += normfirstsquared;
			meanlengthsecond += normsecondsquared;

		}
		double scale = Math.sqrt(meanlengthsecond / meanlengthfirst);

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
			while (fiducialsE.hasMoreElements()) {
				PointsPair pair = (PointsPair) fiducialsE.nextElement();
				Matrix Rs = new Matrix(3, 1);
				Matrix Rp = new Matrix(3, 1);
				// double
				// normfirstsquared=(pair.first.getX()*pair.first.getX()+pair.first.getY()*pair.first.getY());
				// double
				// normsecondsquared=(pair.second.getX()*pair.second.getX()+pair.second.getY()*pair.second.getY());
				Rs.set(0, 0, pair.first.getX());
				Rs.set(1, 0, pair.first.getY());
				// rajouter un test sur mode 3d et passer en z!! RAJOUTER AUSSI
				// DANS pair (PointsPair)
				Rs.set(2, 0, 1.);
				Rp.set(0, 0, pair.second.getX());
				Rp.set(1, 0, pair.second.getY());
				// rajouter un test sur mode 3d et passer en z!!
				Rp.set(2, 0, 1.);

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
		double[][] vals = { { tmeanx }, { tmeany }, { 1. } };
		Matrix vecmeantarget = new Matrix(vals);
		double[][] valss = { { smeanx }, { smeany }, { 1. } };
		;
		Matrix vecmeansource = new Matrix(valss);
		Matrix T = vecmeantarget.minus(R.times(vecmeansource).times(scale));
		

		return (new SimilarityTransformation2D(T, R, scale));
	}
}