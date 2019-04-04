package plugins.perrine.orthoviewerroi;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;

import java.awt.RenderingHints;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.canvas.IcyCanvasEvent;
import icy.canvas.Layer;
import icy.gui.component.IcySlider;
import icy.gui.frame.progress.ToolTipFrame;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;

import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginCanvas;
import icy.preferences.CanvasPreferences;
import icy.roi.ROI;
import icy.roi.ROI.ROIPainter;

import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.system.thread.SingleProcessor;
import icy.system.thread.ThreadUtil;

import icy.type.point.Point5D;

import icy.type.rectangle.Rectangle5D;
import icy.util.GraphicsUtil;
import plugins.kernel.roi.roi3d.ROI3DPoint;
/**
 * was modified from Alexandre Dufour OrthoViewer;
*	adding 3D point ROI + can be added in all 3 views, and manipulated in 3D views
* in addition, if a canvas2D view of the same sequence exists, 
* the current z will be synchronized with this viewer view.
* 
 * Corrected zoom management for add ROI
 * @author paul-gilloteaux-p, dufour-a
 *
 */
public class OrthoViewerRoi extends Plugin implements PluginCanvas {

	@Override
	public String getCanvasClassName() {
		 return OrthoViewerRoi.class.getName();
    }
    
    @Override
    public IcyCanvas createCanvas(Viewer viewer)
    {
        return new OrthoCanvasRoi(viewer);
	}
    @SuppressWarnings("serial")
    private class OrthoCanvasRoi extends IcyCanvas2D  {
    final JPanel orthoViewPanel;
    
    final OrthoViewRoi xy, zy, xz;
    
    double xScale = 1, yScale = 1;
    
    final IcySlider zoomSlider = new IcySlider(IcySlider.HORIZONTAL, 1, 1000, 100);
    
    double zoom = 1;
    
    public OrthoCanvasRoi(Viewer viewer)
    {
        super(viewer);
      
        orthoViewPanel = new JPanel(null);
        
        xy = new OrthoViewRoi(DimensionId.Z);
        xz = new OrthoViewRoi(DimensionId.Y);
        zy = new OrthoViewRoi(DimensionId.X);
        
        xScale = getSequence().getPixelSizeZ() / getSequence().getPixelSizeX();
        if (Double.isNaN(xScale) || xScale == 0) xScale = 1.0;
        
        yScale = getSequence().getPixelSizeZ() / getSequence().getPixelSizeY();
        if (Double.isNaN(yScale) || yScale == 0) yScale = 1.0;
        
        posX = getSequence().getSizeX() / 2;
        posY = getSequence().getSizeY() / 2;
        posZ = getSequence().getSizeZ() / 2;
        
        orthoViewPanel.add(xy);
        orthoViewPanel.add(zy);
        orthoViewPanel.add(xz);
        
        setZoom(1.0);
        viewer.setSize((int)(viewer.getWidth()+getSequence().getSizeZ()*yScale+10), (int)(viewer.getHeight()+getSequence().getSizeZ()*xScale+10));
        JScrollPane scroll = new JScrollPane(orthoViewPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);
        
        updateTNav();
        updateZNav();
        
        getMouseImageInfosPanel().setInfoColorVisible(false);
        getMouseImageInfosPanel().setInfoCVisible(false);
        getMouseImageInfosPanel().setInfoXVisible(false);
        getMouseImageInfosPanel().setInfoYVisible(false);
        getMouseImageInfosPanel().setInfoDataVisible(false);
        
        
        xy.imageChanged();
        xz.imageChanged();
        zy.imageChanged();
        
        //invalidate();
        repaint();
        new ToolTipFrame("<html>" + "Similary to classical OrthoViewer, you can browse your data"+
        "<br> in xy plan, xz and yz plan.</br>  "+
        		" <br> the main difference is that you can place and drag 3D Roi points on these views </br>"+
        		" <br> <li> RIGHT click to move the position cursor (navigate in the views)</li></br>"
        		+ "<br> <li> LEFT click on any view will add an ROI at the mouse position</li></br>"
        		+ "<br> <li> DRAG a point by putting your cursor onn it and dragging (slowly)</li></br>"
        		+ "<br> <li> Refresh if necessary by moving the mouse above the views</li></br>"
        		 + "<br> Note that colors are not respected in xz and yz view in this version</br>"
				+ "</html>","startmessageorthoroi");
    }
    
