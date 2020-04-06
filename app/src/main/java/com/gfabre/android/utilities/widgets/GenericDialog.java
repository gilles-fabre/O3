package com.gfabre.android.utilities.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gfabre.android.o3.R;

import static com.gfabre.android.o3.R.layout.message_dialog;
import static com.gfabre.android.o3.R.layout.prompt_dialog;

/**
 * Android dialogs are a nightmare. This class makes it easy to create
 * and use a dialog by passing it an xml layout description of the dialog content
 * and receiving the initialize/ok/cancel events.
 * 
 * @author gilles fabre
 * @date February, 2014
 */
public class GenericDialog extends DialogFragment {

	private static final String DIALOG_ID = "DialogId";

	private static final int 	MESSAGE_DIALOG_ID = message_dialog;
	private static final String MESSAGE_DIALOG_TAG = "Message Dialog";
    private static final int 	PROMPT_DIALOG_ID = prompt_dialog;


    private int			 		mLayout;
	private GenericDialog 		mFragment;
	private View				mView;
	private String				mTitle;
	private boolean				mResult;		// true when positive click, false else
	private boolean				mSingleButton;
	private AlertDialog			mDialog;
	private int					mDialogId;
	private String				mPositiveButtonText = "Ok";
	private String				mNegativeButtonText = "Cancel";
	private Bundle				mBundle;		// to pass data to the dialog and hence to
												// the callbacks

	public GenericDialog() {
		super();
		mFragment = this;
		mLayout = android.R.layout.simple_list_item_1;
		mTitle = "";
		mSingleButton = true;
		mDialogId = 0;
		mBundle = new Bundle();
		setRetainInstance(true); // don't dismiss dialog on rotation.

		try {
			mHandler = new Handler()
			{
				@Override
				public void handleMessage(Message mesg)
				{
					throw new RuntimeException();
				}
			};
		} catch (Exception e) {
			// nop
		}
	}

	@SuppressLint("ValidFragment")
	public GenericDialog(int layout, String title, boolean singleButton, String ... buttonTexts) {
		super();		
		mFragment = this;
		mLayout = layout;
		mTitle = title;
		mSingleButton = singleButton;
		mDialogId = layout;
		mBundle = new Bundle();
		if (buttonTexts != null) {
		    if (buttonTexts[0] != null)
			    mPositiveButtonText = buttonTexts[0];
		    if (buttonTexts.length > 1 && buttonTexts[1] != null)
			    mNegativeButtonText = buttonTexts[1];
		}
		setRetainInstance(true); // don't dismiss dialog on rotation.

		try {
			mHandler = new Handler()
			{
				@Override
				public void handleMessage(Message mesg)
				{
					throw new RuntimeException();
				}
			};
		} catch (Exception e) {
			// nop
		}
	}

	@SuppressLint("ValidFragment")
	public GenericDialog(int layout, String title, boolean singleButton) {
		super();		
		mFragment = this;
		mLayout = layout;
		mTitle = title;
		mSingleButton = singleButton;
		mDialogId = layout;
		mBundle = new Bundle();
		setRetainInstance(true); // don't dismiss dialog on rotation.

		try {
			mHandler = new Handler()
			{
				@Override
				public void handleMessage(Message mesg)
				{
					throw new RuntimeException();
				}
			};
		} catch (Exception e) {
			// nop
		}
	}

	/**
	 * Use the bundle to set data for the listener callbacks
	 */
	public Bundle getBundle() {
		return mBundle;
	}
	
	/**
	 * In case we use the same layout for different dialogs...
	 *
	 */
	public void setDialogId(int Id) {
		mDialogId = Id;
	}
	
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface GenericDialogListener {
        void onDialogPositiveClick(int Id, GenericDialog dialog, View view);
        void onDialogNegativeClick(int Id, GenericDialog dialog, View view);
        void onDialogInitialize(int Id, GenericDialog dialog, View view);
        boolean onDialogValidate(int Id, GenericDialog dialog, View view);
		void onDismiss(int mDialogId, GenericDialog mFragment, View mView);
    }
    
    // Use this instance of the interface to deliver action events
    GenericDialogListener mListener;
    
