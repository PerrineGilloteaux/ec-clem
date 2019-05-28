package plugins.perrine.easyclemv0.storage.xml.rigid;

import Jama.Matrix;
import icy.sequence.Sequence;
import icy.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.perrine.easyclemv0.model.DimensionSize;
import plugins.perrine.easyclemv0.model.SequenceSize;
import plugins.perrine.easyclemv0.util.SequenceUtil;
import java.util.ArrayList;
import java.util.Date;
import static plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXml.*;

public class RigidTransformationXmlWriter {

    private Document document;

    public RigidTransformationXmlWriter(Document document) {
        this.document = document;
        document.getDocumentElement().setAttribute("type", "rigid");
    }

    public void write(Matrix M, int order) {
        Element element = XMLUtil.addElement(document.getDocumentElement(), transformationElement);
        XMLUtil.setAttributeValue(element, processDateAttribute, new Date().toString());
        XMLUtil.setAttributeIntValue(element, orderAttribute, order);
        XMLUtil.setAttributeIntValue(element, nrowAttribute, M.getRowDimension());
        XMLUtil.setAttributeIntValue(element, ncolAttribute, M.getColumnDimension());
        for(int i = 0; i < M.getRowDimension(); i++) {
            for(int j = 0; j < M.getColumnDimension(); j++) {
                Element value = document.createElement(valueElement);
                value.setAttribute(rowAttribute, String.valueOf(i));
                value.setAttribute(colAttribute, String.valueOf(j));
                value.setTextContent(String.valueOf(M.get(i, j)));
                element.appendChild(value);
            }
        }
    }

    public void writeSizeOf(Sequence sequence) {
        Element element = XMLUtil.addElement(document.getDocumentElement(), imageSizeElement);
        SequenceSize dimension = SequenceUtil.getUsedDimensions(sequence);
        XMLUtil.setAttributeValue(element, imageDimension, String.valueOf(dimension.getN()));
        for(DimensionSize entry : dimension.getDimensions()) {
            Element value = document.createElement(dimensionSizeElement);
            value.setAttribute(imageDimensionName, entry.getDimensionId().name());
            value.setAttribute(dimensionpixelSize, String.valueOf(entry.getPixelSizeInNanometer()));
            value.setTextContent(String.valueOf(entry.getSize()));
            element.appendChild(value);
        }
    }

    public void removeLastTransformationElement() {
        Element root = XMLUtil.getRootElement(document);
        ArrayList<Element> transfoElementArrayList = XMLUtil.getElements(root, transformationElement);

        int maxorder = 0;
        int order = 0;
        for (Element transfoElement : transfoElementArrayList) {
            order = XMLUtil.getAttributeIntValue(transfoElement, orderAttribute, 0);
            if (maxorder < order)
                maxorder = order;
        }

        for (Element transfoElement : transfoElementArrayList) {
            order = XMLUtil.getAttributeIntValue(transfoElement, orderAttribute, 0);
            if (maxorder == order){
                Node parent = transfoElement.getParentNode();
                parent.removeChild(transfoElement);
                parent.normalize();
            }
        }
    }
}
