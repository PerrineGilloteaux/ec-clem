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



import Jama.Matrix;
/**  Author Perrine.paul-gilloteaux@curie.fr
* Similarity = rotation (around gravity center then)+translation+scale
*  TODO: check the 3D transform (actually the matrices T and R are already 3D, but this is this class oversimplifying the problem.
* TODO Rename for affine ecause of flip allowed
*/ 
public class SimilarityTransformation3D {
	private double dx, dy, dz;

	private double scalexy;
	private double scalez;
	private Matrix T;
	private Matrix R;// R=Rz(kappa)Ry(Phi)Rx(omega)
	private double orisizeX, orisizeY,orisizeZ; // because the transform have been computed in physical units

	// Constructor
	public SimilarityTransformation3D(Matrix T, Matrix R, double[] scale,double orisizeX, double orisizeY, double orisizeZ) {
		this.dx = T.get(0, 0);
		this.dy = T.get(1, 0);
		this.dz = T.get(2, 0);
		this.scalexy = scale[0];
		this.scalez = scale[1];// to take anisotrop in account

		this.T = T.copy();
		this.R = R.copy();
		this.orisizeX=orisizeX;
		this.orisizeY=orisizeY;
		this.orisizeZ=orisizeZ;
	}

	
	public SimilarityTransformation3D(Matrix Transfo, double orisizex2,
			double orisizey2, double orisizez2) {
		this.scalexy=1.0;
		this.scalez=1.0;
		this.R=new Matrix(3,3);
		this.T=new Matrix(3,1);
		R.set(0, 0,Transfo.get(0, 0) );
		R.set(0, 1,Transfo.get(0, 1));
		R.set(0, 2,Transfo.get(0, 2));
		R.set(1,0 ,Transfo.get(1, 0));
		R.set(1, 1,Transfo.get(1, 1));
		R.set(1, 2,Transfo.get(1, 2));
		R.set(2,0 ,Transfo.get(2, 0));
		R.set(2, 1,Transfo.get(2, 1));
		R.set(2, 2,Transfo.get(2, 2));
		T.set(0, 0, Transfo.get(0, 3));
		T.set(1, 0, Transfo.get(1, 3));
		T.set(2, 0, Transfo.get(2, 3));
		
		
		
		this.orisizeX=orisizex2;
		this.orisizeY=orisizey2;
		this.orisizeZ=orisizez2;
	}


	/**
	 * Matrix is sr11 sr12 szr13 tx sr21 sr22 szr23 ty sr31 sr31 szr33 tz 0 0 0
	 * 1
	 * Return the new position in PHYSICAL UNIT, while it is given in PIXEL in input
	 * @param pnt
	 */

	public void apply(PPPoint3D pnt) {
		Matrix matrixtransfo = this.getMatrix();

		double newX = matrixtransfo.get(0, 3) + matrixtransfo.get(0, 0)
				* pnt.getX()*this.orisizeX + matrixtransfo.get(0, 1) * pnt.getY()*this.orisizeY
				+ matrixtransfo.get(0, 2) * pnt.getZ()*this.orisizeZ;
		double newY = matrixtransfo.get(1, 3) + matrixtransfo.get(1, 0)
				* pnt.getX()*this.orisizeX + matrixtransfo.get(1, 1) * pnt.getY()*this.orisizeY
				+ matrixtransfo.get(1, 2) * pnt.getZ()*this.orisizeZ;
		double newZ = matrixtransfo.get(2, 3) + matrixtransfo.get(2, 0)
				* pnt.getX()*this.orisizeX + matrixtransfo.get(2, 1) * pnt.getY()*this.orisizeY
				+ matrixtransfo.get(2, 2) * pnt.getZ()*this.orisizeZ;
		pnt.setLocation(newX, newY, newZ);
	}
	public double getorisizex(){
		return this.orisizeX;
	}
	public double getorisizey(){
		return this.orisizeY;
	}
	public double getorisizez(){
		return this.orisizeZ;
	}
	
	
	public double getdx() {

		return this.dx;
	}

	public double getdy() {

		return this.dy;
	}
	public double getdz() {

		return this.dz;
	}

	public double getscalex() {

		return this.scalexy;
	}
	public double getscalez() {

		return this.scalez;
	}

	public Matrix getR() {
		return this.R;
	}

	public Matrix getT() {
		return this.T;
	}

	public Matrix getMatrix() {
		Matrix transfomatrix3D = new Matrix(4, 4);
		transfomatrix3D.set(0, 0, R.get(0, 0) * scalexy);
		transfomatrix3D.set(0, 1, R.get(0, 1) * scalexy);
		transfomatrix3D.set(0, 2, R.get(0, 2) * scalexy);
		transfomatrix3D.set(0, 3, T.get(0, 0));

		transfomatrix3D.set(1, 0, R.get(1, 0) * scalexy);
		transfomatrix3D.set(1, 1, R.get(1, 1) * scalexy);
		transfomatrix3D.set(1, 2, R.get(1, 2) * scalexy);
		transfomatrix3D.set(1, 3, T.get(1, 0));

		transfomatrix3D.set(2, 0, R.get(2, 0) * scalexy);
		transfomatrix3D.set(2, 1, R.get(2, 1) * scalexy);
		transfomatrix3D.set(2, 2, R.get(2, 2) * scalez);
		transfomatrix3D.set(2, 3, T.get(2, 0));

		transfomatrix3D.set(3, 0, 0);
		transfomatrix3D.set(3, 1, 0);
		transfomatrix3D.set(3, 2, 0);
		transfomatrix3D.set(3, 3, 1);

		return transfomatrix3D;
	}
	
	public Matrix getMatrixnoScale() {
		Matrix transfomatrix3D = new Matrix(4, 4);
		transfomatrix3D.set(0, 0, R.get(0, 0) );
		transfomatrix3D.set(0, 1, R.get(0, 1) );
		transfomatrix3D.set(0, 2, R.get(0, 2) );
		transfomatrix3D.set(0, 3, T.get(0, 0));

		transfomatrix3D.set(1, 0, R.get(1, 0) );
		transfomatrix3D.set(1, 1, R.get(1, 1) );
		transfomatrix3D.set(1, 2, R.get(1, 2) );
		transfomatrix3D.set(1, 3, T.get(1, 0));

		transfomatrix3D.set(2, 0, R.get(2, 0));
		transfomatrix3D.set(2, 1, R.get(2, 1) );
		transfomatrix3D.set(2, 2, R.get(2, 2) );
		transfomatrix3D.set(2, 3, T.get(2, 0));

		transfomatrix3D.set(3, 0, 0);
		transfomatrix3D.set(3, 1, 0);
		transfomatrix3D.set(3, 2, 0);
		transfomatrix3D.set(3, 3, 1);

		return transfomatrix3D;
	}

}
