package com.gfabre.android.utilities.widgets;

public class FileItem implements Comparable<FileItem> {
    private String mName;
    private String mData;
    private String mDate;
    private String mPath;
    private String mImage;
   
    FileItem(String name, String data, String date, String path, String img) {
        mName = name;
        mData = data;
        mDate = date;
        mPath = path;
        mImage = img;           
    }
    
    public String getName() {
        return mName;
    }
    
    String getData() {
        return mData;
    }
    
    public String getDate() {
        return mDate;
    }
    
    String getPath() {
        return mPath;
    }
    
    String getImage() {
        return mImage;
    }

    public int compareTo(FileItem o) {
        if( mName != null)
            return mName.toLowerCase().compareTo(o.getName().toLowerCase());
        else
            throw new IllegalArgumentException();
    }
}