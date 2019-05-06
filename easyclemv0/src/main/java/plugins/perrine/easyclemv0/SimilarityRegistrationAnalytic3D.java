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

import Jama.SingularValueDecomposition;
import icy.util.Random;
import java.util.Enumeration;
import java.util.Vector;
import Jama.Matrix;

public class SimilarityRegistrationAnalytic3D {

	public SimilarityTransformation3D apply(Vector<PointsPair3D> fiducials) {
		PointsPair3D barycentre = getBarycentre(fiducials);
		substract(fiducials, barycentre);
		double scale = getScale(fiducials);
		Matrix R = getR(fiducials);
		Matrix T = getT(R, barycentre, scale);
		print("R", R);
		print("T", T);
		System.out.println(String.format("Scale is %f", scale));
		return new SimilarityTransformation3D(T, R, new double[] { scale, scale },1,1,1);
	}

	public SimilarityTransformation3D apply (
			Vector<PointsPair3D> fiducials,
			double pixelSizeXsource,
			double pixelSizeYsource,
			double pixelSizeZsource,
			double pixelSizeXtarget,
			double pixelSizeYtarget,
			double pixelSizeZtarget
	) {
		convertToPhysicalUnits(
			fiducials,
			pixelSizeXsource,
			pixelSizeYsource,
			pixelSizeZsource,
			pixelSizeXtarget,
			pixelSizeYtarget,
			pixelSizeZtarget
		);
		PointsPair3D barycentre = getBarycentre(fiducials);
		substract(fiducials, barycentre);
		addNoise(fiducials, pixelSizeZsource, pixelSizeZtarget);
		double scale = getScale(fiducials);
		Matrix R = getR(fiducials);
		Matrix T = getT(R, barycentre, scale);
		print("R", R);
		print("T", T);
		System.out.println("Scale is " + scale);
		return new SimilarityTransformation3D(T, R, new double[] { scale, scale }, pixelSizeXsource, pixelSizeYsource, pixelSizeZsource);
	}

	private PointsPair3D getBarycentre(Vector<PointsPair3D> points) {
		Enumeration<?> fiducialsE;
		double smeanx = 0, smeany = 0, smeanz = 0, tmeanx = 0, tmeany = 0, tmeanz = 0;
		int numPoints = points.size();
		fiducialsE = points.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			smeanx += pair.first.getX();
			smeany += pair.first.getY();
			smeanz += pair.first.getZ();
			tmeanx += pair.second.getX();
			tmeany += pair.second.getY();
			tmeanz += pair.second.getZ();
		}
		smeanx /= numPoints;
		smeany /= numPoints;
		smeanz /= numPoints;
		tmeanx /= numPoints;
		tmeany /= numPoints;
		tmeanz /= numPoints;
		return new PointsPair3D (
			new PPPoint3D(smeanx, smeany, smeanz),
			new PPPoint3D(tmeanx, tmeany, tmeanz)
		);
	}

	private void substract(Vector<PointsPair3D> points, PointsPair3D toSubstract) {
		Enumeration<?> fiducialsE;
		fiducialsE = points.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			pair.first.setLocation(
		pair.first.getX() - toSubstract.first.getX(),
		pair.first.getY() - toSubstract.first.getY(),
		pair.first.getZ()- toSubstract.first.getZ()
			);
			pair.second.setLocation(
		pair.second.getX() - toSubstract.second.getX(),
		pair.second.getY() - toSubstract.second.getY(),
		pair.second.getZ()- toSubstract.second.getZ()
			);
		}
	}

	private double getScale(Vector<PointsPair3D> points) {
		double meanlengthfirst = 0;
		double meanlengthsecond = 0;
		Enumeration<?> fiducialsE;
		fiducialsE = points.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			double normfirstsquared = pair.first.getX() * pair.first.getX() + pair.first.getY() * pair.first.getY() + (pair.first.getZ() * pair.first.getZ());
			double normsecondsquared = pair.second.getX() * pair.second.getX() + pair.second.getY() * pair.second.getY() +  pair.second.getZ() * pair.second.getZ();
			meanlengthfirst += normfirstsquared;
			meanlengthsecond += normsecondsquared;
		}
		return Math.sqrt(meanlengthsecond / meanlengthfirst);
	}

	private Matrix getR(Vector<PointsPair3D> points) {
		if (points.size() < 3) {
			return Matrix.identity(3, 3);
		}
		Enumeration<?> fiducialsE;
		fiducialsE = points.elements();
		Matrix M = new Matrix(3, 3, 0);
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			Matrix Rs = new Matrix(3, 1);
			Matrix Rt = new Matrix(3, 1);
			Rs.set(0, 0, pair.first.getX());
			Rs.set(1, 0, pair.first.getY());
			Rs.set(2, 0, pair.first.getZ());
			Rt.set(0, 0, pair.second.getX());
			Rt.set(1, 0, pair.second.getY());
			Rt.set(2, 0, pair.second.getZ());
			M.plusEquals(Rs.transpose().times(Rt));
		}
		SingularValueDecomposition svd = M.svd();
		return svd.getV().times(svd.getU().transpose());
	}

	private Matrix getT(Matrix R, PointsPair3D barycentre, double scale) {
		Matrix sourceBarycentre = new Matrix(
				new double[][] {{ barycentre.first.getX() }, { barycentre.first.getY() }, { barycentre.first.getZ() }}
		);
		Matrix targetBarycentre = new Matrix(
				new double[][] {{ barycentre.second.getX() }, { barycentre.second.getY() }, { barycentre.second.getZ() }}
		);
		return targetBarycentre.minus(R.times(sourceBarycentre).times(scale));
	}

	private void print(String name, Matrix M) {
		System.out.println(String.format("%s is :", name));
		M.print(1, 5);
	}

	private void convertToPhysicalUnits (
			Vector<PointsPair3D> points,
			double pixelSizeXsource,
			double pixelSizeYsource,
			double pixelSizeZsource,
			double pixelSizeXtarget,
			double pixelSizeYtarget,
			double pixelSizeZtarget
	) {
		Enumeration<?> fiducialsE;
		fiducialsE = points.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			pair.first.setLocation( pair.first.getX() * pixelSizeXsource,pair.first.getY() * pixelSizeYsource, pair.first.getZ() * pixelSizeZsource);
			pair.second.setLocation( pair.second.getX() * pixelSizeXtarget,pair.second.getY() * pixelSizeYtarget, pair.second.getZ() * pixelSizeZtarget);
		}
	}

	private void addNoise(Vector<PointsPair3D> points, double pixelSizeZsource, double pixelSizeZtarget) {
		Enumeration<?> fiducialsE;
		fiducialsE = points.elements();
		while (fiducialsE.hasMoreElements()) {
			PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
			if (pair.first.getZ() == 0.0) {
				pair.first.setLocation(
						pair.first.getX(),
						pair.first.getY(),
						(Random.nextDouble() * pixelSizeZsource) - 0.5
				);
			}
			if (pair.second.getZ() == 0.0) {
				pair.second.setLocation(
						pair.second.getX(),
						pair.second.getY(),
						(Random.nextDouble() * pixelSizeZtarget) - 0.5
				);
			}
		}
	}
}