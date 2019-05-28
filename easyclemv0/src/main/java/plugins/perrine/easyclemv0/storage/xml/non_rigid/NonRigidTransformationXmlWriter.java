package plugins.perrine.easyclemv0.storage.xml.non_rigid;

import icy.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.model.FiducialSet;
import plugins.perrine.easyclemv0.model.Point;
import java.util.Date;

import static plugins.perrine.easyclemv0.storage.xml.non_rigid.NonRigidTransformationXml.*;

public class NonRigidTransformationXmlWriter {

    private Document document;

    public NonRigidTransformationXmlWriter(Document document) {
        this.document = document;
        document.getDocumentElement().setAttribute("type", "nonrigid");
    }

    public void write(FiducialSet fiducialSet) {
//        String name = imagesource.getFilename() + "_NONRIGIDtransfo.xml";
        Element fiducialSetElement = XMLUtil.addElement(document.getDocumentElement(), fiducialSetElementName);

        Element sourceDatasetElement = XMLUtil.addElement(fiducialSetElement, datasetElementName);
        sourceDatasetElement.setAttribute(datasetTypeAttributeName, "source");
        write(fiducialSet.getSourceDataset(), sourceDatasetElement);

        Element targetDatasetElement = XMLUtil.addElement(fiducialSetElement, datasetElementName);
        sourceDatasetElement.setAttribute(datasetTypeAttributeName, "target");
        write(fiducialSet.getTargetDataset(), targetDatasetElement);
    }

    private void write(Dataset dataset, Element datasetElement) {
        datasetElement.setAttribute(datasetNAttributeName, String.valueOf(dataset.getN()));
        datasetElement.setAttribute(datasetDimensionAttributeName, String.valueOf(dataset.getDimension()));
        for(int i = 0; i < dataset.getN(); i++) {
            Element point = XMLUtil.addElement(datasetElement, pointElementName);
            write(dataset.getPoint(i), point);
        }
    }

    private void write(Point point, Element pointElement) {
        for(int i = 0; i < point.getDimension(); i++) {
            Element coordinate = XMLUtil.addElement(pointElement, coordinateElementName);
            coordinate.setAttribute(coordinateDimensionAttributeName, String.valueOf(i));
            coordinate.setTextContent(String.valueOf(point.getmatrix().get(i, 0)));
        }
    }

    public void writeTransformationElements(int n, int extentX, int extentY, int extentZ, double spacingX, double spacingY, double spacingZ, double inputSpacingX, double inputSpacingY, double inputSpacingZ) {
//        String name = imagesource.getFilename() + "_NONRIGIDtransfo.xml";
        Element transfoElement = XMLUtil.addElement(document.getDocumentElement(), transformationElementsElement);
        XMLUtil.setAttributeIntValue(transfoElement, extentxAttribute, extentX);
        XMLUtil.setAttributeIntValue(transfoElement, extentyAttribute, extentY);
        XMLUtil.setAttributeIntValue(transfoElement, extentzAttribute, extentZ);
        XMLUtil.setAttributeDoubleValue(transfoElement, spacingxAttribute , spacingX );
        XMLUtil.setAttributeDoubleValue(transfoElement, spacingyAttribute , spacingY );
        XMLUtil.setAttributeDoubleValue(transfoElement, spacingzAttribute , spacingZ  );
        XMLUtil.setAttributeDoubleValue(transfoElement, inputSpacingxAttribute , inputSpacingX );
        XMLUtil.setAttributeDoubleValue(transfoElement, inputSpacingyAttribute , inputSpacingY );
        XMLUtil.setAttributeDoubleValue(transfoElement, inputSpacingzAttribute , inputSpacingZ  );
        XMLUtil.setAttributeIntValue(transfoElement, nPointsAttribute , n);
        XMLUtil.setAttributeValue(transfoElement, processDateAttribute, new Date().toString());
    }
}
