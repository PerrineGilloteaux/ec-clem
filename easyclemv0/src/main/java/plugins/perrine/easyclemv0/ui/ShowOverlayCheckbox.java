package plugins.perrine.easyclemv0.ui;

import icy.painter.Overlay;
import plugins.perrine.easyclemv0.model.Workspace;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ShowOverlayCheckbox extends JCheckBox {

    private Workspace workspace;
    private Overlay overlay;

    public ShowOverlayCheckbox(Workspace workspace, Overlay overlay, String text, String tooltip) {
        super(text, false);
        this.workspace = workspace;
        this.overlay = overlay;
        setToolTipText(tooltip);
        addActionListener((arg0) -> action(arg0));
        setVisible(false);
    }

    public void setOverlay(Overlay overlay) {
        this.overlay = overlay;
    }

    private void action(ActionEvent arg0) {
        if (((JCheckBox) arg0.getSource()).isSelected()) {
            workspace.getSourceSequence().addOverlay(overlay);
        } else {
            workspace.getSourceSequence().removeOverlay(overlay);
        }
    }
}
