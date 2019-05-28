package plugins.perrine.easyclemv0.ui;

import Jama.Matrix;
import com.google.common.io.Files;
import icy.gui.dialog.MessageDialog;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.util.XMLUtil;
import org.w3c.dom.Document;
import plugins.perrine.easyclemv0.factory.DatasetFactory;
import plugins.perrine.easyclemv0.model.Dataset;
import plugins.perrine.easyclemv0.model.DatasetTransformer;
import plugins.perrine.easyclemv0.model.Workspace;
import plugins.perrine.easyclemv0.roi.RoiUpdater;
import plugins.perrine.easyclemv0.storage.xml.XmlFileReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlReader;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlWriter;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ShowPointsButton extends JButton {

    private Workspace workspace;

    private DatasetFactory datasetFactory;
    private XmlFileReader xmlFileReader = new XmlFileReader();
    private RigidTransformationXmlReader xmlReader;
    private RigidTransformationXmlWriter xmlWriter;
    private DatasetTransformer datasetTransformer;
    private RoiUpdater roiUpdater;

    public ShowPointsButton() {
        super("Show ROIs on original source image");
        setToolTipText("Show the original source Image, with the points selected shown (save the source image to save the ROIs)");
        addActionListener((arg0) -> action());
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    private void action() {
        if (workspace.getSourceSequence() == null || workspace.getTargetSequence() == null) {
            MessageDialog.showDialog("Make sure source and target image are openned and selected");
            return;
        }

        boolean sorted = true;
        List<ROI> listRoisource = workspace.getSourceSequence().getROIs(sorted);
        if (listRoisource.size() > 0) {
            workspace.getSourceSequence().beginUpdate();
            workspace.getSourceSequence().removeAllImages();
            if (workspace.getSourceBackup() == null) {
                MessageDialog.showDialog("argh.");
                return;
            }

            try {
                for (int t = 0; t < workspace.getSourceBackup().getSizeT(); t++) {
                    for (int z = 0; z < workspace.getSourceBackup().getSizeZ(); z++) {
                        workspace.getSourceSequence().setImage(t, z,
                            workspace.getSourceBackup().getImage(t, z)
                        );
                    }
                }
            } finally {
                workspace.getSourceSequence().endUpdate();
            }

            Dataset sourceDataset = datasetFactory.getFrom(workspace.getSourceSequence());
            Matrix transformationMatrix = xmlReader.read(xmlFileReader.loadFile(workspace.getXMLFile()));
            Dataset transformedSourceDataset = datasetTransformer.apply(sourceDataset, transformationMatrix.inverse());
            roiUpdater.updateRoi(transformedSourceDataset, workspace.getSourceSequence());

            // Reinitialize XML FILE
            //AND CREATE A COPY of the former one for back up with the date
            String fileName = workspace.getXMLFile().getPath() +
                "_" + java.time.LocalDateTime.now().getDayOfMonth() +
                "_" + java.time.LocalDateTime.now().getMonth() +
                "_" + java.time.LocalDateTime.now().getHour() +
                "_" + java.time.LocalDateTime.now().getMinute() +
                "_backup.xml";
            File dest = new File(fileName);
            System.out.println("A back up of your transfo has been saved as" + fileName);

            try {
                Files.copy(workspace.getXMLFile(), dest);
            } catch (IOException e) {
                e.printStackTrace();
            }

            xmlWriter = new RigidTransformationXmlWriter(xmlFileReader.loadFile(workspace.getXMLFile()));
            xmlWriter.writeSizeOf(workspace.getTargetSequence());

            saveRois(workspace.getSourceSequence());
            saveRois(workspace.getTargetSequence());
        }
    }

    private void saveRois(Sequence sequence) {
        final List<ROI> rois = sequence.getROIs();
        if (rois.size() > 0) {
            final Document doc = XMLUtil.createDocument(true);
            if (doc != null) {
                ROI.saveROIsToXML(XMLUtil.getRootElement(doc), rois);
                System.out.println("ROIS saved before in "+ sequence.getFilename()+"_ROIsavedwhenshowonoriginaldata.xml"+"\n Use Load Roi(s) if needed in ROI top menu" );
                XMLUtil.saveDocument(doc, sequence.getFilename()+"_ROIsavedwhenshowonoriginaldata.xml");
            }
        }
    }
}
