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

import java.awt.geom.Point2D;

/**
 * Author: Perrine.Paul-Gilloteaux@curie.fr point pair 2D for point pair based
 * registration
 * Modified on the 18/07 to add name as an attribute
 */

public class PointsPair {

	public Point2D first; // source
	public Point2D second; // target
	public String name;

	public PointsPair(Point2D first, Point2D second, String name) {
		this.first = first;
		this.second = second;
		this.name=name;
	}
	public PointsPair(Point2D first, Point2D second) {
		this.first = first;
		this.second = second;
		
	}
	
	public PointsPair(PointsPair other) {
		this.first = new Point2D.Double();
		this.first.setLocation(other.first);
		this.second = new Point2D.Double();
		this.second.setLocation(other.second);
		this.name=this.getName();

	}

	/* return for each point the difference in position of the 2 points */

	public double getDiffinpixels() {

		return this.first.distance(this.second);

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
		return 1.0;
	}
	public double getfirstzinpixels(){
		return 1.0;
	}
	public String getName(){
		return this.name;
	}
	public void setName(String name){
		this.name=name;
	}
}
