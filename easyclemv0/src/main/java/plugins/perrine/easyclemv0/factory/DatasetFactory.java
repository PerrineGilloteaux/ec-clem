package plugins.perrine.easyclemv0.factory;

import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.roi.RoiProcessor;

import java.util.ArrayList;

public class DatasetFactory {

    private RoiProcessor roiProcessor = new RoiProcessor();

    public Dataset getFrom(Sequence sequence) {
        convertAllROI(sequence);
        return new Dataset(roiProcessor.getPointsFromRoi(sequence.getROIs()));
    }

    private void convertAllROI(Sequence sequence) {
        ArrayList<ROI> roiList = sequence.getROIs();
        roiProcessor.convert(roiList);
        sequence.removeAllROI();
        sequence.addROIs(roiList, false);
    }
}
