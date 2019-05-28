package plugins.perrine.easyclemv0.storage;

import Jama.Matrix;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import plugins.perrine.easyclemv0.storage.xml.XmlFileReader;
import plugins.perrine.easyclemv0.storage.xml.XmlFileWriter;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlWriter;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlFileWriterTest {
    private File file = new File("XmlFileWriterTest.xml");
    private XmlFileWriter xmlFileWriter = new XmlFileWriter();
    private RigidTransformationXmlWriter xmlWriter;
    private XmlFileReader xmlFileReader = new XmlFileReader();
    private RigidTransformationXmlReader xmlReader = new RigidTransformationXmlReader();

    @BeforeEach
    @AfterEach
    private void clearFile() {
        file.delete();
    }

    @Test
    void writeAndRead() {
        Matrix M = new Matrix(new double[][] {{ 1 }, { 2 }, { 3 }});
        write(M);
        Matrix read = read();
        assertEquals(M.getRowDimension(), read.getRowDimension());
        assertEquals(M.getColumnDimension(), read.getColumnDimension());
    }

    private void write(Matrix M) {
        Document document = xmlFileWriter.loadFile(file);
        xmlWriter = new RigidTransformationXmlWriter(document);
        xmlWriter.write(M, 0);
        xmlFileWriter.write(file);
    }

    private Matrix read() {
        Document document = xmlFileReader.loadFile(file);
        return xmlReader.read(document);
    }
}
