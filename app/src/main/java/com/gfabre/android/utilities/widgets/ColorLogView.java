package com.gfabre.android.utilities.widgets;

import android.annotation.SuppressLint;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

// TODO: Use a WebView. The TextView doesn't support the font size...
public class ColorLogView {
	public  static final int SMALL_FONT = 0;
	public  static final int MEDIUM_FONT = 1;
	public  static final int BIG_FONT = 2;
	
    private static final int DEFAULT_FONT_SIZE = MEDIUM_FONT;
    
	private TextView mTextView;
	private String   mHtmlText;
	private int		 mFontSize;
	
	public ColorLogView(TextView textView) {
		mTextView = textView;
		mTextView.setLinksClickable(true);
		mTextView.setAutoLinkMask(BIG_FONT);
		mTextView.setMovementMethod(LinkMovementMethod.getInstance());
		mHtmlText = "";
		mFontSize = DEFAULT_FONT_SIZE;
	}
	  /**
     * Appends the msg text at the end of the current log, using the
     * specified color, and a bold font if the bold argument is set.
     * 
     * @param msg   the text to append to the log
     * @param color the color to use
     * @param bold  use a bold font if set
     */
    @SuppressLint("DefaultLocale")
	public final void appendText(String msg, int color, boolean bold) {
    	if (mHtmlText == null || mTextView == null)
    		return;
    	
        String colorString = Integer.toHexString(color).toUpperCase();
        String page = "<font color=\"#" + colorString + "\">";
        switch (mFontSize) {
        	case SMALL_FONT:
        		page += "<small>";
        		break;
        		
        	case BIG_FONT:
        		page += "<big>";
        		break;
        }

        if (bold)
            page += "<b>";
        page += msg;
        if (bold)
            page += "</b>";
        
        page += "</font>";
        
        page = page.replace("\n", "<br>");

        mHtmlText += page;
        mTextView.append(Html.fromHtml(page));
    }
    
    /**
     * Get the log content.
     */
    public String getText() {
    	return mHtmlText;
    }
    
    /**
     * Set the log content.
     */
    public void setText(String text) {
    	mHtmlText = text;
    	if (mTextView != null)
    		mTextView.setText(Html.fromHtml(mHtmlText));
    }

    /**
     * Clears the log 
     */
    public void clear() {
    	mHtmlText = "";
        if (mTextView != null)
        	mTextView.setText(null);
    }
    
    /**
     * Font size manipulation
     */
    public int getFontSize() {
    	return mFontSize;
    }

    public void setFontSize(int fontSize) {
    	mFontSize = fontSize;
    }

    public void resetFontSize() {
    	mFontSize = DEFAULT_FONT_SIZE;
    }
}
