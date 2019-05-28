package plugins.perrine.easyclemv0.storage.xml;

import icy.util.XMLUtil;
import org.w3c.dom.Document;
import java.io.File;
import java.io.IOException;

public class XmlFileWriter {

    private Document document;
    private XmlFileReader xmlFileReader = new XmlFileReader();

    public Document loadFile(File XMLFile) {
        checkFileExists(XMLFile);
        document = xmlFileReader.loadFile(XMLFile);
        return document;
    }

    private void checkFileExists(File XMLFile) {
        if(!XMLFile.exists()) {
            try {
                XMLFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            document = XMLUtil.createDocument(true);
            write(XMLFile);
        }
    }

    public void write(File XMLFile) {
        XMLUtil.saveDocument(document, XMLFile);
    }

    public Document getDocument() {
        return document;
    }
}
