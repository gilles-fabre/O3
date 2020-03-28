package com.gfabre.android.o3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import com.gfabre.android.utilities.widgets.ScrollImageView;
import com.gfabre.android.threeD.*;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A simple canvas view used to draw from scripts
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class GraphView extends ScrollImageView {
    static final float            DOT_SIZE = 10f;
    private Double                mMinX, mMaxX, mMinY, mMaxY;
    private Double                mPovX, mPovY, mPovZ;
    private Canvas				  mBmpCanvas;
    private Bitmap				  mBitmap;
    private Paint                 mPaint;
    private int 				  mWidth,
    							  mHeight;

    private static final int      UPDATE_VIEW_MESSAGE = 1;
    private static final int      PARTIAL_UPDATE_VIEW_MESSAGE = 2;

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

        mPovX = 0.0;
        mPovY = 0.0;
        mPovZ = 0.0;

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

    /**
     * All interactions with the UI must be done from the main UI thread, hence
     * thru a message handler, invoked from the background thread running the scripts.
     */
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                // update the stack
                case UPDATE_VIEW_MESSAGE:
                    invalidate();
                    break;

                case PARTIAL_UPDATE_VIEW_MESSAGE:
                    invalidate((Rect)inputMessage.obj);
                    break;
            }
        }
    };

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

        Rect rect = new Rect(x.intValue(), y.intValue(), x.intValue(), y.intValue());
        mBmpCanvas.drawPoint(rect.left, rect.top, mPaint);
        mHandler.obtainMessage(UPDATE_VIEW_MESSAGE, rect).sendToTarget();
    }

    public void doPlot3D(Double x, Double y, Double z) {
        // compute screen ratio and origin x,y,z in 'screen' coords (without ratio yet)
        double dx = (mMaxX - mMinX);
        double ox = mMinX - 1;
        double dy = (mMaxY - mMinY);
        double oy = mMaxY;

        // if range is incorrect, don't draw anything (and don't divide by 0!)
        if (dx == 0.0 || dy == 0.0)
            return;

        // convert to projected 3D coordinates
        Dot dot = new Dot(x, y, z);
        dot.project(mPovX, mPovY, mPovZ);

        x = dot.getZX();
        y = dot.getZY();

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

        Rect rect = new Rect(x.intValue(), y.intValue(), x.intValue(), y.intValue());
        mBmpCanvas.drawPoint(rect.left, rect.top, mPaint);
        mHandler.obtainMessage(UPDATE_VIEW_MESSAGE, rect).sendToTarget();
    }

    public void doLine(Double x0, Double y0, Double x1, Double y1) {
        // compute screen ratio and origin x,y in 'screen' coords (without ratio yet)
        double dx = (mMaxX - mMinX);
        double ox = mMinX - 1;
        double dy = (mMaxY - mMinY);
        double oy = mMaxY;

        // if range is incorrect, don't draw anything (and don't divide by 0!)
        if (dx == 0.0 || dy == 0.0)
            return;

        // convert orthonormal coordinates to screen coordinates
        x0 -= ox;
        y0 = oy - y0;
        x1 -= ox;
        y1 = oy - y1;

        // apply screen ratio
        x0 *= Double.valueOf(mWidth) / dx;
        y0 *= Double.valueOf(mHeight) / dy;
        x1 *= Double.valueOf(mWidth) / dx;
        y1 *= Double.valueOf(mHeight) / dy;

        // TODO : clip line to the screen
        Rect rect = new Rect(x0.intValue(), y0.intValue(), x1.intValue(), y1.intValue());
        mBmpCanvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, mPaint);
        mHandler.obtainMessage(PARTIAL_UPDATE_VIEW_MESSAGE, rect).sendToTarget();
    }

    public void doLine3D(Double x0, Double y0, Double z0, Double x1, Double y1, Double z1) {
        // compute screen ratio and origin x,y in 'screen' coords (without ratio yet)
        double dx = (mMaxX - mMinX);
        double ox = mMinX - 1;
        double dy = (mMaxY - mMinY);
        double oy = mMaxY;

        // if range is incorrect, don't draw anything (and don't divide by 0!)
        if (dx == 0.0 || dy == 0.0)
            return;

        // convert to projected 3D coordinates
        Dot dot0 = new Dot(x0, y0, z0);
        dot0.project(mPovX, mPovY, mPovZ);

        x0 = dot0.getZX();
        y0 = dot0.getZY();

        Dot dot1 = new Dot(x1, y1, z1);
        dot1.project(mPovX, mPovY, mPovZ);

        x1 = dot1.getZX();
        y1 = dot1.getZY();

        // convert orthonormal coordinates to screen coordinates
        x0 -= ox;
        y0 = oy - y0;
        x1 -= ox;
        y1 = oy - y1;

        // apply screen ratio
        x0 *= Double.valueOf(mWidth) / dx;
        y0 *= Double.valueOf(mHeight) / dy;
        x1 *= Double.valueOf(mWidth) / dx;
        y1 *= Double.valueOf(mHeight) / dy;

        // TODO : clip line to the screen
        Rect rect = new Rect(x0.intValue(), y0.intValue(), x1.intValue(), y1.intValue());
        mBmpCanvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, mPaint);
        mHandler.obtainMessage(PARTIAL_UPDATE_VIEW_MESSAGE, rect).sendToTarget();
    }

    public void doErase(Double r, Double g, Double b) {
        mBitmap.eraseColor(Color.rgb(r.intValue(), g.intValue(), b.intValue()));
        mHandler.obtainMessage(UPDATE_VIEW_MESSAGE).sendToTarget();
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

    public void doPov3D(Double x, Double y, Double z) {
        mPovX = x;
        mPovY = y;
        mPovZ = z;
    }

    public void setColor(Double r, Double g, Double b) {
        mPaint.setARGB(255, r.intValue(), g.intValue(), b.intValue());
    }

    public void setDotSize(Double s) {
        mPaint.setStrokeWidth(s.floatValue());
    }

    @SuppressLint("WrongThread")
    public void saveToPngFile(String filename) throws IOException {
        if (!filename.endsWith(".png") && !filename.endsWith(".PNG"))
            filename += ".png";
        FileOutputStream out = new FileOutputStream(filename);
        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
        out.close();
    }
}
