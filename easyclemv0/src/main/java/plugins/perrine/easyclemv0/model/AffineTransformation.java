package plugins.perrine.easyclemv0.model;

import Jama.Matrix;

public class AffineTransformation {
    protected Matrix A;
    protected Matrix T;

    public Point apply(Point point) {
        return new Point(getMatrix().times(point.getmatrix()));
    }

    public Dataset apply(Dataset dataset) {
        Matrix M = dataset.getMatrix().times(getMatrix());
        return new Dataset(M.getArray());
    }

    public Matrix getMatrix() {
        Matrix M = new Matrix(A.getRowDimension() + 1, A.getColumnDimension() + 1, 0);
        for(int i = 0; i < A.getRowDimension(); i++) {
            for(int j = 0; j < A.getColumnDimension(); j++) {
                M.set(i, j, A.get(i, j));
            }
        }
        for(int i = 0; i < T.getRowDimension(); i++) {
            M.set(i, M.getColumnDimension() - 1, T.get(i, 0));
        }
        M.set(M.getRowDimension() - 1, M.getColumnDimension() - 1, 1);
        return M;
    }
}
