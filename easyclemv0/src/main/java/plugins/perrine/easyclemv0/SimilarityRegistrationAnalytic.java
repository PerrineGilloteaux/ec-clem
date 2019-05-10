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

import java.awt.geom.Point2D;
import java.util.Enumeration;
import java.util.Vector;
import Jama.Matrix;
import Jama.SingularValueDecomposition;

public class SimilarityRegistrationAnalytic {

    public SimilarityTransformation2D apply(Vector<PointsPair> fiducials) {
        PointsPair barycentre = getBarycentre(fiducials);
        substract(fiducials, barycentre);
        double scale = getScale(fiducials);
        Matrix R = getR(fiducials);
        Matrix T = getT(R, barycentre, scale);
        print("R", R);
        print("T", T);
        System.out.println("Scale is " + scale);
        return new SimilarityTransformation2D(T, R, scale);
    }

    public SimilarityTransformation2D applynomessage(Vector<PointsPair> fiducials) {
        PointsPair barycentre = getBarycentre(fiducials);
        substract(fiducials, barycentre);
        double scale = getScale(fiducials);
        Matrix R = getR(fiducials);
        Matrix T = getT(R, barycentre, scale);
        return new SimilarityTransformation2D(T, R, scale);
    }

    private PointsPair getBarycentre(Vector<PointsPair> points) {
        int numPoints = points.size();
        Enumeration<?> fiducialsE;
        double smeanx = 0, smeany = 0, tmeanx = 0, tmeany = 0;
        fiducialsE = points.elements();
        while (fiducialsE.hasMoreElements()) {
            PointsPair pair = (PointsPair) fiducialsE.nextElement();
            smeanx += pair.first.getX();
            smeany += pair.first.getY();
            tmeanx += pair.second.getX();
            tmeany += pair.second.getY();
        }
        smeanx /= numPoints;
        smeany /= numPoints;
        tmeanx /= numPoints;
        tmeany /= numPoints;
        return new PointsPair (
            new Point2D.Double(smeanx, smeany),
            new Point2D.Double(tmeanx, tmeany)
        );
    }

    private void substract(Vector<PointsPair> points, PointsPair toSubstract) {
        Enumeration<?> fiducialsE;
        fiducialsE = points.elements();
        while (fiducialsE.hasMoreElements()) {
            PointsPair pair = (PointsPair) fiducialsE.nextElement();
            pair.first.setLocation(
                pair.first.getX() - toSubstract.first.getX(),
                pair.first.getY() - toSubstract.first.getY()
            );
            pair.second.setLocation(
                pair.second.getX() - toSubstract.second.getX(),
                pair.second.getY() - toSubstract.second.getY()
            );
        }
    }

    private double getScale(Vector<PointsPair> points) {
        double meanlengthfirst = 0;
        double meanlengthsecond = 0;
        Enumeration<?> fiducialsE;
        fiducialsE = points.elements();
        while (fiducialsE.hasMoreElements()) {
            PointsPair3D pair = (PointsPair3D) fiducialsE.nextElement();
            double normfirstsquared = pair.first.getX() * pair.first.getX() + pair.first.getY() * pair.first.getY();
            double normsecondsquared = pair.second.getX() * pair.second.getX() + pair.second.getY() * pair.second.getY();
            meanlengthfirst += normfirstsquared;
            meanlengthsecond += normsecondsquared;
        }
        return Math.sqrt(meanlengthsecond / meanlengthfirst);
    }

    private Matrix getR(Vector<PointsPair> points) {
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
            Rs.set(2, 0, 1);
            Rt.set(0, 0, pair.second.getX());
            Rt.set(1, 0, pair.second.getY());
            Rt.set(2, 0, 1);
            M.plusEquals(Rs.transpose().times(Rt));
        }
        SingularValueDecomposition svd = M.svd();
        return svd.getV().times(svd.getU().transpose());
    }

    private Matrix getT(Matrix R, PointsPair barycentre, double scale) {
        Matrix sourceBarycentre = new Matrix(
            new double[][] {{ barycentre.first.getX() }, { barycentre.first.getY() }, { 1 }}
        );
        Matrix targetBarycentre = new Matrix(
            new double[][] {{ barycentre.second.getX() }, { barycentre.second.getY() }, { 1 }}
        );
        return targetBarycentre.minus(R.times(sourceBarycentre).times(scale));
    }

    private void print(String name, Matrix M) {
        System.out.println(String.format("%s is :", name));
        M.print(1, 5);
    }
}