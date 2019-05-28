package plugins.perrine.easyclemv0.storage.xml.rigid;

import Jama.Matrix;
import icy.sequence.DimensionId;
import icy.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import plugins.perrine.easyclemv0.model.DimensionSize;
import plugins.perrine.easyclemv0.model.SequenceSize;
import java.util.ArrayList;
import static plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXml.*;

public class RigidTransformationXmlReader {

    public Matrix read(Document document) {
        Element root = XMLUtil.getRootElement(document);
        ArrayList<Element> elements = XMLUtil.getElements(root, transformationElement);
        ArrayList<Matrix> transformationList = new ArrayList<>();
        for (Element element : elements) {
            Matrix M = new Matrix(
                Integer.parseInt(element.getAttribute(nrowAttribute)),
                Integer.parseInt(element.getAttribute(ncolAttribute))
            );
            ArrayList<Element> values = XMLUtil.getElements(element, valueElement);
            for(Element value : values) {
                M.set(
                    Integer.parseInt(value.getAttribute(rowAttribute)),
                    Integer.parseInt(value.getAttribute(colAttribute)),
                    Double.parseDouble(value.getTextContent())
                );
            }
            transformationList.add(M);
        }

        if(transformationList.size() == 0) {
            return null;
        }

        Matrix combined = transformationList.remove(0);
        while(!transformationList.isEmpty()) {
            combined = combined.times(transformationList.remove(0));
        }

        return combined;
    }
}
