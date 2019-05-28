package plugins.perrine.easyclemv0.registration;

import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.model.Similarity;

public class TransformationComputer {

    private NDimensionnalSimilarityRegistration nDimensionnalSimilarityRegistration;

    public TransformationComputer() {
        nDimensionnalSimilarityRegistration = new NDimensionnalSimilarityRegistration();
    }

    public Similarity compute(Dataset sourceDataset, Dataset targetDataset) {
//        Matrix combinedtransfobefore = xmlFileStorage.read();
        Similarity similarity = nDimensionnalSimilarityRegistration.apply(sourceDataset, targetDataset);
        similarity.printSummary();

//        Matrix transfo = similarity.getMatrix();
//        if(combinedtransfobefore != null) {
//            transfo = combinedtransfobefore.times(similarity.getMatrix());
//        }

        return similarity;
    }
}
