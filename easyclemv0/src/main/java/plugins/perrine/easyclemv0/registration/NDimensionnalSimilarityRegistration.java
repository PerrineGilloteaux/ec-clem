package plugins.perrine.easyclemv0.registration;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.model.Similarity;

public class NDimensionnalSimilarityRegistration {

    public Similarity apply(Dataset source, Dataset target) {

        source.substractBarycentre();
        target.substractBarycentre();
        double scale = Math.sqrt(target.getMeanNorm() / source.getMeanNorm());

        Matrix R = getR(source, target);
        Matrix T = getT(R, source.getBarycentre().getmatrix(), target.getBarycentre().getmatrix(), scale);
        print("R", R);
        print("T", T);
        System.out.println("Scale is " + scale);
        return new Similarity(R, T, scale);
    }

    private Matrix getR(Dataset source, Dataset target) {
        if (source.getN() < 3) {
            return Matrix.identity(3, 3);
        }

        Matrix M = target.getMatrix().transpose().times(source.getMatrix());
        SingularValueDecomposition svd = M.svd();
        return svd.getV().times(svd.getU().transpose());
    }

    private Matrix getT(Matrix R, Matrix sourceBarycentre, Matrix targetBarycentre, double scale) {
        return targetBarycentre.minus(R.times(sourceBarycentre).times(scale));
    }

    private void print(String name, Matrix M) {
        System.out.println(String.format("%s is :", name));
        M.print(1, 5);
    }
}
