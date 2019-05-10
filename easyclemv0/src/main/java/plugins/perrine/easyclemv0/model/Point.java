package plugins.perrine.easyclemv0.model;

import Jama.Matrix;

public class Point {
    private Matrix coordinates;

    public Point(int dimension) {
        coordinates = new Matrix(dimension, 1);
    }

    public Point(Matrix matrix) {
        this.coordinates = matrix;
    }

    public int getDimension() {
        return coordinates.getRowDimension();
    }

    public Matrix getmatrix() {
        return coordinates;
    }
}
