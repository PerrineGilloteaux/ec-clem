package plugins.perrine.easyclemv0.storage.xml.non_rigid;

import icy.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.model.FiducialSet;
import plugins.perrine.easyclemv0.model.Point;
import java.util.ArrayList;
import static plugins.perrine.easyclemv0.storage.xml.non_rigid.NonRigidTransformationXml.*;
import static plugins.perrine.easyclemv0.storage.xml.non_rigid.NonRigidTransformationXml.coordinateDimensionAttributeName;

public class NonRigidTransformationXmlReader {

    public FiducialSet read(Document document) {
        Element fiducialSetElement = XMLUtil.getElement(document.getDocumentElement(), fiducialSetElementName);
        ArrayList<Element> datasetElements = XMLUtil.getElements(fiducialSetElement);
        if(datasetElements.size() != 2) {
            throw new RuntimeException("File should contain exactly 2 datasets");
        }
        Dataset dataset1 = readDataset(datasetElements.get(0));
        Dataset dataset2 = readDataset(datasetElements.get(1));
        if(datasetElements.get(0).getAttribute(datasetTypeAttributeName).equals("source")) {
            return new FiducialSet(dataset1, dataset2);
        }
        return new FiducialSet(dataset2, dataset1);
    }

    private Dataset readDataset(Element datasetElement) {
        Dataset result = new Dataset(Integer.valueOf(datasetElement.getAttribute(datasetDimensionAttributeName)));
        ArrayList<Element> pointElements = XMLUtil.getElements(datasetElement);
        for(Element pointElement : pointElements) {
            result.addPoint(readPoint(pointElement));
        }
        return result;
    }

    private Point readPoint(Element pointElement) {
        ArrayList<Element> coordinateElements = XMLUtil.getElements(pointElement);
        Point result = new Point(coordinateElements.size());
        for(Element coordinate : coordinateElements) {
            result.getmatrix().set(Integer.valueOf(coordinate.getAttribute(coordinateDimensionAttributeName)), 0, Double.valueOf(coordinate.getTextContent()));
        }
        return result;
    }
}
