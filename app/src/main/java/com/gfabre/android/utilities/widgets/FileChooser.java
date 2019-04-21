package com.gfabre.android.utilities.widgets;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.gfabre.android.utilities.widgets.GenericDialog.GenericDialogListener;
import com.gfabre.android.o3.R;

/**
 * A simple file chooser.
 * 
 * @author gilles fabre
 * @date   May 14, 2014	
 */
public class FileChooser implements GenericDialogListener {
	private static final int FILE_CHOOSER_DIALOG_ID = R.layout.file_chooser;
	
	private static final String EMPTY_DIR_ICON 	= "empty_directory_icon";
	private static final String DIR_ICON 		= "directory_icon";
	private static final String FILE_ICON 		= "file_icon";
	private static final String UP_ICON 		= "up_icon";

	public static final String FILENAME = "Filename";
	
	private String 				mFilter; 			// filter displayed files based on this.
	private File   				mCurrentFile;		// current directory
    private FileArrayAdapter 	mAdapter;			// files array adapter
	private Activity			mActivity;			// calling activity
	private GenericDialog 		mDialog;  			// chooser dialog
	private ListView 			mList;				// files listview
	
	public FileChooser(int Id, Activity activity, String filter, String dir) {
		mFilter = filter;
		mCurrentFile = dir == null ? new File("/") : new File(dir);
		mActivity = activity;
		
		mDialog = new GenericDialog(FILE_CHOOSER_DIALOG_ID, activity.getString(R.string.pick_script), false);
		mDialog.setDialogId(Id);
		mDialog.setListener(this);
		mDialog.show(activity.getFragmentManager(), activity.getString(R.string.pick_script));
	}

	/**
	 * Goes through the current directory and populates the files list.
	 */
	private void populate() {
		File[]dirs = mCurrentFile.listFiles();
	    mDialog.getDialog().setTitle(mCurrentFile.getAbsolutePath());
	    List<FileItem> dir = new ArrayList<>();
	    List<FileItem> fls = new ArrayList<>();
        try{
        	for (File ff: dirs) {
                Date 		lastModDate = new Date(ff.lastModified());
                DateFormat 	formater = DateFormat.getDateTimeInstance();
                String date_modify = formater.format(lastModDate);
                if (ff.isDirectory()) {
                    File[] fbuf = ff.listFiles();
                    int buf = 0;
                    if (fbuf != null) 
                        buf = fbuf.length;
                    String num_item = String.valueOf(buf);
                    if (buf <= 1) 
                    	num_item = num_item + " item";
                    else 
                    	num_item = num_item + " items";
                   
                    dir.add(new FileItem(ff.getName(), 
                    					 num_item, 
                    					 date_modify, 
                    					 ff.getAbsolutePath(), 
                    					 buf == 0 ? EMPTY_DIR_ICON : DIR_ICON));
                } else {
                	String name = ff.getName();
                	// filter based on extension
                	int i = name.lastIndexOf(".");
                	String extension = i == -1 ? "" : name.substring(i);
                	if (mFilter == null || 
                		mFilter.isEmpty() || 
                		(!extension.isEmpty() && mFilter.contains(extension))) 
                		
                		// add the file
	                	fls.add(new FileItem(name,
	                						 Long.valueOf(ff.length()).toString(), 
	                						 date_modify, 
	                						 ff.getAbsolutePath(), 
	                						 FILE_ICON));
                }
            }
        } catch(Exception e) {
        	// do not get intrusive, that would be useless
        }

        Collections.sort(dir);
        Collections.sort(fls);
        dir.addAll(fls);
        if (!mCurrentFile.getPath().equals("/"))
        	dir.add(0,new FileItem("..", "", "", mCurrentFile.getParent(), UP_ICON));

        mAdapter = new FileArrayAdapter(mActivity, R.layout.file_row, dir);
        ListView list = (ListView)mDialog.getField(R.id.file_list);
        if (list != null) {
    		// set the selection type and the adapter
    		list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    		list.setAdapter(mAdapter);
        }
    }
	
	@Override
	public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
		// call the activity listener
		((GenericDialogListener)mActivity).onDialogPositiveClick(Id, dialog, view);
	}

	@Override
	public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
		mCurrentFile = null;
	}

	@Override
	public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
		populate();
        mList = (ListView)mDialog.getField(R.id.file_list);
        if (mList != null) {
        	mList.setOnItemClickListener(new OnItemClickListener() {

        		@Override
        		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        			FileItem item = mAdapter.getItem(position);
					if (item == null || item.getPath().equals("/")) // from Android 7, can't list files in root
                    	return;
                    mCurrentFile = new File(item.getPath());
                    String image = item.getImage();
	                if (image.equals(DIR_ICON) ||
		                image.equals(EMPTY_DIR_ICON) || 
	                    image.equals(UP_ICON)) 
                        populate();
	                else 
	                	mList.setItemChecked(position, true);
        		}
        	});
        }
	}

	@Override
	public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
		String filename = null;
		
		try {
			if (mCurrentFile != null && mCurrentFile.isFile()) {
				filename = mCurrentFile.getCanonicalPath();
			
				// save the selected filename in the bundle
				mDialog.getBundle().putString(FILENAME, filename);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return filename != null;
	}

	@Override
	public void onDismiss(int Id, GenericDialog dialog, View mView) {
	}
}
