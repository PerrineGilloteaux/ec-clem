package plugins.perrine.easyclemv0.util;

import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import plugins.perrine.easyclemv0.model.DimensionSize;
import plugins.perrine.easyclemv0.model.SequenceSize;

public abstract class SequenceUtil {

    public static SequenceSize getUsedDimensions(Sequence sequence) {
        SequenceSize sequenceSize = new SequenceSize();

        if(sequence.getSizeX() > 1) {
            sequenceSize.add(new DimensionSize(DimensionId.X, sequence.getSizeX(), sequence.getPixelSizeX()));
        }

        if(sequence.getSizeY() > 1) {
            sequenceSize.add(new DimensionSize(DimensionId.Y, sequence.getSizeY(), sequence.getPixelSizeY()));
        }

        if(sequence.getSizeZ() > 1) {
            sequenceSize.add(new DimensionSize(DimensionId.Z, sequence.getSizeZ(), sequence.getPixelSizeZ()));
        }

        return sequenceSize;
    }
}
