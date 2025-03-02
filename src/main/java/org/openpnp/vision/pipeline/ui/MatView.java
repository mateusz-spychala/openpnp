package org.openpnp.vision.pipeline.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import org.opencv.core.Mat;
import org.openpnp.util.OpenCvUtils;

public class MatView extends JComponent {
    static final boolean HIGH_QUALITY_RENDERING = true;

    private BufferedImage image;
    
    private Double zoom = 1.0;

	public void setZoom(Double val) {
		if (val >= 1.0 && val <= 4.0) {
			this.zoom = val;
		}
	}

	public Double getZoom() {
		return this.zoom;
	}

    public MatView() {
        setBackground(Color.black);
    }

    public void setMat(Mat mat) {
        if (mat == null || mat.empty()) {
            image = null;
        }
        else {
            image = OpenCvUtils.toBufferedImage(mat);
        }
        repaint();
    }

    public BufferedImage getImage() {
        return image;
    }

    public Point scalePoint(Point p) {
        if (image == null) {
            return new Point(0, 0);
        }
        
        Insets ins = getInsets();
        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        double destWidth = getWidth() - ins.left - ins.right;
        double destHeight = getHeight() - ins.top - ins.bottom;

        double widthRatio = sourceWidth / destWidth;
        double heightRatio = sourceHeight / destHeight;

        double scaledHeight, scaledWidth;

        if (heightRatio > widthRatio) {
            double aspectRatio = sourceWidth / sourceHeight;
            scaledHeight = destHeight;
            scaledWidth = (scaledHeight * aspectRatio);
        }
        else {
            double aspectRatio = sourceHeight / sourceWidth;
            scaledWidth = destWidth;
            scaledHeight = (scaledWidth * aspectRatio);
        }

        // Scale by user mouse scroll zoom
        scaledHeight *= zoom;
        scaledWidth *= zoom;

        int imageX = (int) (ins.left + (destWidth / 2) - (scaledWidth / 2));
        int imageY = (int) (ins.top + (destHeight / 2) - (scaledHeight / 2));
        
        int x = (int) ((p.x - imageX) * (sourceWidth / scaledWidth));
        int y = (int) ((p.y - imageY) * (sourceHeight / scaledHeight));
        
        x = Math.max(x, 0);
        x = Math.min(x, image.getWidth());
        y = Math.max(y, 0);
        y = Math.min(y, image.getHeight());
        
        return new Point(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }

        Insets ins = getInsets();
        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        double destWidth = getWidth() - ins.left - ins.right;
        double destHeight = getHeight() - ins.top - ins.bottom;

        /**
         * We want to fit both axes in the given destWidth and destHeight while maintaining the
         * aspect ratio. If the frame is smaller in either or both axes than the original will need
         * to be scaled to fill the space as completely as possible while still maintaining the
         * aspect ratio. 1. Determine the source size of the image: sourceWidth, sourceHeight. 2.
         * Determine the max size each axis can be: destWidth, destHeight. 3. Calculate how much
         * each axis needs to be scaled to fit. 4. Use the larger of the two and scale the opposite
         * axis by the aspect ratio + the scaling ratio.
         */

        double widthRatio = sourceWidth / destWidth;
        double heightRatio = sourceHeight / destHeight;

        double scaledHeight, scaledWidth;

        if (heightRatio > widthRatio) {
            double aspectRatio = sourceWidth / sourceHeight;
            scaledHeight = destHeight;
            scaledWidth = (scaledHeight * aspectRatio);
        }
        else {
            double aspectRatio = sourceHeight / sourceWidth;
            scaledWidth = destWidth;
            scaledHeight = (scaledWidth * aspectRatio);
        }

        // Scale by user mouse scroll zoom
        scaledHeight *= zoom;
        scaledWidth *= zoom;

        int imageX = (int) (ins.left + (destWidth / 2) - (scaledWidth / 2));
        int imageY = (int) (ins.top + (destHeight / 2) - (scaledHeight / 2));

        Graphics2D g2d = (Graphics2D) g;

        if (HIGH_QUALITY_RENDERING) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform t = new AffineTransform();
            double scaleW = ((double)scaledWidth)/image.getWidth();
            double scaleH = ((double)scaledHeight)/image.getHeight();
            // Scaled
            t.translate(imageX, imageY);
            t.scale(scaleW, scaleH);
            g2d.drawImage(image, t, null);
        }
        else {
            g2d.drawImage(image, imageX, imageY, 
            		(int) scaledWidth, 
            		(int) scaledHeight, null);
        }
    }
}
