package com.gfabre.android.utilities.widgets;

import android.app.Activity;
import android.widget.Toast;

/**
 * Use this class to display messages in a separate thread
 * 
 * @author gilles fabre
 * @date March, 2014
 */
public class MessageDisplayer implements Runnable {
	String 		mMessage;
	int 		mDuration; // -1 == dialog, 0 == short toast, 1 == long toast
	Activity 	mActivity;
	
	/**
	 * Message displayer constructor.
	 * 
	 * @param activity is the calling activity
	 * @param message is the message to be displayed
	 * @param duration is -1 if a dialog must be used, 0 if a short toast message,
	 * 		  1 if a long toast message.
	 */
	public MessageDisplayer(Activity activity, String message, int duration) {
		mActivity = activity;
		mMessage = message;
		mDuration = duration;
	}
	
	@Override
	public void run() {
		if (mDuration >= 0 )
			Toast.makeText(mActivity, mMessage, mDuration == 0 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
		else
			GenericDialog.displayMessage(mActivity, mMessage);
	}
}