    // Override the Fragment.onAttach() method to instantiate the GenericDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mListener == null) {
	        // Verify that the host activity implements the callback interface
	        try {
	            // Instantiate the GenericDialogListener so we can send events to the host
	            mListener = (GenericDialogListener)activity;
	        } catch (ClassCastException e) {
	            // The activity doesn't implement the interface, throw exception
	            throw new ClassCastException(activity.toString()
	                    + " must implement GenericDialogListener");
	        }
        }
    }

    // Force a new listener
    public void setListener(GenericDialogListener listener) {
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
        	mDialogId = savedInstanceState.getInt(DIALOG_ID);
        	return mDialog;
        }
        
    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    	
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        if (inflater == null)
        	return null;
        
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        mView = inflater.inflate(mLayout, null);
        builder.setView(mView);
        
        // Add action buttons
        builder.setPositiveButton(mPositiveButtonText, new DialogInterface.OnClickListener() {
        	@Override
	       	public void onClick(DialogInterface dialog, int id) {
        		// this one won't be called, since we re set it later on...
	       	}
	   });
        
        if (!mSingleButton) {
	       builder.setNegativeButton(mNegativeButtonText, new DialogInterface.OnClickListener() {
	    	   	public void onClick(DialogInterface dialog, int id) {
	    	   		if (mListener != null)
	    	   			mListener.onDialogNegativeClick(mDialogId, mFragment, mView);
	            }
	       });      
        }
        
        mDialog = builder.create();
        if (mDialog != null) {
        	mDialog.setTitle(mTitle);
        	mDialog.setCanceledOnTouchOutside(false); // this default behavior is a p.i.t.a...
        }
        
        return mDialog;
    }
    
	/**
	 * Save the dialog id on pause..
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		if (outState != null)
			outState.putInt(DIALOG_ID, mDialogId);
	}

	/**
	 * Weird.. seems to fix a bug in the compatibility library :/
	 */
	 @Override
	 public void onDestroyView() {
	     if (getDialog() != null && getRetainInstance())
	         getDialog().setDismissMessage(null);
	         super.onDestroyView();
	 }
	 
	 /**
	  * Set the positive button click listener once the button EXISTS!
	  */
	 @Override
	 public void onResume() {
		 super.onResume();
		 
	    // add a listener to validate the dialog when the ok button is pressed.
	    Button positiveButton = mDialog.getButton(Dialog.BUTTON_POSITIVE);
	    if (positiveButton != null) 
	        positiveButton.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View view) {
	                // if the listener is ok, close the dialog
	                if (mListener != null &&
	                	mListener.onDialogValidate(mDialogId, mFragment, mView)) {
	                    dismiss();
	                	mListener.onDialogPositiveClick(mDialogId, mFragment, mView);
	    	           	mResult = true;
	                }
	            }
	        });
	 }
	 
	 /**
     * Invoke the listener activity to get populated
     */
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	View view = null;
    	
    	if (savedInstanceState == null) {
	    	view = super.onCreateView(inflater, container, null);
			if (mListener != null)
				mListener.onDialogInitialize(mDialogId, mFragment, mView);
    	}
		
    	return view;
	}
    
    /**
     * Invoke the listener if the dialog is dismissed
     */
    @Override 
    public void onDismiss(DialogInterface dialog) {
		if (mListener != null)
			mListener.onDismiss(mDialogId, mFragment, mView);

    	super.onDismiss(dialog);
    }

    /**
     * Field accessor.
     */
    public View getField(int id) {
    	return mView == null ? null : mView.findViewById(id);
    }

    public final LinearLayout getLayout() {
    	return (LinearLayout)mView;
    }
    
    /**
     * Get the button pressed: true if positive false else.
     */
    public boolean getResult() {
    	return mResult;
    }

    /**
     * The mHandler which throws the exception used to get out of the event looper inner loop in our
     * modal dialogs. The String that receives the user input upon promptMessage.
     */
    private String mUserInput = null;
    private Handler mHandler = null;

    /**
	 * Utility method to display a modal and SYNCHRONOUS persistent message dialog.
	 * @param activity is the parent activity
	 * @param msg is the message to display
	 */
	public static void displayMessage(Activity activity, String msg) {
         final String 	_msg = msg;

         final GenericDialog _dialog = new GenericDialog(MESSAGE_DIALOG_ID, null, true);
         _dialog.setListener(new GenericDialogListener() {
                @Override
                public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
                }

                @Override
                public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
                }

                @Override
                public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
                    TextView text = (TextView)dialog.getField(R.id.message);
                    if (text != null)
                        text.setText(_msg);
                }

                @Override
                public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
                    _dialog.mHandler.sendMessage(_dialog.mHandler.obtainMessage());
                    return true;
                }

                @Override
                public void onDismiss(int Id, GenericDialog dialog,
                                      View mView) {
                }
            });

         _dialog.show(activity.getFragmentManager(), MESSAGE_DIALOG_TAG);
        try{
            Looper.loop();
        } catch (RuntimeException e) {
            System.out.println("exiting on" + e.getMessage());
        }
	}

	/**
	 * Utility method to display a modal and SYNCHRONOUS persistent message dialog
	 * and return the user input as a String.
	 *
	 * @param activity is the parent activity
	 * @param inputType is the user expected input type (EditText InputType)
	 * @param msg is the message to display
	 * @param value is the default input value (can be null)
	 */
	public static String promptMessage(Activity activity, int inputType, String msg, String value) {
		final String    _value = value;
		final String 	_msg = msg;
		final int       _inputType = inputType;

		final GenericDialog _dialog = new GenericDialog(PROMPT_DIALOG_ID, null, true);
		_dialog.setListener(new GenericDialogListener() {
			@Override
			public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
			}

			@Override
			public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
			}

			@Override
			public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
				TextView text = (TextView)dialog.getField(R.id.message);
				if (text != null)
					text.setText(_msg);
				EditText inputField = (EditText)dialog.getField(R.id.input);
				if (inputField != null) {
					inputField.setInputType(_inputType);
					if (_value != null)
						inputField.setText(_value);
				}
			}

			@Override
			public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
				_dialog.mHandler.sendMessage(_dialog.mHandler.obtainMessage());
				EditText inputField = (EditText)dialog.getField(R.id.input);
				if (inputField != null)
					_dialog.mUserInput = inputField.getText().toString();
				return true;
			}

			@Override
			public void onDismiss(int mDialogId, GenericDialog mFragment,
								  View mView) {
			}
		});

		_dialog.show(activity.getFragmentManager(), MESSAGE_DIALOG_TAG);
		try{
			Looper.loop();
		} catch (RuntimeException e) {
			System.out.println("exiting on" + e.getMessage());
		}

		return _dialog.mUserInput;
	}
}
