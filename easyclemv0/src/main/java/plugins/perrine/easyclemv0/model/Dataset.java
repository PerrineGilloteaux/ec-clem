package plugins.perrine.easyclemv0.model;

import Jama.Matrix;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

import java.util.List;

public class Dataset {
    private Matrix points;
    private int dimension;
    private int n;
    private Mean mean = new Mean();

    public Dataset(List<Point> points) {
        n = points.size();
        dimension = points.get(0).getDimension();
        this.points = new Matrix(n, dimension);
        for(int i = 0; i < n; i++) {
            for(int j = 0; j < dimension; j++) {
                this.points.set(i, j, points.get(i).getmatrix().get(j, 0));
            }
        }
    }

    public Dataset(double[][] points) {
        n = points.length;
        dimension = points[0].length;
        this.points = new Matrix(points);
    }

    public Point getBarycentre() {
        Matrix barycentre = new Matrix(dimension, 1);
        for(int i = 0; i < dimension; i++) {
            mean.clear();
            for(int j = 0; j < n; j++) {
                mean.increment(points.get(j, i));
            }
            barycentre.set(i, 0, mean.getResult());
        }
        return new Point(barycentre);
    }

    public void substractBarycentre() {
        substractRowWise(
            getBarycentre()
        );
    }

    public double getMeanNorm() {
        mean.clear();
        for(int i = 0; i < n; i++) {
            mean.increment(points.getMatrix(i, i, 0, dimension - 1).norm2());
        }
        return mean.getResult();
    }

    public boolean isCoplanar() {
        boolean result = true;
        for(int j = 0; j < dimension; j++) {
            boolean isCoplanarForColumn = true;
            double firstValue = points.get(0, j);
            for(int i = 1; i < n; i++) {
                if(points.get(i, j) != firstValue) {
                    isCoplanarForColumn = false;
                    break;
                }
            }
            result &= isCoplanarForColumn;
        }
        return result;
    }

    private void substractRowWise(Point point) {
        for(int j = 0; j < dimension; j++) {
            for(int i = 0; i < n; i++) {
                points.set(i, j, points.get(i, j) - point.getmatrix().get(j, 0));
            }
        }
    }

    public Matrix getMatrix() {
        return points;
    }

    public int getN() {
        return n;
    }

    public int getDimension() {
        return dimension;
    }
}
