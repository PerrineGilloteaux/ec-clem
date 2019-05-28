package plugins.perrine.easyclemv0.ui;

import plugins.perrine.easyclemv0.model.WorkspaceTransformer;
import javax.swing.*;

public class UpdateTransformationButton extends JButton {

    private WorkspaceTransformer workspaceTransformer;

    public void setWorkspaceTransformer(WorkspaceTransformer workspaceTransformer) {
        this.workspaceTransformer = workspaceTransformer;
    }

    public UpdateTransformationButton() {
        super("Update Transformation");
        setToolTipText("Press this button if you have moved the points, prepared set of points, \n or obtained some black part of the image. This will refresh it");
        addActionListener((arg0) -> action());
    }

    private void action() {
//        if (
//					workspace.getSourceSequence() == null ||
//					workspace.getTargetSequence() == null
//				) {
//					MessageDialog.showDialog("Make sure source and target image are openned and selected");
//					return;
//				}
//
//				Dataset sourceDataset = datasetFactory.getFrom(workspace.getSourceSequence());
//				Dataset targetDataset = datasetFactory.getFrom(workspace.getTargetSequence());
//
//				if (sourceDataset.getN() != targetDataset.getN()) {
//					new AnnounceFrame("Warning: not the same number of point on both image. Nothing done",5);
//					Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
//					return;
//				}

        workspaceTransformer.run();

//				if (!workspace.isNonRigid()) {
//					workspaceTransformer.run();
//				} else {
//					ThreadUtil.bgRun(new Runnable(){
//						@Override
//						public void run() {
//							NonRigidTranformationVTK nrtransfo = new NonRigidTranformationVTK();
//							nrtransfo.setImageSourceandpoints(
//								GuiCLEMButtons.this.matiteclasse.checkgrid,
//								GuiCLEMButtons.this.matiteclasse.source.getValue(),
//								GuiCLEMButtons.this.matiteclasse.getRoiProcessor().getPointsFromRoi(GuiCLEMButtons.this.matiteclasse.source.getValue().getROIs())
//							);
//							nrtransfo.setImageTargetandpoints(
//								GuiCLEMButtons.this.matiteclasse.target.getValue(),
//								GuiCLEMButtons.this.matiteclasse.getRoiProcessor().getPointsFromRoi(GuiCLEMButtons.this.matiteclasse.target.getValue().getROIs())
//							);
//							nrtransfo.run();
//							GuiCLEMButtons.this.matiteclasse.updateRoi();
//						}
//					});
//				}
    }
}
