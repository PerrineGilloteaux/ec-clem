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
 * Author: Perrine.Paul-Gilloteaux@curie.fr A 3D point
 */

public class PPPoint3D {
	double x, y, z;

	public PPPoint3D() {
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
	}

	public PPPoint3D(double xx, double yy, double zz) {
		this.x = xx;
		this.y = yy;
		this.z = zz;
	}

	public void setLocation(PPPoint3D other) {
		this.x = other.getX();
		this.y = other.getY();
		this.z = other.getZ();

	}

	public double getX() {

		return x;
	}

	public double getY() {

		return y;
	}

	public double getZ() {

		return z;
	}

	public double distance(PPPoint3D second) {

		return Math.sqrt((this.x - second.getX()) * (this.x - second.getX())
				+ (this.y - second.getY()) * (this.y - second.getY())
				+ (this.z - second.getZ()) * (this.z - second.getZ()));
	}

	/**
	 * 
	 * @param second
	 * @param aniso
	 *            is the scale between x,y (considered as isotropic) and z such
	 *            as z/x=aniso exemple: x= 1; y=1; z=3->aniso should be 3 to
	 *            take into account the anisotropy in the distance computation.
	 * @return
	 */
	public double distanceaniso(PPPoint3D second, double aniso) {

		return Math.sqrt((this.x - second.getX()) * (this.x - second.getX())
				+ (this.y - second.getY()) * (this.y - second.getY()) + aniso
				* aniso * (this.z - second.getZ()) * (this.z - second.getZ()));
	}

	public void setLocation(double newX, double newY, double newZ) {
		x = newX;
		y = newY;
		z = newZ;

	}
}
