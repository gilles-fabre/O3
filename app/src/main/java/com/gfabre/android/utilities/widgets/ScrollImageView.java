package com.gfabre.android.utilities.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * A scrollable Image View for Android.
 * 
 * @author gilles fabre
 * @date February, 2014
 */
public class ScrollImageView extends View {
	//private Display mDisplay;
	private View    mParent;
	
	private Bitmap 	mImage;
	private Paint	mPaint;

	/* Current x and y of the touch */
	private float mCurrentX = 0;
	private float mCurrentY = 0;

	private float mOffsetX = 0;
	private float mOffsetY = 0;
	
	/* The touch distance change from the current touch */
	private float mDeltaX = 0;
	private float mDeltaY = 0;

	public ScrollImageView(Context context) {
		super(context);
		initScrollImageView(context);
		mPaint = new Paint(Paint.DITHER_FLAG);
	}

	private void initScrollImageView(Context context) {
		mParent = (View)getParent();
	}
	
	protected int getOffsetX() {
		return (int)mOffsetX;
	}

	protected int getOffsetY() {
		return (int)mOffsetY;
	}
	
	protected void resetOffsets() {
		mOffsetX = mOffsetY = 0;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (mParent != null) {
			int width = mParent.getWidth();
			int height = mParent.getHeight();
			setMeasuredDimension(width, height);
		} else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}
	
	public Bitmap getImage() {
		return mImage;
	}

	public void setImage(Bitmap image) {
		mImage = image;
	}

	/**
	 * Follow the user movement and store the current touch coordinate
	 * as well as the offset from the last initial touch when moving.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event == null)
			return false;
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mCurrentX = event.getRawX();
				mCurrentY = event.getRawY();
				break;
				
					
			case MotionEvent.ACTION_MOVE:
				float x = event.getRawX();
				float y = event.getRawY();
	
				// Update how much the touch moved
				mDeltaX = x - mCurrentX;
				mDeltaY = y - mCurrentY;
	
				mCurrentX = x;
				mCurrentY = y;

				float newOffsetX = mOffsetX + mDeltaX;
				// Don't scroll off the left or right edges of the bitmap.
				if (newOffsetX <= 0 && mImage.getWidth() + newOffsetX > getMeasuredWidth())
					mOffsetX += mDeltaX;
		
				float newOffsetY = mOffsetY + mDeltaY;
				// Don't scroll off the top or bottom edges of the bitmap.
                if (newOffsetY <= 0 && mImage.getHeight() + newOffsetY > getMeasuredHeight())
					mOffsetY += mDeltaY;
				
				invalidate();
				break;
				
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				mCurrentX = mCurrentY = mDeltaX = mDeltaY = 0;
				break;
		}
		
		// consumed event
		return true;
	}

	/**
	 * Draw the bitmap starting at the new offset.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		if (mImage == null || canvas == null) 
			return;

		canvas.drawBitmap(mImage, mOffsetX, mOffsetY, mPaint);
	}
}