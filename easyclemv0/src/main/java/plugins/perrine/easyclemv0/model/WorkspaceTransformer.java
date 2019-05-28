package plugins.perrine.easyclemv0.model;

import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.roi.ROI;
import icy.sequence.Sequence;
import icy.sequence.SequenceListener;
import plugins.kernel.roi.roi2d.plugin.ROI2DPointPlugin;
import plugins.perrine.easyclemv0.factory.DatasetFactory;
import plugins.perrine.easyclemv0.factory.TREComputerFactory;
import plugins.perrine.easyclemv0.image_transformer.RigidImageTransformerInterface;
import plugins.perrine.easyclemv0.monitor.MonitorTargetPoint;
import plugins.perrine.easyclemv0.error.TREComputer;
import plugins.perrine.easyclemv0.image_transformer.ImageTransformerFactory;
import plugins.perrine.easyclemv0.monitor.MonitoringConfiguration;
import plugins.perrine.easyclemv0.registration.TransformationComputer;
import plugins.perrine.easyclemv0.roi.RoiUpdater;
import plugins.perrine.easyclemv0.storage.xml.SequenceSizeXmlReader;
import plugins.perrine.easyclemv0.storage.xml.XmlFileWriter;
import plugins.perrine.easyclemv0.storage.xml.rigid.RigidTransformationXmlWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkspaceTransformer {

    private Sequence sourceSequence;
    private Sequence sourceBackup;
    private Sequence targetSequence;
    private boolean stopFlag;
    private boolean pause;
    private MonitoringConfiguration monitoringConfiguration;

    private RoiUpdater roiUpdater = new RoiUpdater();
    private DatasetFactory datasetFactory = new DatasetFactory();
    private TransformationComputer transformationComputer;
    private XmlFileWriter xmlFileWriter = new XmlFileWriter();
    private SequenceSizeXmlReader sequenceSizeXmlReader = new SequenceSizeXmlReader();
    private RigidTransformationXmlWriter xmlWriter;
    private ImageTransformerFactory imageTransformerFactory = new ImageTransformerFactory();
    private TREComputerFactory treComputerFactory = new TREComputerFactory();

    private List<Integer> listofNvalues = new ArrayList<>();
    private List<Double> listoftrevalues = new ArrayList<>();

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public WorkspaceTransformer(Workspace workspace) {
        this.sourceSequence = workspace.getSourceSequence();
        this.sourceBackup = workspace.getSourceBackup();
        this.targetSequence = workspace.getTargetSequence();
        transformationComputer = new TransformationComputer();

        xmlWriter = new RigidTransformationXmlWriter(
            xmlFileWriter.loadFile(workspace.getXMLFile())
        );

        this.stopFlag = workspace.getWorkspaceState().isStopFlag();
        this.pause = workspace.getWorkspaceState().isPause();
        this.monitoringConfiguration = workspace.getMonitoringConfiguration();
        xmlWriter.writeSizeOf(workspace.getTargetSequence());
    }

    private SequenceListener[] removeListeners(Sequence sequence) {
        SequenceListener[] listeners = sequence.getListeners();
        for(SequenceListener listener : listeners) {
            sequence.removeListener(listener);
        }
        return listeners;
    }

    private void addListeners(Sequence sequence, SequenceListener[] listeners) {
        for(SequenceListener listener : listeners) {
            sequence.addListener(listener);
        }
    }

    public void run() {
        if (stopFlag) {
            return;
        }
        executorService.submit(() -> {
            SequenceListener[] sourceSequenceListeners = removeListeners(sourceSequence);
            SequenceListener[] targetSequenceListeners = removeListeners(targetSequence);
//
//        convertAllROI(sourceSequence);
//        convertAllROI(targetSequence);
//
//        double[][] sourcePointsFromRoi = roiProcessor.getPointsFromRoi(sourceSequence.getROIs());
//        double[][] targetPointsFromRoi = roiProcessor.getPointsFromRoi(targetSequence.getROIs());

//        if (sourcePointsFromRoi.length != targetPointsFromRoi.length) {
//            boolean removed = checkRoiNames(sourceSequence) || checkRoiNames(targetSequence);
//            sourcePointsFromRoi = roiProcessor.getPointsFromRoi(sourceSequence.getROIs());
//            targetPointsFromRoi = roiProcessor.getPointsFromRoi(targetSequence.getROIs());
//            if (removed) {
//                new AnnounceFrame("All points named Point2D or Point3D and likely not added by you have been removed. Re click now on \"apply transform\"");
//            }
//            if (sourcePointsFromRoi.length != targetPointsFromRoi.length) {
//                MessageDialog.showDialog("Number of points", "The number of points of ROI in source and target image are different. \n Check your ROI points and update transfo ");
//            }
//            Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
//            return;
//        }

            Dataset sourceDataset = datasetFactory.getFrom(sourceSequence);
            Dataset targetDataset = datasetFactory.getFrom(targetSequence);

            if(sourceDataset.getN() != targetDataset.getN()) {
                MessageDialog.showDialog("Number of points", "The number of points of ROI in source and target image are different. \n Check your ROI points and update transfo ");
                throw new RuntimeException("The number of points of ROI in source and target image are different");
            }

//        int z = sourceSequence.getFirstViewer().getPositionZ();
//        ROI roi = sourceSequence.getROIs().get(sourceSequence.getROIs().size() - 1);
//        if (roi != null) {
//            Point5D pos = roi.getPosition5D();
//            pos.setZ(z);
//            roi.setPosition5D(pos);
//            if (!pause) {
//                ComputeTransfo(sourceDataset, targetDataset);
//            } else {
//                new AnnounceFrame("You are in pause mode, click on update transfo", 3);
//                Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
//            }
//        }

            if (!pause) {
                ComputeTransfo(sourceDataset, targetDataset);
            } else {
                new AnnounceFrame("You are in pause mode, click on update transfo", 3);
                Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
            }

            addListeners(sourceSequence, sourceSequenceListeners);
            addListeners(targetSequence, targetSequenceListeners);
        });
    }

    private boolean checkRoiNames(Sequence sequence) {
        boolean removed = false;
        ArrayList<ROI> listroi = sequence.getROIs();
        for (ROI roi : listroi) {
            if (roi.getName().contains("Point2D")) {
                sequence.removeROI(roi);
                removed = true;
            }
            if (roi.getName().contains("Point3D")) {
                sequence.removeROI(roi);
                removed = true;
            }
        }
        return removed;
    }

    private void ComputeTransfo(Dataset sourceDataset, Dataset targetDataset) {
        if (sourceDataset.getN() <= sourceDataset.getDimension()) {
            System.out.println("One more point");
            new AnnounceFrame("No transformation will be computed with less than " + sourceDataset.getN() + 1 + " points. You have placed " + sourceDataset.getN() + " points", 2);
            return;
        }

        if (sourceDataset.isCoplanar() || targetDataset.isCoplanar() || sourceDataset.getN() < 4) {
            System.out.println("Instability: One more point");
            new AnnounceFrame("The position of the points does not allow a correct 3D transform. \n You need at least 2 points in separate z (slice). \n You may want to consider a 2D transform (it will still transform the full stack).");
            return;
        }

        if (sourceBackup == null) {
            MessageDialog.showDialog("Please press the Play button to initialize process first");
            return;
        }

//        restoreBackup(sourceSequence, sourceBackup);
        Similarity similarity = transformationComputer.compute(sourceDataset, targetDataset);
        xmlWriter.write(similarity.getMatrix(), sourceDataset.getN());


        RigidImageTransformerInterface imageTransformer = imageTransformerFactory.createImageTransformer(sourceDataset.getDimension());
        imageTransformer.setSourceSequence(sourceSequence);
        imageTransformer.setTargetSize(sequenceSizeXmlReader.readSize(xmlFileWriter.getDocument()));
        imageTransformer.setParameters(similarity.getMatrix());
        imageTransformer.run();

        Dataset sourceTransformedDataset = similarity.apply(sourceDataset);
        roiUpdater.updateRoi(sourceTransformedDataset, sourceSequence);

        if (monitoringConfiguration.isMonitor()) {
//            TargetRegistrationErrorMap ComputeFRE = new TargetRegistrationErrorMap();
//            ComputeFRE.setSequence(targetSequence);
            TREComputer treComputer = treComputerFactory.getFrom(sourceDataset, targetDataset);
//            double FLEmax = fleComputer.maxdifferrorinnm(sourceDataset, targetDataset, sourceSequence.getPixelSizeX(), targetSequence.getPixelSizeX());

//            System.out.println("Max localization error FLE estimated " + FLEmax + " nm");
//            if (monitorTargetOnSource) { // in that case we need to update the position of target
//                monitoringPoint = similarity.apply(monitoringPoint);
//            }

            listofNvalues.add(listofNvalues.size(), targetDataset.getN());
            listoftrevalues.add(
                listoftrevalues.size(),
                treComputer.getExpectedSquareTRE(monitoringConfiguration.getMonitoringPoint())
            );

            double[][] TREValues = new double[listofNvalues.size()][2];

            for (int i = 0; i < listofNvalues.size(); i++) {
                TREValues[i][0] = listofNvalues.get(i);
                TREValues[i][1] = listoftrevalues.get(i);
                System.out.println("N=" + TREValues[i][0] + ", TRE=" + TREValues[i][1]);
            }
            MonitorTargetPoint.UpdatePoint(TREValues);
        }

        sourceSequence.getFirstViewer().getLutViewer().setAutoBound(false);
        new AnnounceFrame("Transformation Updated", 5);
    }

    private void restoreBackup(Sequence sequence, Sequence backup) {
        sequence.setAutoUpdateChannelBounds(false);
        sequence.beginUpdate();
        sequence.removeAllImages();
        try {
            for (int t = 0; t < backup.getSizeT(); t++) {
                for (int z = 0; z < backup.getSizeZ(); z++) {
                    sequence.setImage(t, z, backup.getImage(t, z));
                }
            }
        }
        finally {
            sequence.endUpdate();
        }
    }
}
