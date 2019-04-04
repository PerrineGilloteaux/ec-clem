/**
 * Copyright 2010-2017 Perrine Paul-Gilloteaux, CNRS.
 * Perrine.Paul-Gilloteaux@univ-nantes.fr
 * 
 * This file is part of EC-CLEM. NOT used but set for retrocompatibility purpose.
 * 
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 **/

package plugins.perrine.easyclemv0;



import icy.canvas.IcyCanvas;
import icy.roi.ROI;

import icy.sequence.Sequence;

import icy.type.point.Point5D;
import icy.type.point.Point5D.Double;
import icy.util.XMLUtil;
import icy.vtk.IcyVtkPanel;
import plugins.kernel.canvas.VtkCanvas;
import plugins.kernel.roi.roi2d.ROI2DPoint;

import vtk.vtkActor;
import vtk.vtkPolyDataMapper;
import vtk.vtkSphereSource;
import vtk.vtkTransform;


import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;


import org.w3c.dom.Node;


// adaptation from the class ROI2D
public  class myRoi3D extends ROI2DPoint
{
    
	
	public class myROI3DPointPainter extends ROI2DPointPainter
    {
		 vtkSphereSource vtkSource;
		
        @Override
        protected void finalize() throws Throwable
        {
            super.finalize();
 
            // init 3D painters stuff
            if (vtkSource != null)
                vtkSource.Delete();
        }
 
        @Override
        public void mouseMove(MouseEvent e, Double imagePoint, IcyCanvas canvas)
        {
            super.mouseMove(e, imagePoint, canvas);

            // special case: we want to set focus when we have control point selected
            if (hasSelectedPoint())
                setFocused(true);
            /*
           final VtkCanvas canvas2 = canvas3d.get();
            // canvas was closed
            if (canvas2 == null)
                return;
 
            final IcyVtkPanel vtkPanel = canvas2.getVtkPanel();
            // canvas was closed
            if (vtkPanel == null)
                return;
 
            final Sequence seq = canvas2.getSequence();
            // nothing to update
            if (seq == null)
                return;
            
            if (actor!=null){
            if (canvas2.getPickedObject()==actor){
            	actor.DragableOn();
            	System.out.println(actor.GetPosition()[0]);
            }
            }*/
        }
 
       
       /* private final Thread updateThread_ = new Thread() {
    		@Override
    		public void run() {
    			while (isAlive() && !this.isInterrupted()) {
    				screenUpdateLock.lock();
    				try {
    					try {
    						updateCondition.await();
    						actor.SetPosition(unsyncX * pixelSizeX, unsyncY
    								* pixelSizeY, unsyncZ * pixelSizeZ);
    						unsyncX = getX();
    						unsyncY = Anchor3D.this.getY();
    						unsyncZ = Anchor3D.this.getZ();
    					} catch (InterruptedException e) {
    						e.printStackTrace();
    					}
    				} finally {
    					screenUpdateLock.unlock();
    				}
    			}
    		}
    	};*/
      
 

        @Override
        protected void initVtkObjects()
        {
            // init 3D painters stuff
            vtkSource = new vtkSphereSource();
            vtkSource.SetRadius(getStroke());
            vtkSource.SetThetaResolution(12);
            vtkSource.SetPhiResolution(12);
           
           
            
            polyMapper = new vtkPolyDataMapper();
            polyMapper.SetInputConnection(vtkSource.GetOutputPort());
 
            actor = new vtkActor();
            actor.SetMapper(polyMapper);
 
            // initialize color
            final Color col = getColor();
            actor.GetProperty().SetColor(col.getRed() / 255d, col.getGreen() / 255d, col.getBlue() / 255d);
        }
 
        /**
         * update 3D painter for 3D canvas (called only when VTK is loaded).
         */
        @Override
        protected void rebuildVtkObjects()
        {
            final VtkCanvas canvas = canvas3d.get();
            // canvas was closed
            if (canvas == null)
                return;
 
            final IcyVtkPanel vtkPanel = canvas.getVtkPanel();
            // canvas was closed
            if (vtkPanel == null)
                return;
 
            final Sequence seq = canvas.getSequence();
            // nothing to update
            if (seq == null)
                return;
 
            final Point2D pos = getPoint();
            double curZ = getZd();
 
            // all slices ?
            if (curZ == -1)
                // set object at middle of the volume
                curZ = seq.getSizeZ() / 2d;
 
            // actor can be accessed in canvas3d for rendering so we need to synchronize access
            vtkPanel.lock();
            try
            {
                vtkSource.SetRadius(getStroke());
                vtkSource.SetCenter(pos.getX(), pos.getY(),
                		curZ*(scaling[2]/scaling[1])); // the fact the the icy canvas is not in real coordiante is a nightmare...
               
               
                // to deal with anisotropy and have nice spheres
                vtkTransform correctsphere=new vtkTransform();
                correctsphere.Scale(1, 1, scaling[1]/scaling[2]); 
                actor.SetUserTransform(correctsphere);
                //to get anisotropy
                /*vtkTransformPolyDataFilter tr=new vtkTransformPolyDataFilter();
                tr.SetInputData(vtkSource.GetOutput());
                tr.SetTransform(correctsphere);
                tr.Update();*/
                polyMapper.Update();
                //scaling[0]=1;
               // scaling[1]=1;
                //scaling[2]=this.getAttachedCanvas().get(0).getSequence().getPixelSizeX()/this.getAttachedCanvas().get(0).getSequence().getPixelSizeZ();
                actor.SetScale(scaling); //Done with transfo...
            }
            finally
            {
                vtkPanel.unlock();
            }
 
            // need to repaint
            painterChanged();
        }
 
