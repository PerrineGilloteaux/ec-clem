package plugins.perrine.easyclemv0.storage;

import Jama.Matrix;
import icy.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class XmlFileStorage {
    private static String transformationElement = "MatrixTransformation";
    private static String processDateAttribute = "processDate";
    private static String orderAttribute = "order";
    private static String nrowAttribute = "nrow";
    private static String ncolAttribute = "ncol";
    private static String valueElement = "value";
    private static String rowAttribute = "row";
    private static String colAttribute = "col";
    private File XMLFile;

    public XmlFileStorage(File XMLFile) {
        this.XMLFile = XMLFile;
    }

    public void write(Matrix M, int order) {
        Document document = loadFile(XMLFile);
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
        XMLUtil.saveDocument(document, XMLFile);
    }

    public Matrix read() {
        Document document = loadFile(XMLFile);
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

    private void checkFileExists() {
        if(!XMLFile.exists()) {
            try {
                XMLFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Document document = XMLUtil.createDocument(true);
            XMLUtil.saveDocument(document, XMLFile);
        }
    }

    private Document loadFile(File XMLFile) {
        checkFileExists();
        return XMLUtil.loadDocument(XMLFile);
    }
}