    @Override
    public void customizeToolbar(JToolBar toolBar)
    {
        super.customizeToolbar(toolBar);
        
        final JLabel sizeLabel = new JLabel("  Zoom:");
        final JLabel zoomValueLabel = new JLabel(zoomSlider.getValue() + "%");
        
        zoomSlider.setFocusable(false);
        zoomSlider.addChangeListener(new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent e)
            {
                setZoom(zoomSlider.getValue() / 100.0);
                zoomValueLabel.setText(zoomSlider.getValue() + "%");
            }
        });
        
        toolBar.add(sizeLabel);
        toolBar.add(zoomSlider);
        toolBar.add(zoomValueLabel);
    }
    
    private void setZoom(double newZoom)
    {
        this.zoom = newZoom;
        
        // adjust the main (XY) panel size
        int xyWidth = (int) Math.round(newZoom * getSequence().getSizeX());
        int xyHeight = (int) Math.round(newZoom * getSequence().getSizeY());
        
        xy.setBounds(0, 0, xyWidth, xyHeight);
        zy.setBounds(xyWidth + 5, 0, (int) Math.round(newZoom * getSequence().getSizeZ() * xScale), xyHeight);
        xz.setBounds(0, xyHeight + 5, xyWidth, (int) Math.round(newZoom * getSequence().getSizeZ() * yScale));
        orthoViewPanel.setPreferredSize(new Dimension(xy.getWidth() + 5 + zy.getWidth(), xy.getHeight() + 5 + xz.getHeight()));
    }
    
    @Override
    public java.awt.geom.Point2D.Double canvasToImage(Point point)
    {
        return super.canvasToImage(point);
    }
    
    @Override
    public void changed(IcyCanvasEvent event)
    {
        super.changed(event);
        //System.out.println(event.getType().toString());//always position_changed
        //System.out.println(event.getDim());
        switch (event.getType())
        {
        case POSITION_CHANGED: {
            if (event.getDim() == DimensionId.Z)
            {
                xy.imageChanged();
                refresh();
            }
            else if (event.getDim() == DimensionId.T)
            {
                xy.imageChanged();
                zy.imageChanged();
                xz.imageChanged();
                refresh();
            }
            break;
        }
        default:
        }
    }
    
    @Override
    protected void lutChanged(int component)
    {
        super.lutChanged(component);
        
        try
        {
            if (xy != null) xy.imageChanged();
            if (zy != null) zy.imageChanged();
            if (xz != null) xz.imageChanged();
        }
        catch (NullPointerException npE)
        {
            // as silly as it seems, this may happen...
        }
        
        refresh();
    }
    
    protected void mousePositionChanged(DimensionId dim, int x, int y)
    {
        x = (int) Math.max(0, x / zoom);
        y = (int) Math.max(0, y / zoom);
       
        int maxWidth = getSequence().getSizeX() - 1;
        int maxHeight = getSequence().getSizeY() - 1;
        int maxDepth = getSequence().getSizeZ() - 1;
        
        switch (dim)
        {
        case Z: {
            
            // adjust X
            if (x > maxWidth) x = maxWidth;
            if (x != posX)
            {
                setPositionXInternal(x);
                zy.imageChanged();
            }
            
            // adjust Y
            if (y > maxHeight) y = maxHeight;
            if (y != posY)
            {
                setPositionYInternal(y);
                xz.imageChanged();
            }
            
            break;
        }
        case Y: {
            
            // adjust X
            if (x > maxWidth) x = maxWidth;
            if (x != posX)
            {
                setPositionXInternal(x);
                zy.imageChanged();
            }
            
            // adjust Z
            double scale = getSequence().getPixelSizeZ() / getSequence().getPixelSizeX();
            if (Double.isNaN(scale) || scale == 0) scale = 1.0;
            
            y /= scale;
            if (y > maxDepth) y = maxDepth;
            if (y != posZ)
            {
                setPositionZInternal(y);
                xy.imageChanged();
            }
            
            break;
        }
        case X: {
            
            // adjust Y
            if (y > maxHeight) y = maxHeight;
            if (y != posY)
            {
                setPositionYInternal(y);
                xz.imageChanged();
            }
            
            // adjust Z
            double scale = getSequence().getPixelSizeZ() / getSequence().getPixelSizeY();
            if (Double.isNaN(scale) || scale == 0) scale = 1.0;
            
            x /= scale;
            if (x > maxDepth) x = maxDepth;
            if (x != posZ)
            {
                setPositionZInternal(x);
                xy.imageChanged();
            }
            
            break;
        }
        default:
        }
        
        refresh();
    }
    
    @Override
    public void refresh()
    {
        getMouseImageInfosPanel().updateInfos(this);
        repaint();
    }
    
    /**
     * Snapshot
     */
    @Override
    public BufferedImage getRenderedImage(int t, int z, int c, boolean canvasView)
    {
        Dimension size = orthoViewPanel.getPreferredSize();
        BufferedImage snap = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = snap.createGraphics();
        
        int oldZ = getPositionZ();
        int oldT = getPositionT();
        
        setPositionZInternal(z);
        setPositionTInternal(t);
        xy.imageCache.run();
        
        orthoViewPanel.paintAll(g);
        
        setPositionZInternal(oldZ);
        setPositionZInternal(oldT);
        
        return snap;
    }
    
    public IcyBufferedImage getCurrentImage(DimensionId fixedDim)
    {
        Sequence seq = getSequence();
        int sizeX = seq.getSizeX();
        int sizeY = seq.getSizeY();
        int sizeZ = seq.getSizeZ(posT);
        int sizeC = seq.getSizeC();
        
        if (sizeZ == 0) return null;
        
        switch (fixedDim)
        {
        case Z:
            return super.getImage(posT, posZ, -1);
            
        case Y: {
            if (posY == -1) return null;
            
            // create the XZ side view
            IcyBufferedImage xzImage = new IcyBufferedImage(sizeX, sizeZ, sizeC, seq.getDataType_());
            
            int inY = sizeX * posY;
            int out_offset = 0;
            
            // field only used for debugging purposes
            int inSize = 0, outSize = 0;
            
            try
            {
                Object in_z_c_xy = seq.getDataXYCZ(posT);
                Object out_c_xy = xzImage.getDataXYC();
                
                for (int z = 0; z < sizeZ; z++)
                {
                    Object in_c_xy = Array.get(in_z_c_xy, z);
                    
                    // handle missing slices
                    if (in_c_xy == null) continue;
                    
                    out_offset = z * sizeX;
                    
                    for (int c = 0; c < sizeC; c++)
                    {
                        Object in_xy = Array.get(in_c_xy, c);
                        inSize = Array.getLength(in_xy);
                        Object out_xy = Array.get(out_c_xy, c);
                        outSize = Array.getLength(out_xy);
                        System.arraycopy(in_xy, inY, out_xy, out_offset, sizeX);
                    }
                }
                return xzImage;
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                throw new ArrayIndexOutOfBoundsException(
                        "cannot copy from [size=" + inSize + ",off=" + inY + "] to [size=" + outSize + ",off=" + out_offset + "] with size " + sizeX);
            }
        }
        case X: {
            // create the ZY side view
            IcyBufferedImage zyImage = new IcyBufferedImage(sizeZ, sizeY, sizeC, seq.getDataType_());
            
            Object in_z_c_xy = seq.getDataXYCZ(posT);
            Object out_c_xy = zyImage.getDataXYC();
            
            // wait for buffers
            ThreadUtil.sleep(20);
            
            for (int z = 0; z < sizeZ; z++)
            {
                Object in_c_xy = Array.get(in_z_c_xy, z);
                
                // handle missing slices
                if (in_c_xy == null) continue;
                
                for (int c = 0; c < sizeC; c++)
                {
                    Object in_xy = Array.get(in_c_xy, c);
                    Object out_xy = Array.get(out_c_xy, c);
                    
                    for (int y = 0, in_offset = posX, out_off = z; y < sizeY; y++, in_offset += sizeX, out_off += sizeZ)
                    {
                        Object pixelIN = Array.get(in_xy, in_offset);
                        Array.set(out_xy, out_off, pixelIN);
                    }
                }
            }
            
            return zyImage;
        }
        
        default:
            throw new UnsupportedOperationException();
        }
    }
    
    @Override
    public int getPositionC()
    {
        return -1;
    }
    
    @Override
    public void repaint()
    {
        super.repaint();
        if (xy != null) xy.repaint();
        if (zy != null) zy.repaint();
        if (xz != null) xz.repaint();
    }
    
    @Override
    public void keyReleased(KeyEvent e)
    {
        super.keyReleased(e);
        repaint();
    }
    
    @SuppressWarnings("unused")
    public class OrthoViewRoi extends JPanel implements MouseWheelListener, MouseListener, MouseMotionListener
    {
        private Timer refreshTimer;
        
        public class ImageCache implements Runnable
        {
            /**
             * image cache
             */
            private BufferedImage imageCache;
            
            /**
             * processor
             */
            private final SingleProcessor processor;
            /**
             * internals
             */
            private boolean               needRebuild;
            
            public ImageCache()
            {
                super();
                
                processor = new SingleProcessor(true, "OrthoView renderer");
                // we want the processor to stay alive for sometime
                processor.setKeepAliveTime(5, TimeUnit.MINUTES);
                
                imageCache = null;
                needRebuild = true;
                // build cache
                processor.submit(this);
            }
            
            public void invalidCache()
            {
                needRebuild = true;
            }
            
            public boolean isValid()
            {
                return !needRebuild;
            }
            
            public boolean isProcessing()
            {
                return processor.isProcessing();
            }
            
            public void refresh()
            {
                if (needRebuild)
                    // rebuild cache
                    processor.submit(this);
                    
                // just repaint
                repaint();
            }
            
            public BufferedImage getImage()
            {
                return imageCache;
            }
            
            @Override
            public void run()
            {
                // important to set it to false at beginning
                needRebuild = false;
                
                final IcyBufferedImage img = getCurrentImage(currentDimension);
                
                if (img != null) imageCache = IcyBufferedImageUtil.getARGBImage(img, getLut(), imageCache);
                else imageCache = null;
                
                // repaint now
                repaint();
            }
        }
        
        /**
         * Image cache
         */
        final ImageCache imageCache;
        
        /**
         * internals
         */
        private final Font font;
        
        public final DimensionId currentDimension;
        
        private final Point5D.Double mousePosition = new Point5D.Double(getPositionX(), getPositionY(), getPositionZ(), getPositionT(), getPositionC());
        
        public OrthoViewRoi(DimensionId dim)
        {
            super();
            
            this.currentDimension = dim;
            
            imageCache = new ImageCache();
            
            font = new Font("Arial", Font.BOLD, 16);
            
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
            
            
        }
        
        @Override
        public void mouseClicked(MouseEvent e)
        {
            //System.out.println(e.toString());
        	if (e.getButton()==3){ // Perrine: 3 means right click, 1 is left click
        		// send mouse event to painters first
        		for (Layer layer : getVisibleLayers()){
        			layer.getOverlay().mouseClick(e, mousePosition, OrthoCanvasRoi.this);
        			
        		}
        		}
        	else if (e.getButton()==1){
        		// check if we have click on a roi (to move it)
        		boolean RoiSelected=false;
        		//for (Layer layer : getVisibleLayers()){
        			
        		/*	ROI roi = layer.getAttachedROI();
        			if (roi != null ){
        				if ((roi.getPosition5D().getX()<mousePosition.getX()+10)||(roi.getPosition5D().getX()>mousePosition.getX()-10)){
        					if ((roi.getPosition5D().getY()<mousePosition.getY()+10)||(roi.getPosition5D().getY()>mousePosition.getY()-10)){
        					System.out.println(roi.getPosition5D().getX() +" "+roi.getPosition5D().getY());
        					RoiSelected=true;
        					layer.getOverlay().mouseClick(e, mousePosition, OrthoCanvasRoi.this);
        					break;
        					}
        				}
        			}
        		}*/
        		//if (!RoiSelected){
        			Sequence seq =getSequence();

        			ROI3DPoint roi3D = new ROI3DPoint();
        			Point5D pos=roi3D.getPosition5D();
        			switch (currentDimension){
        			case Z:
        				pos.setX(mousePosition.getX());
        				pos.setY(mousePosition.getY());
        				pos.setZ(posZ);

        				break;
        			case Y:
        				pos.setX(((mousePosition.getX())*getWidth()/seq.getSizeX())/zoom);
        				
        				
        				
        				
        				pos.setY(posY);
        				pos.setZ((zoom*(mousePosition.getY())/getHeight()*seq.getSizeZ()));

        				break;
        			case X:
        				pos.setX(posX);
        				pos.setY(((mousePosition.getY())*getHeight()/seq.getSizeY())/zoom);
        				pos.setZ((zoom*(mousePosition.getX())*seq.getSizeZ()/getWidth()));
        				break;
        			default:
        				break;
        			}
        			roi3D.setPosition5D(pos);
        			roi3D.setStroke(5);
        			roi3D.setColor(Color.getHSBColor((float)Math.random()*1000, (float)1,(float)1));
        			seq.addROI(roi3D);
        			repaint();
        			
        		//}
        	}
            }
        
        @Override
        public void mousePressed(MouseEvent e)
        {
        	//System.out.println(e.toString());
        	// send mouse event to painters first
        	if (e.getButton()==3){
        	//for (Layer layer : getVisibleLayers()) System.out.println(layer.getName());
            for (Layer layer : getVisibleLayers())
                layer.getOverlay().mousePressed(e, mousePosition, OrthoCanvasRoi.this);
                
            mousePositionChanged(currentDimension, e.getX(), e.getY());
        	}
        	else{
        		
        	}
        	repaint();
        }
        
        @Override
        public void mouseReleased(MouseEvent e)
        {
        	//System.out.println(e.toString());
        	
        	// send mouse event to painters after
            for (Layer layer : getVisibleLayers())
                layer.getOverlay().mouseReleased(e, mousePosition, OrthoCanvasRoi.this);
        }
        
        @Override
        public void mouseEntered(MouseEvent e)
        {
        }
        
        @Override
        public void mouseExited(MouseEvent e)
        {
        }
        
        @Override
        public void mouseMoved(MouseEvent e)
        {
        	//System.out.println(e.toString());
        	Point2D.Double p = canvasToImage(e.getPoint());
            mousePosition.x = p.x / zoom;
            mousePosition.y = p.y / zoom;
            
            // send mouse event to painters first
            for (Layer layer : getVisibleLayers())
                layer.getOverlay().mouseMove(e, mousePosition, OrthoCanvasRoi.this);
                
            repaint();
        }
        
        @Override
        public void mouseDragged(MouseEvent e)
        {

        	if(e.getModifiersEx()==MouseEvent.getMaskForButton(3)){


        		Point2D.Double p = canvasToImage(e.getPoint());
        		Point5D.Double p5 = new Point5D.Double(p.x / zoom, p.y / zoom, mousePosition.z, mousePosition.t, mousePosition.c);

        		// mousePosition.y = p.y;

        		// send mouse event to painters after
        		for (Layer layer : getVisibleLayers()){
        			layer.getOverlay().mouseDrag(e, p5, OrthoCanvasRoi.this);

        		}
        		mousePositionChanged(currentDimension, e.getX(), e.getY());

        	}
        	else{
        		Sequence seq=getSequence();
        		for (Layer layer : getVisibleLayers()){
        			ROI roi=layer.getAttachedROI();


        			if (roi!=null){
        				Point2D.Double p = canvasToImage(e.getPoint());
        				Point5D.Double p5 = new Point5D.Double(p.x / zoom, p.y / zoom, mousePosition.z, mousePosition.t, mousePosition.c);



        				Rectangle5D rect2 = roi.getBounds5D();
        				Rectangle5D rect = new Rectangle5D.Double(rect2);


        				rect.setSizeX(roi.getStroke()*3);
        				rect.setSizeY(roi.getStroke()*3);
        				rect.setSizeZ(roi.getStroke()*3);
        				switch (currentDimension){

        				case Y:
        					

        					p5.setX((p5.getX())*getWidth()/seq.getSizeX()/zoom);


        					p5.setZ(((p5.getY())/getHeight()*seq.getSizeZ()));
        					p5.setY(posY);
        					
        					break;
        				case X:
        					
        					p5.setY(((p5.getY())*getHeight()/seq.getSizeY())/zoom);
        					p5.setZ(((p5.getX())*seq.getSizeZ()/getWidth()));
        					p5.setX(posX);
        					break;
        				default:
        					
        					p5.setZ(posZ);
        					break;
        				}
        				rect.setX(p5.x-roi.getStroke());
    					rect.setY(p5.y-roi.getStroke());
    					rect.setZ(p5.z-roi.getStroke());
        				if (rect.contains(rect2)){
        					roi.setPosition5D(p5);
        					refresh();
        				}
        			}

        		}

        	}
        }
   
        	
        
        
        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {
        }
        
        @Override
        protected void paintComponent(Graphics g)
        {
            // pre-paint
            super.paintComponent(g);
            
            // check if the image data exists
            
            Sequence seq = getSequence();
            
            double sizeZ = seq.getSizeZ(posT);
            double scaleX = seq.getPixelSizeZ() / seq.getPixelSizeX();
            if (Double.isNaN(scaleX) || scaleX == 0) scaleX = 1.0;
            double scaleY = seq.getPixelSizeZ() / seq.getPixelSizeY();
            if (Double.isNaN(scaleY) || scaleY == 0) scaleY = 1.0;
            
            final BufferedImage img = imageCache.getImage();
            
            if (img != null)
            {
                // paint the image data
                
                final Graphics2D g2 = (Graphics2D) g.create();
                
                g2.scale(zoom, zoom);
                
                if (CanvasPreferences.getFiltering())
                {
                    if (getScaleX() < 4d && getScaleY() < 4d)
                    {
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    }
                    else
                    {
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    }
                }
                
                AffineTransform trans = new AffineTransform();
                if (currentDimension == DimensionId.X)
                {
                    trans.scale(scaleX, 1.0);
                }
                else if (currentDimension == DimensionId.Y)
                {
                    trans.scale(1.0, scaleY);
                }
                
                g2.drawImage(img, trans, null);
                
                // paint the layers
                
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                //Transfer z to any canvas2D of the same sequence
                ArrayList<Viewer> ListViewers = getSequence().getViewers();
                for (Viewer v:ListViewers){
                	if (v.getCanvas() instanceof IcyCanvas2D){
                		v.setPositionZ(posZ);
                	}
                }
                final ArrayList<Layer> layers = getVisibleLayers();
                
                // draw them in inverse order to have first painter event at top
                // every layer but the first (i.e. no image, we draw it ourselves)
                for (int i = layers.size() - 2; i >= 0; i--)
                {
                    final Layer layer = layers.get(i);
                    
                    if (!layer.isVisible()) continue;
                    
                    final float alpha = layer.getOpacity();
                    
                    if (alpha != 1f) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    else g2.setComposite(AlphaComposite.SrcOver);
                    
                    
                    
                   
                    if (currentDimension == DimensionId.Z)
                    {
                    	
                    	layer.getOverlay().paint(g2, seq, OrthoCanvasRoi.this);
                        
                    }
                    else
                    {
                        // on side views, check if ROI can be painted
                        ROI roi = layer.getAttachedROI();
                        
                        //if (roi != null && roi instanceof ROI2D)
                        if (roi != null )
                        {
                        	//System.out.println(roi.getName());
                        	 final Graphics2D g3 = (Graphics2D) g.create();
                        	 g3.scale(zoom, zoom);
                        	Color color = ((ROIPainter) layer.getOverlay()).getDisplayColor();
                            //System.out.println(color);
                        	double stroke = ((ROIPainter) layer.getOverlay()).getStroke();
                            
                            ROI roicopy=roi.getCopy();
                            Point5D position=roicopy.getPosition5D();
                            
							//roi.getOverlay().addOverlayListener(OverlayListener listener);
                            
                            
                            g3.setColor(Color.red);
                            
                            g3.setStroke(new BasicStroke((float) ROI.getAdjustedStroke(OrthoCanvasRoi.this, stroke + 2d)));
                            //g3.setPaintMode();
                            if (currentDimension == DimensionId.X)
                            {
                            	//yz
                            	position.setX((roi.getPosition5D().getZ()*getWidth()/seq.getSizeZ())/zoom);
                            	position.setY(roi.getPosition5D().getY());
                            	position.setZ(OrthoCanvasRoi.this.posZ+roi.getPosition5D().getX()-OrthoCanvasRoi.this.posX);
                                roicopy.setPosition5D(position);
                                
                            	
                            }
                            else
                            {
                                // XZ view
                            	position.setX(roi.getPosition5D().getX());
                            	position.setY((roi.getPosition5D().getZ()*getHeight()/seq.getSizeZ())/zoom);
                            	position.setZ(OrthoCanvasRoi.this.posZ+roi.getPosition5D().getY()-OrthoCanvasRoi.this.posY);
                                roicopy.setPosition5D(position);
                             
                            }
                            g3.setColor(Color.red);
                            //OrthoCanvasRoi.this.setForeground(Color.red);
                            g3.setStroke(new BasicStroke((float) ROI.getAdjustedStroke(OrthoCanvasRoi.this, stroke + (roi.isSelected() ? 1d : 0d))));
                            //roicopy.getOverlay().setColor(Color.red);
                            //roicopy.getOverlay().setStroke(stroke);
                            //roicopy.getOverlay().setOpacity(1);
                            roicopy.getOverlay().paint(g3, seq,OrthoCanvasRoi.this);
                            
                            
                            // draw border black line
                            /*g2.setStroke(new BasicStroke((float) ROI.getAdjustedStroke(OrthoCanvasRoi.this, stroke + (roi.isSelected() ? 2d : 1d))));
                            
                            g2.setColor(Color.black);
                            //g2.draw(rect);
                            
                            // draw internal border
                            g2.setColor(color);
                            g2.setStroke(new BasicStroke((float) ROI.getAdjustedStroke(OrthoCanvasRoi.this, stroke + (roi.isSelected() ? 1d : 0d))));
                            //g2.draw(rect);*/
                            
                        }
                    }
                }
                
                g2.setStroke(new BasicStroke((float) (1.0 / zoom)));
                g2.setColor(Color.white);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                
                switch (currentDimension)
                {
                case Z:
                    if (zoom > 1)
                    {
                        g2.draw(new Rectangle2D.Double(posX, 0, 1, seq.getHeight()));
                        g2.draw(new Rectangle2D.Double(0, posY, seq.getWidth(), 1));
                    }
                    else
                    {
                        g2.setStroke(new BasicStroke((float) zoom));
                        g2.drawLine(posX, 0, posX, seq.getHeight());
                        g2.drawLine(0, posY, seq.getWidth(), posY);
                    }
                    break;
                    
                case X:
                    if (zoom > 1)
                    {
                        int x = (int) Math.round(posZ * scaleX);
                        g2.draw(new Rectangle2D.Double(0, posY, seq.getWidth(), 1));
                        g2.draw(new Rectangle2D.Double(x, 0, scaleX, seq.getHeight()));
                    }
                    else
                    {
                        g2.setStroke(new BasicStroke((float) zoom));
                        g2.drawLine(0, posY, seq.getWidth(), posY);
                        int x = (int) Math.round(posZ * scaleX + scaleX * 0.5);
                        g2.setStroke(new BasicStroke((float) (scaleX * zoom)));
                        g2.drawLine(x, 0, x, seq.getHeight());
                    }
                    break;
                    
                case Y:
                    if (zoom > 1)
                    {
                        int y = (int) Math.round(posZ * scaleY);
                        g2.draw(new Rectangle2D.Double(0, y, seq.getWidth(), scaleY));
                        g2.draw(new Rectangle2D.Double(posX, 0, 1, seq.getHeight()));
                    }
                    else
                    {
                        g2.setStroke(new BasicStroke((float) zoom));
                        g2.drawLine(posX, 0, posX, seq.getWidth());
                        int y = (int) Math.round(posZ * scaleY + scaleY * 0.5);
                        g2.setStroke(new BasicStroke((float) (scaleY * zoom)));
                        g2.drawLine(0, y, seq.getWidth(), y);
                    }
                    break;
                    
                default:
                    break;
                }
                
                g2.dispose();
                
            }
            else
            {
                final Graphics2D g2 = (Graphics2D) g.create();
                
                g2.setFont(font);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (OrthoCanvasRoi.this.getCurrentImage() != null)
                    // cache not yet built
                    drawTextCenter(g2, "Loading...", 0.8f);
                else
                    // no image
                    drawTextCenter(g2, " No image ", 0.8f);
                    
                g2.dispose();
            }
            
            // image or layers changed during repaint --> refresh again
            
            if (!isCacheValid()) refresh();
            
            // if (!isCacheValid())
            // refresh();
            // cache is being rebuild --> refresh to show progression
            // else if (imageCache.isProcessing()) refreshLater(100);
        }
        
        public void drawTextBottomRight(Graphics2D g, String text, float alpha)
        {
            final Rectangle2D rect = GraphicsUtil.getStringBounds(g, text);
            final int w = (int) rect.getWidth();
            final int h = (int) rect.getHeight();
            final int x = getWidth() - (w + 8 + 2);
            final int y = getHeight() - (h + 8 + 2);
            
            g.setColor(Color.gray);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.fillRoundRect(x, y, w + 8, h + 8, 8, 8);
            
            g.setColor(Color.white);
            g.drawString(text, x + 4, y + 2 + h);
        }
        
        public void drawTextTopRight(Graphics2D g, String text, float alpha)
        {
            final Rectangle2D rect = GraphicsUtil.getStringBounds(g, text);
            final int w = (int) rect.getWidth();
            final int h = (int) rect.getHeight();
            final int x = getWidth() - (w + 8 + 2);
            final int y = 2;
            
            g.setColor(Color.gray);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.fillRoundRect(x, y, w + 8, h + 8, 8, 8);
            
            g.setColor(Color.white);
            g.drawString(text, x + 4, y + 2 + h);
        }
        
        public void drawTextCenter(Graphics2D g, String text, float alpha)
        {
            final Rectangle2D rect = GraphicsUtil.getStringBounds(g, text);
            final int w = (int) rect.getWidth();
            final int h = (int) rect.getHeight();
            final int x = (getWidth() - (w + 8 + 2)) / 2;
            final int y = (getHeight() - (h + 8 + 2)) / 2;
            
            g.setColor(Color.gray);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.fillRoundRect(x, y, w + 8, h + 8, 8, 8);
            
            g.setColor(Color.white);
            g.drawString(text, x + 4, y + 2 + h);
        }
        
        @Override
        public void repaint()
        {
            super.repaint();
        }
        
        public void refresh()
        {
            imageCache.refresh();
        }
        
        /**
         * Refresh in sometime
         */
        public void refreshLater(int milli)
        {
            refreshTimer.setInitialDelay(milli);
            refreshTimer.start();
        }
        
        public void imageChanged()
        {
            imageCache.invalidCache();
        }
        
        public void layersChanged()
        {
       
        }
        
        public boolean isCacheValid()
        {
            return imageCache.isValid();
        }

        

		
		
    }
    
    @Override
    public Component getViewComponent()
    {
        return null;
    }

	
    
   


	
	
	


}
}