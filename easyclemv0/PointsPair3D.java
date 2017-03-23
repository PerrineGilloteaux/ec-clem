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
 * Author: Perrine.Paul-Gilloteaux@curie.fr pointpair 3D methods
 */

public class PointsPair3D {

	public PPPoint3D first; // source
	public PPPoint3D second; // target

	public PointsPair3D(PPPoint3D first, PPPoint3D second) {
		this.first = first;
		this.second = second;

	}

	public PointsPair3D(PointsPair3D other) {
		this.first = new PPPoint3D();
		this.first.setLocation(other.first);
		this.second = new PPPoint3D();
		this.second.setLocation(other.second);

	}

	/* return for each point the difference in position of the 2 points */

	public double getDiffinpixels() {

		return this.first.distance(this.second);

	}

	/* return for each point the difference in position of the 2 points */

	public double getDiffinpixelsaniso(double aniso) {

		return this.first.distanceaniso(this.second, aniso);

	}
	
	
	public double getfirstxinpixels(){
		return this.first.getX();
	}
	public double getfirstyinpixels(){
		return this.first.getY();
	}
	public double getsecondxinpixels(){
		return this.second.getX();
	}
	public double getsecondyinpixels(){
		return this.second.getY();
	}
	public double getsecondzinpixels(){
		return this.second.getZ();
	}
	public double getfirstzinpixels(){
		return this.first.getZ();
	}

	public double getDiffx_squared_inpixels() {
		// TODO Auto-generated method stub
		return Math.pow(this.second.getX()-this.first.getX(),2);
	}
	public double getDiffy_squared_inpixels() {
		// TODO Auto-generated method stub
		return Math.pow(this.second.getY()-this.first.getY(),2);
	}
	public double getDiffz_squared_inpixels() {
		// TODO Auto-generated method stub
		return Math.pow(this.second.getZ()-this.first.getZ(),2);
	}
}
