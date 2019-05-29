/**
 * Copyright 2010-2017 Perrine Paul-Gilloteaux, CNRS.
 * Perrine.Paul-Gilloteaux@univ-nantes.fr
 * 
 * This file is part of EC-CLEM.
 * 
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 **/



/**
 * Author: Perrine.Paul-Gilloteaux@curie.fr
 * last set of button: dealing with error prediction. 
 * this will read the checkbox for fiducial localisation error and for predicted FRE,
 * as well as launching the computation of the error map.
 */

package plugins.perrine.easyclemv0;

import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import plugins.perrine.easyclemv0.error.FLEComputer;
import plugins.perrine.easyclemv0.error.TREComputer;
import plugins.perrine.easyclemv0.factory.DatasetFactory;
import plugins.perrine.easyclemv0.model.Workspace;
import plugins.perrine.easyclemv0.overlay.ErrorInPositionOverlay;
import plugins.perrine.easyclemv0.overlay.PredictedErrorInPositionOverlay;
import plugins.perrine.easyclemv0.ui.ComputeErrorMapButton;
import plugins.perrine.easyclemv0.ui.MonitorTargetPointButton;
import plugins.perrine.easyclemv0.ui.ShowOverlayCheckbox;

public class GuiCLEMButtons2 extends JPanel {

	private static final long serialVersionUID = 1L;

	private Workspace workspace;

	public GuiCLEMButtons2(Workspace workspace) {
		this.workspace = workspace;
		ComputeErrorMapButton computeErrorMapButton = new ComputeErrorMapButton(workspace);
		MonitorTargetPointButton btnNewButton5 = new MonitorTargetPointButton(workspace);
		JCheckBox showerror = new ShowOverlayCheckbox(
			workspace,
			new ErrorInPositionOverlay(workspace),
			"Show Difference in Positions",
			"This will draw around each point on source image a red circle which radius is the difference between source point and target point positions (called Fiducial registration error)"
		);
		JCheckBox showpredictederror = new ShowOverlayCheckbox(
			workspace,
			new PredictedErrorInPositionOverlay(workspace),
			"Show Predicted Error in Positions ",
			"This will draw around each point on source image an orange circle which radius is the predicted error from the actual point configuration (called Target registration error)"
		);
	
		add(showerror);
		add(showpredictederror);
		add(computeErrorMapButton);
		add(btnNewButton5);
	}

	protected void removespecificrigidbutton(){
		Component[] listcomp = this.getComponents();
		// we keep only the first component
		for (int c = 1; c < listcomp.length; c++){
			Component mycomp = listcomp[c];
			mycomp.setVisible(false);
		}
	}

	protected void reshowspecificrigidbutton(){
		Component[] listcomp = this.getComponents();
		// we keep only the first component
		for (int c = 1; c < listcomp.length; c++){
			Component mycomp = listcomp[c];
			mycomp.setVisible(true);
		}
	}
}