        @Override
        protected void updateVtkDisplayProperties()
        {
            if (actor != null)
            {
                final VtkCanvas cnv = canvas3d.get();
                final Color col = getDisplayColor();
                final double r = col.getRed() / 255d;
                final double g = col.getGreen() / 255d;
                final double b = col.getBlue() / 255d;
                // final float opacity = getOpacity();
 
                final IcyVtkPanel vtkPanel = (cnv != null) ? cnv.getVtkPanel() : null;
 
                // we need to lock canvas as actor can be accessed during rendering
                if (vtkPanel != null)
                {
                    vtkPanel.lock();
                    try
                    {
                        actor.GetProperty().SetColor(r, g, b);
                    }
                    finally
                    {
                        vtkPanel.unlock();
                    }
                }
                else
                {
                    actor.GetProperty().SetColor(r, g, b);
                }
 
                // need to repaint
                painterChanged();
            }
        }
    }
 
	
	
	
	@Override
    protected myROI3DPointPainter createPainter()
    {
        return new myROI3DPointPainter();
    }

	/**
     * Return myRoi3D of ROI list.
     */
    public static List<myRoi3D> getROI3DList(List<ROI> rois)
    {
        final List<myRoi3D> result = new ArrayList<myRoi3D>();

        for (ROI roi : rois)
            if (roi instanceof myRoi3D)
                result.add((myRoi3D) roi);

        return result;
    }
  
    public String getROIClassName()
    {
        return myRoi3D.class.getName();
    }

       
    public static final String ID_Zd = "zd";
    public static final String ID_Z = "z";
    public static final String ID_T = "t";
    public static final String ID_C = "c";

    /**
     * z coordinate attachment in DOUBLE!! in addition to the one in int for display purpose, this one keep the true z position
     */
    protected double zd; 
 


    
    final public int getDimension3()
    {
        return 3;
    }

    /**
     * Returns the Z position.<br>
     * <code>-1</code> is a special value meaning the ROI is set on all Z slices (infinite Z
     * dimension).
     */
    public double getZd()
    {
        return zd;
    }

    /**
     * Sets Z position of this 2D ROI.<br>
     * You cannot set the ROI on a negative Z position as <code>-1</code> is a special value meaning
     * the ROI is set on all Z slices (infinite Z dimension).
     */
    public void setZd(double value)
    {
        final double v;

        // special value for infinite dimension --> change to -1
        if (value == java.lang.Double.MIN_VALUE)
            v = -1;
        else
            v = value;

        if (zd != v)
        {
            zd = v;
            roiChanged(false);
        }
    }

    
    
    
    
   

    @Override
    public Point5D getPosition5D(){
    	Point5D pt=new Point5D.Double(this.getPosition2D().getX(),this.getPosition2D().getY(),this.getZd(),this.getT(),this.getC());
		return pt;
    	
    }

    @Override
    public void setPosition5D(Point5D position)
    {
        beginUpdate();
        try
        {
            setZ( (int) Math.round(position.getZ()));
            setZd( position.getZ());
            setT((int) position.getT());
            setC((int) position.getC());
            setPosition2D(position.toPoint2D());
        }
        finally
        {
            endUpdate();
        }
    }

   


    @Override
    public boolean loadFromXML(Node node)
    {
        beginUpdate();
        try
        {
            if (!super.loadFromXML(node))
                return false;
            setZd(XMLUtil.getElementDoubleValue(node, ID_Zd, 0.0));
            setZ(XMLUtil.getElementIntValue(node, ID_Z, -1));
            setT(XMLUtil.getElementIntValue(node, ID_T, -1));
            setC(XMLUtil.getElementIntValue(node, ID_C, -1));
        }
        finally
        {
            endUpdate();
        }

        return true;
    }

    @Override
    public boolean saveToXML(Node node)
    {
        if (!super.saveToXML(node))
            return false;
        XMLUtil.setElementDoubleValue(node, ID_Zd, getZd());
        XMLUtil.setElementIntValue(node, ID_Z, getZ());
        XMLUtil.setElementIntValue(node, ID_T, getT());
        XMLUtil.setElementIntValue(node, ID_C, getC());

        return true;
    }


	
	 
	    
	 
	   
	 
	   
	 
	    public myRoi3D(Point2D pos)
	    {
	        super(pos);
	        
	        
	    }
	 
	    /**
	     * Generic constructor for interactive mode
	     */
	    public myRoi3D(Point5D pt)
	    {
	        this(pt.toPoint2D());
	        this.zd=pt.getZ();
	        this.z=(int) pt.getZ();
	        setC((int)pt.getC());
	        setT((int)pt.getT());
	        // getOverlay().setMousePos(pt);
	    }
	    
	    
	    public myRoi3D(ROI roi){
	    
	    	this(roi.getPosition5D());
	    	this.setColor(roi.getColor());
	    	this.setName(roi.getName());
	    	this.setStroke(getStroke()*2);
	    	
	    }
	    public myRoi3D(double x, double y, double z)
	    {
	        this(new Point2D.Double(x, y));
	        this.zd=z;
	        this.z=(int) z;
	    }
	 
	    public myRoi3D()
	    {
	        this(new Point5D.Double());
	    }
	 

	   
	 
	    
}