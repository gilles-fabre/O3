package com.gfabre.android.o3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import com.gfabre.android.utilities.widgets.ScrollImageView;

/**
 * A simple canvas view used to draw from scripts
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class GraphView extends ScrollImageView {
    static final float            DOT_SIZE = 10f;
    private Double                mMinX, mMaxX, mMinY, mMaxY;
    private Canvas				  mBmpCanvas;
    private Bitmap				  mBitmap;
    private Paint                 mPaint;
    private int 				  mWidth,
    							  mHeight;
    
    public GraphView(Context context) {
        super(context);

        mMinX = mMinY = mMaxX = mMaxY = 0.0;
        View parent = (View)getParent();
        if (parent != null) {
        	// get the parent size;
        	mWidth = parent.getWidth();
        	mHeight = parent.getHeight();
        } else {
	        // retrieve the display size
	        Display display = ((WindowManager) context
					.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
	
	        Rect rect = new Rect();
	        if (display != null) 
		        display.getRectSize(rect);  
	        mWidth = rect.width();
	        mHeight = rect.height();
	    }

	    mMaxX = Double.valueOf(mWidth) - 1;
        mMaxY = Double.valueOf(mHeight) - 1;

        // create a bitmap of that dimension
        mBitmap = Bitmap.createBitmap(mWidth,
        							  mHeight,
    								  Bitmap.Config.ARGB_8888); 
		if (mBitmap == null)
			return;

		setImage(mBitmap);
        mBmpCanvas = new Canvas(mBitmap);
        mPaint = new Paint();
        mPaint.setStrokeWidth(DOT_SIZE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void doPlot(Double x, Double y) {
        // compute screen ratio and origin x,y in 'screen' coords (without ratio yet)
        double dx = (mMaxX - mMinX);
        double ox = mMinX - 1;
        double dy = (mMaxY - mMinY);
        double oy = mMaxY;

        // if range is incorrect, don't draw anything (and don't divide by 0!)
        if (dx == 0.0 || dy == 0.0)
            return;

        // convert orthonormal coordinates to screen coordinates
        x -= ox;
        y = oy - y;

        // apply screen ratio
        x *= Double.valueOf(mWidth) / dx;
        y *= Double.valueOf(mHeight) / dy;

        // clip x/y to the screen bounds
        if (x < 0 ||
            x >= mWidth ||
            y < 0 ||
            y >= mHeight)
        return; // just clip

        mBmpCanvas.drawPoint(x.intValue(), y.intValue(), mPaint);
    }

    public void doErase(Double r, Double g, Double b) {
        mBitmap.eraseColor(Color.rgb(r.intValue(), g.intValue(), b.intValue()));
    }

    public void setRange(Double xMin, Double xMax, Double yMin, Double yMax) {
        mMinX = xMin;
        mMaxX = xMax;
        mMinY = yMin;
        mMaxY = yMax;

        // make sure the orthonormal center is on screen
        if (mMinY > 0)
            mMinY = 0.0;
        if (mMaxY < 0)
            mMaxY = 0.0;

        if (mMinX > 0)
            mMinX = 0.0;
        if (mMaxX < 0)
            mMaxX = 0.0;

        // check min/max consistency
        if (mMinX > mMaxX)
        {
            Double x = mMaxX;
            mMaxX = mMinX;
            mMinX = x;
        }
        if (mMinY > mMaxY)
        {
            Double y = mMaxY;
            mMaxY = mMinY;
            mMinY = y;
        }
    }

    public void setColor(Double r, Double g, Double b) {
        mPaint.setARGB(255, r.intValue(), g.intValue(), b.intValue());
    }

    public void setDotSize(Double s) {
        mPaint.setStrokeWidth(s.floatValue());
    }
}
