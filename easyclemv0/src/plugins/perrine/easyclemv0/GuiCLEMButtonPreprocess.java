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
 * one set of button: this one is to call the apply transform plugin 
 * and rename correctly the file in the Easyclem workflow
 */
package plugins.perrine.easyclemv0;

import icy.gui.dialog.MessageDialog;

import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;

import javax.swing.JPanel;

import plugins.perrine.easyclemv0.EasyCLEMv0;

public class GuiCLEMButtonPreprocess extends JPanel {

	private static final long serialVersionUID = 1L;
	EasyCLEMv0 matiteclasse;

	/**
	 * Create the panel.
	 */
	public GuiCLEMButtonPreprocess(EasyCLEMv0 matiteclasse) {
		this.matiteclasse = matiteclasse;

		JButton btnNewButton = new JButton(
				"I want to preprocess my data");

		btnNewButton
				.setToolTipText("Do it before computing the transform: it can help by reducing the dimensionality with flattening for exemple. You will then still be able to apply the transformation computed to the full stack/movie in a second run.");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {

				if (GuiCLEMButtonPreprocess.this.matiteclasse.source.getValue() != null) {
					for (final PluginDescriptor pluginDescriptor : PluginLoader
								.getPlugins()) {
							// System.out.print(pluginDescriptor.getSimpleClassName());
							// // output the name of the
							// class.

							// This part of the example check for a match in the
							// name of the class
							if (pluginDescriptor.getSimpleClassName()
									.compareToIgnoreCase("Preprocess3Dstackto2D") == 0) {
								// System.out.print(" ==> Starting by looking at the name....");

								// Create a new Runnable which contain the proper
								// launcher

								PluginLauncher.start(pluginDescriptor);
					
								

							}
						}
					} else {
						MessageDialog
								.showDialog("Source was closed. Please open one and try again");
					}
			}

	
			});
		add(btnNewButton);
		
		//add(tooltip);

	}
}