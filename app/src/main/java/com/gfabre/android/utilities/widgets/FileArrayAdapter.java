package com.gfabre.android.utilities.widgets;

import java.util.List; 

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.gfabre.android.o3.R;

/**
 * This class describes a specialized adapter for a file list.
 */
public class FileArrayAdapter extends ArrayAdapter <FileItem> {
   private Activity 		mActivity;
   private int 				mResId;
   private List<FileItem>	mItems;
  
   FileArrayAdapter(Activity activity, int viewResourceId, List<FileItem> objects) {
       super(activity, viewResourceId, objects);
       mActivity = activity;
       mResId = viewResourceId;
       mItems = objects;
   }

   public FileItem getItem(int i) {
	   return mItems.get(i);
   }
    
   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
	   View view = convertView;
	   if (view == null) {
		   LayoutInflater vi = (LayoutInflater)mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		   view = vi.inflate(mResId, null);
	   }
         
	   final FileItem item = mItems.get(position);
	   if (item != null) {
		   TextView name = (TextView)view.findViewById(R.id.fd_name);
		   TextView data = (TextView)view.findViewById(R.id.fd_data);
		   TextView date = (TextView)view.findViewById(R.id.fd_date);

		   // take the ImageView from layout and set the image
           ImageView imageView = (ImageView) view.findViewById(R.id.fd_icon);
           if (imageView != null) { 
	           String uri = "drawable/" + item.getImage();
	           int imageResource = mActivity.getResources().getIdentifier(uri, null, mActivity.getPackageName());
	           Drawable image = mActivity.getResources().getDrawable(imageResource);
	           imageView.setImageDrawable(image);
           }
           
           if (name != null)
        	   name.setText(item.getName());
          
           if (data != null)
               data.setText(item.getData());
          
           if (date != null)
        	   date.setText(item.getDate());
	   }
       
	   return view;
   }
}