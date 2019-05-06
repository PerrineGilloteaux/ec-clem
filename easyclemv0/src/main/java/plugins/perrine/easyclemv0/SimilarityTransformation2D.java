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

//import icy.util.XMLUtil;

import java.awt.geom.Point2D;

import Jama.Matrix;

/**
 * Author Perrine.paul-gilloteaux@curie.fr Similarity = rotation (around gravity
 * center then)+translation+scale
 * 
 * TODO: Rename for affine ecause of flip allowed
 */

public class SimilarityTransformation2D {
	private double dx, dy;
	private double sTheta, cTheta;
	private double scale;
	private Matrix T;
	private Matrix R;

	// Constructor
	public SimilarityTransformation2D(Matrix T, Matrix R, double scale) {
		this.dx = T.get(0, 0);
		this.dy = T.get(1, 0);
		this.scale = scale;
		cTheta = R.get(0, 0); // should be symetric is everything wenbt fine...
		sTheta = R.get(1, 0);
		this.T = T.copy();
		this.R = R.copy();
	}

	// Apply the transformation to one point does not support flipping
	public void apply_old(Point2D pnt) {

		double newX = dx + scale * cTheta * pnt.getX() - scale * sTheta
				* pnt.getY();
		double newY = dy + scale * sTheta * pnt.getX() + scale * cTheta
				* pnt.getY();
		pnt.setLocation(newX, newY);
	}

	public void apply(Point2D pnt) {
		Matrix matrixtransfo = this.getMatrix();
		double newX = matrixtransfo.get(0, 3) + matrixtransfo.get(0, 0)
				* pnt.getX() + matrixtransfo.get(0, 1) * pnt.getY();
		double newY = matrixtransfo.get(1, 3) + matrixtransfo.get(1, 0)
				* pnt.getX() + matrixtransfo.get(1, 1) * pnt.getY();
		pnt.setLocation(newX, newY);
	}

	// accessor
	public double getS() {

		return this.sTheta;
	}

	public double getC() {

		return this.cTheta;
	}

	public double getdx() {

		return this.dx;
	}

	public double getdy() {

		return this.dy;
	}

	public double getscale() {

		return this.scale;
	}

	public Matrix getR() {
		return this.R;
	}

	public Matrix getT() {
		return this.T;
	}

	public Matrix getMatrix() {
		Matrix transfomatrix3D = new Matrix(4, 4);
		transfomatrix3D.set(0, 0, R.get(0, 0) * scale);
		transfomatrix3D.set(0, 1, R.get(0, 1) * scale);
		transfomatrix3D.set(0, 2, R.get(0, 2) * scale);
		transfomatrix3D.set(0, 3, T.get(0, 0));

		transfomatrix3D.set(1, 0, R.get(1, 0) * scale);
		transfomatrix3D.set(1, 1, R.get(1, 1) * scale);
		transfomatrix3D.set(1, 2, R.get(1, 2) * scale);
		transfomatrix3D.set(1, 3, T.get(1, 0));

		transfomatrix3D.set(2, 0, R.get(2, 0) * scale);
		transfomatrix3D.set(2, 1, R.get(2, 1) * scale);
		transfomatrix3D.set(2, 2, R.get(2, 2) * scale);
		transfomatrix3D.set(2, 3, T.get(2, 0));

		transfomatrix3D.set(3, 0, 0);
		transfomatrix3D.set(3, 1, 0);
		transfomatrix3D.set(3, 2, 0);
		transfomatrix3D.set(3, 3, 1);

		return transfomatrix3D;
	}

}
