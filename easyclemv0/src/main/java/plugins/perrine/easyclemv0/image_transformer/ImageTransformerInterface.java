package plugins.perrine.easyclemv0.image_transformer;

import Jama.Matrix;
import icy.sequence.Sequence;

public interface ImageTransformerInterface {
    void setImageSource(Sequence sequence);
    void setDestinationsize(Sequence target);
    void setParameters(Matrix transfo);
    void run();
}
