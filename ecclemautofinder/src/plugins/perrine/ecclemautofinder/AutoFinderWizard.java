/**
 * Copyright 2010-2017 Perrine Paul-Gilloteaux, CNRS.
 * Perrine.Paul-Gilloteaux@univ-nantes.fr
 * 
 * This file is part of EC-CLEM AUTOFINDER.
 * 
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 **/


package plugins.perrine.ecclemautofinder;


import java.awt.Dimension;


import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

import javax.swing.JRadioButton;

import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ToolTipFrame;
import icy.gui.util.GuiUtil;

import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;

import plugins.adufour.ezplug.EzPlug;

public class AutoFinderWizard extends EzPlug{
	JRadioButton case1 = new JRadioButton("coucou 2D or 3D,use segmented objects (cells,nuclei, vessels..) to find the corresponding area of EM in FM");
	JRadioButton case2 = new JRadioButton("2D or 3D, use segmented objects (cells, nuclei, vessels..) with similar content in EM and FM");
	JRadioButton case3 = new JRadioButton("2D or 3D, use spot detection (q-dots,beads, melanosomes....) to find the corresponding area of EM in FM");
	JRadioButton case4 = new JRadioButton("2D or 3D, use spot detection (q-dots,beads, melanosomes....) with similar content in EM and FM");
	public AutoFinderWizard() {
	System.out.println("and then");
}

	private JLabel getIcon( String name )
	{
		ImageIcon icon =  new ImageIcon(this.getClass().getResource(name));
		//ImageIcon icon = ResourceUtil.getImageIcon( ImageUtil.load(  new File(name)  ) );		
		JLabel label = new JLabel( icon );
		label.setSize( 128 , 128 );
		label.setPreferredSize( new Dimension( 128, 128) );
		
		return label;
	}

	@Override
	public void clean() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void execute() {
		// TODO Auto-generated method stub
		//check the number of selection,should be only one
		int count=0;
		if(case1.isSelected())
			count++;
		if(case2.isSelected())
			count++;
		if(case3.isSelected())
			count++;
		if(case4.isSelected())
			count++;
		if (count!=1){
			new AnnounceFrame("Please select one and only one case");
			return;
		}
		
		if (case1.isSelected()||case2.isSelected()){
			// if nuclei-like, launch binary slection with a message: launch on 2 segmented source and target image
			for (final PluginDescriptor pluginDescriptor : PluginLoader
					.getPlugins()) {

				if (pluginDescriptor.getSimpleClassName()
						.compareToIgnoreCase("ConvertBinarytoPointRoi") == 0) {
					// System.out.print(" ==> Starting by looking at the name....");

					// Create a new Runnable which contain the proper
					// launcher

					PluginLauncher.start(pluginDescriptor);
					new ToolTipFrame(    			
							"<html>"+
									"<br> Use this plugin to create point on"+
									"<br> the perimeter or surface of your segmented objects"+
									"</html>"
							);
				}
			}
		}
		if (case3.isSelected()||case4.isSelected()){
			//if spot-like, launch spot detector with a message (Export to ROI activated)
			for (final PluginDescriptor pluginDescriptor : PluginLoader
					.getPlugins()) {

				if (pluginDescriptor.getSimpleClassName()
						.compareToIgnoreCase("SpotDetector") == 0) {
					// System.out.print(" ==> Starting by looking at the name....");

					// Create a new Runnable which contain the proper
					// launcher

					PluginLauncher.start(pluginDescriptor);
					new ToolTipFrame(    			
							"<html>"+
									"<br> Detect spot in both source and target image."+
									"<br> Do not forget to select <b>Export to Roi</b> "+
									"<br> in the output tab of <b>Spot Detector</b> "+
									"</html>"
							);
					

				}
			}
		}
		
		
		// if same content: lanch autofinder with same content
		if (case2.isSelected()||case4.isSelected()){
		new ToolTipFrame(    			
				"<html>"+
						"<br> Select <b>About the same content in both nd Images</b> in AutoFinder"+
						"</html>"
				);
		}
		// if find small : launch and say to try both method
		if (case1.isSelected()||case3.isSelected()){
		new ToolTipFrame(    			
				"<html>"+
						"<br> Select <b>Find smaller part in big field of view</b> in AutoFinder"+
						"<br> Try both method1 and method 2 (it will depend of the"+
						"<br> rotation of your image, flipped or not, etc..."+
						"</html>"
				);
		}
	}

	@Override
	protected void initialize() {
		// TODO Auto-generated method stub
		addComponent( GuiUtil.createLineBoxPanel( 	
				Box.createHorizontalGlue(),
				getIcon("nucleiFM.png"),
				getIcon("nuclei.png"),
				Box.createHorizontalGlue(),
				getIcon("nucleifused.png"),
				
				Box.createHorizontalGlue()));
			addComponent( GuiUtil.createLineBoxPanel( 	
				Box.createHorizontalGlue(),
				case1,
				
				Box.createHorizontalGlue()));
			
			addComponent( GuiUtil.createLineBoxPanel( 	
					Box.createHorizontalGlue(),
					getIcon("nucleiFMsamecontent.png"),
					getIcon("nuclei.png"),
					Box.createHorizontalGlue(),
					getIcon("nucleisamecontentfused.png"),
					
					Box.createHorizontalGlue()));
				addComponent( GuiUtil.createLineBoxPanel( 	
					Box.createHorizontalGlue(),
					case2,
					
					Box.createHorizontalGlue()));
				
				addComponent( GuiUtil.createLineBoxPanel( 	
						Box.createHorizontalGlue(),
						getIcon("spotFM.png"),
						getIcon("spotEM.png"),
						Box.createHorizontalGlue(),
						getIcon("spotfused.png"),
						
						Box.createHorizontalGlue()));
					addComponent( GuiUtil.createLineBoxPanel( 	
						Box.createHorizontalGlue(),
						case3,
						
						Box.createHorizontalGlue()));
					
					addComponent( GuiUtil.createLineBoxPanel( 	
							Box.createHorizontalGlue(),
							getIcon("spotFMsamecontent.png"),
							getIcon("spotEM.png"),
							Box.createHorizontalGlue(),
							getIcon("spotsamecontentfused.png"),
							
							Box.createHorizontalGlue()));
						addComponent( GuiUtil.createLineBoxPanel( 	
							Box.createHorizontalGlue(),
							case4,
							
							Box.createHorizontalGlue()));
	}
}
