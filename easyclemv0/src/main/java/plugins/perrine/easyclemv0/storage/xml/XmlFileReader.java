package plugins.perrine.easyclemv0.storage.xml;

import icy.util.XMLUtil;
import org.w3c.dom.Document;
import java.io.File;

public class XmlFileReader {

    public Document loadFile(File XMLFile) {
        return XMLUtil.loadDocument(XMLFile);
    }

    public boolean isRigid(Document document) {
        String type = document.getDocumentElement().getAttribute("type");
        return type.equals("rigid");
    }
}
