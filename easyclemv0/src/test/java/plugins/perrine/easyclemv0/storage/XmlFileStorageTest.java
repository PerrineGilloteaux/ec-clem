package plugins.perrine.easyclemv0.storage;

import Jama.Matrix;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlFileStorageTest {
    private XmlFileStorage subjectUnderTest;
    private File file = new File("XmlFileStorageTest.xml");

    @BeforeEach
    @AfterEach
    private void clearFile() {
        file.delete();
    }

    @Test
    void writeAndRead() {
        subjectUnderTest = new XmlFileStorage(file);

        Matrix M = new Matrix(new double[][] {{ 1 }, { 2 }, { 3 }});
        subjectUnderTest.write(M, 0);
        Matrix read = subjectUnderTest.read();
        assertEquals(M.getRowDimension(), read.getRowDimension());
        assertEquals(M.getColumnDimension(), read.getColumnDimension());
    }
}
