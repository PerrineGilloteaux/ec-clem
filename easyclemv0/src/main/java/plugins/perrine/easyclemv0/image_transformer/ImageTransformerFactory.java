package plugins.perrine.easyclemv0.image_transformer;

public class ImageTransformerFactory {

    public RigidImageTransformerInterface createImageTransformer(int dimension) {
        switch (dimension) {
            case 2 : return new ImageTransformer();
            case 3 : return new Stack3DVTKTransformer();
            default: return null;
        }
    }
}
