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

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;

import icy.sequence.Sequence;
import icy.type.point.Point5D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;


import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import plugins.kernel.roi.roi2d.plugin.ROI2DPointPlugin;
import plugins.perrine.easyclemv0.EasyCLEMv0;

import plugins.perrine.easyclemv0.TargetRegistrationErrorMap;



public class GuiCLEMButtons2 extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	EasyCLEMv0 matiteclasse;
	private monitorTargetOverlay myoverlaytarget;

	/**
	 * Create the panel.
	 */
	public GuiCLEMButtons2(EasyCLEMv0 matiteclasse) {
		this.matiteclasse=matiteclasse;
		
		
		JButton btnNewButton4 = new JButton("Compute the whole predicted error map ");
		btnNewButton4.setToolTipText(" This will compute a new image were each pixel value stands for the statistical registration error (called Target Registration Error");
		btnNewButton4.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				TargetRegistrationErrorMap myTREmap=new TargetRegistrationErrorMap();
				if (GuiCLEMButtons2.this.matiteclasse.source.getValue()!=null)
				{
					if (GuiCLEMButtons2.this.matiteclasse.source.getValue().getROIs().size()<3){
						new AnnounceFrame("Without at least 3 ROI points, the error map does not have any meaning. Please add points.",5);
					}
					else{
						double fle=GuiCLEMButtons2.this.matiteclasse.maxdifferrorinnm();
						if (fle==0){
							MessageDialog.showDialog("Please Initialize EC-Clem first by pressing the Play button");
							return;
						}
						myTREmap.apply(GuiCLEMButtons2.this.matiteclasse.source.getValue(),GuiCLEMButtons2.this.matiteclasse.source.getValue().getFirstImage(), fle);
					
					}
				}
				else
				{
					MessageDialog.showDialog("Source and target were closed. Please open one of them and try again");
				}
				}
			

			
		});
		JButton btnNewButton5 = new JButton("Monitor a target point ");
		btnNewButton5.setToolTipText(" This will display the evolution of the target registration error at one target position while points are added");
		btnNewButton5.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
					
				
					for (final PluginDescriptor pluginDescriptor : PluginLoader
							.getPlugins()) {
						
						if (pluginDescriptor.getSimpleClassName()
								.compareToIgnoreCase("MonitorTargetPoint") == 0) {
							selectpoint();
							PluginLauncher.start(pluginDescriptor);
							GuiCLEMButtons2.this.matiteclasse.monitor=true;
				}
					}
			

			
		}});
		
		JCheckBox showerror =new JCheckBox("Show Difference in Positions", false);
		showerror.setToolTipText("This will draw around each point on source image a red circle which radius is the difference between source point and target point positions (called Fiducial registration error)" );
		JCheckBox showpredictederror =new JCheckBox("Show Predicted Error in Positions ", false);
		showpredictederror.setToolTipText("This will draw around each point on source image an orange circle which radius is the predicted error from the actual point configuration (called Target registration error) " );
		showerror.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				
				if (((JCheckBox) arg0.getSource()).isSelected())
				{
					if (GuiCLEMButtons2.this.matiteclasse.source.getValue()==null)
					{
						GuiCLEMButtons2.this.matiteclasse.overlayerrorselected=true;
						return;
					}
					else{
						GuiCLEMButtons2.this.matiteclasse.source.getValue().addOverlay(GuiCLEMButtons2.this.matiteclasse.myoverlayerror);
					}
				}
				else
				{
					if (GuiCLEMButtons2.this.matiteclasse.source.getValue()==null)
					{
						GuiCLEMButtons2.this.matiteclasse.overlayerrorselected=false;
						return;
					}
					else{
						GuiCLEMButtons2.this.matiteclasse.source.getValue().removeOverlay(GuiCLEMButtons2.this.matiteclasse.myoverlayerror);
					}
				}
				
				
			}
		});
			
		showpredictederror.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent arg0) {
					
					if (((JCheckBox) arg0.getSource()).isSelected())
					{
						if (GuiCLEMButtons2.this.matiteclasse.source.getValue()==null)
						{
							GuiCLEMButtons2.this.matiteclasse.predictederrorselected=true;
							return;
						}
						else{
						GuiCLEMButtons2.this.matiteclasse.source.getValue().addOverlay(GuiCLEMButtons2.this.matiteclasse.myoverlaypredictederror);
						}
					}
					else
					{
						if (GuiCLEMButtons2.this.matiteclasse.source.getValue()==null)
						{
							GuiCLEMButtons2.this.matiteclasse.predictederrorselected=false;
							return;
						}
						else{
						GuiCLEMButtons2.this.matiteclasse.source.getValue().removeOverlay(GuiCLEMButtons2.this.matiteclasse.myoverlaypredictederror);
						}
					}
					
					
				}
			
		});
	
		add(showerror);
		
		add(showpredictederror);
		add(btnNewButton4);
		add(btnNewButton5);
		
		
		
	}
	protected void removespecificrigidbutton(){
		Component[] listcomp = this.getComponents();
		// we keep only the first component
		for (int c=1;c<listcomp.length;c++){
			Component mycomp = listcomp[c];
			mycomp.setVisible(false);
		}
	}
	protected void reshowspecificrigidbutton(){
		Component[] listcomp = this.getComponents();
		// we keep only the first component
		for (int c=1;c<listcomp.length;c++){
			Component mycomp = listcomp[c];
			mycomp.setVisible(true);
		}
	}
	protected void selectpoint() {
		GuiCLEMButtons2.this.matiteclasse.waitfortarget=true;
		this.myoverlaytarget = new monitorTargetOverlay();
		GuiCLEMButtons2.this.matiteclasse.target.getValue().addOverlay(this.myoverlaytarget);
		GuiCLEMButtons2.this.matiteclasse.source.getValue().addOverlay(this.myoverlaytarget);
		
	}
	private class monitorTargetOverlay extends Overlay {
		
		boolean notplacedyet=true;
		public monitorTargetOverlay() {
			super("Target point where to monitor accuracy");
		}
		public void mouseMove(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            if (notplacedyet){
			// check if we are dealing with a 2D canvas
            if ((canvas instanceof IcyCanvas2D) && (imagePoint != null))
            {
                // update mouse position
            	GuiCLEMButtons2.this.matiteclasse.xtarget = (int) imagePoint.getX();
            	GuiCLEMButtons2.this.matiteclasse.ytarget = (int) imagePoint.getY();
 
            	Icy.getMainInterface().setSelectedTool("none");
 
                // notify painter changed as dragging property and coordinates may be changed
                painterChanged();
            }
        }
        }
		@Override
        public void mouseClick(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
			
			if (notplacedyet){
			// check if we are dealing with a 2D canvas and we have a valid image position
            if ((canvas instanceof IcyCanvas2D) && (imagePoint != null))
            {
                
            	
            	
            	GuiCLEMButtons2.this.matiteclasse.xtarget = (int) imagePoint.getX();
            	GuiCLEMButtons2.this.matiteclasse.ytarget = (int) imagePoint.getY();
               painterChanged();
                notplacedyet=false;
                GuiCLEMButtons2.this.matiteclasse.waitfortarget=false;
                if (canvas.getSequence().getName()==GuiCLEMButtons2.this.matiteclasse.target.getValue().getName()){
                	GuiCLEMButtons2.this.matiteclasse.source.getValue().removeOverlay(myoverlaytarget);
                }
                if (canvas.getSequence().getName()==GuiCLEMButtons2.this.matiteclasse.source.getValue().getName()){
                	GuiCLEMButtons2.this.matiteclasse.target.getValue().removeOverlay(myoverlaytarget);
                	GuiCLEMButtons2.this.matiteclasse.monitortargetonsource=true;
                }
                
            }
            
			}
        }
		@Override
		public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas) {
			// check if we are dealing with a 2D canvas and we have a valid
			// Graphics object
			if ((canvas instanceof IcyCanvas2D) && (g != null)) {
				
					int xm = GuiCLEMButtons2.this.matiteclasse.xtarget;
					int ym = GuiCLEMButtons2.this.matiteclasse.ytarget;
					g.setColor(Color.GREEN);
					g.setStroke(new BasicStroke(3));
					int diameter=Math.round(sequence.getWidth()/25);
					g.drawOval(xm-diameter/2, ym-diameter/2, (int) Math.round(diameter),
							(int) Math.round(diameter));
					// display the cross cursor
	                g.setStroke(new BasicStroke(1));
	                //g.setColor(Color.GREEN);
	                g.drawLine(xm-diameter/2 , ym -diameter/2, xm +diameter/2 , ym + diameter/2);
	                g.drawLine(xm-diameter/2 , ym + diameter/2, xm + diameter/2, ym - diameter/2);
	                //g.setStroke(new BasicStroke(1));
	               // g.setColor(Color.GREEN);
	                g.drawLine(xm -diameter/2, ym -diameter/2, xm + diameter/2, ym + diameter/2);
	                g.drawLine(xm-diameter/2, ym + diameter/2, xm + diameter/2, ym -diameter/2);
	                if (GuiCLEMButtons2.this.matiteclasse.waitfortarget==false)
	                	Icy.getMainInterface().setSelectedTool(ROI2DPointPlugin.class.getName());
	              
				}

			}
		}
	
}