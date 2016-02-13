package com.itextpdf.kernel.pdf.canvas.draw;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;

/**
 * Implementation of {@link Drawable} which draws a dotted horizontal line along
 * the bottom edge of the specified rectangle.
 */
public class DottedLine implements Drawable {

    @Override
    public void draw(PdfCanvas canvas, Rectangle drawArea) {
        canvas.saveState();
        canvas.setLineDash(0, 4, 4 / 2);
        canvas.setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND);
        canvas
                .moveTo(drawArea.getX(), drawArea.getY())
                .lineTo(drawArea.getX() + drawArea.getWidth(), drawArea.getY())
                .stroke()
                .restoreState();
    }
}