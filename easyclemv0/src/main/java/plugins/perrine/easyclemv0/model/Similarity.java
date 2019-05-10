package plugins.perrine.easyclemv0.model;

import Jama.Matrix;

public class Similarity {

    private Matrix R;
    private Matrix T;
    private double scale;

    public Similarity(Matrix R, Matrix T, double scale) {
        this.R = R;
        this.T = T;
        this.scale = scale;
    }

    public Point apply(Point point) {
        return new Point(R.times(scale).times(point.getmatrix()).plus(T));
    }

    public Dataset apply(Dataset dataset) {
        Matrix M = dataset.getMatrix().times(R.times(scale));
        for(int j = 0; j < M.getColumnDimension(); j++) {
            for(int i = 0; i < M.getRowDimension(); i++) {
                M.set(i, j, M.get(i, j) + T.get(j, 0));
            }
        }
        return new Dataset(M.getArray());
    }

    public Matrix getR() {
        return R;
    }

    public Matrix getT() {
        return T;
    }

    public double getScale() {
        return scale;
    }

    public Matrix getMatrix() {
        Matrix S = new Matrix(R.getRowDimension() + 1, R.getColumnDimension() + 1, 0);
        for(int i = 0; i < R.getRowDimension(); i++) {
            for(int j = 0; j < R.getColumnDimension(); j++) {
                S.set(i, j, R.get(i, j) * scale);
            }
        }
        for(int i = 0; i < T.getRowDimension(); i++) {
            S.set(i, S.getColumnDimension() - 1, T.get(i, 0));
        }
        S.set(S.getRowDimension() - 1, S.getColumnDimension() - 1, 1);
        return S;
    }
}
