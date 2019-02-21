package com.gfabre.android.o3;

import android.view.View;
import android.widget.EditText;

import com.gfabre.android.utilities.widgets.GenericDialog;

import java.io.File;
import java.io.IOException;

/**
 * A simple modal dialog used to edit/[save] a script.
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class EditScriptDialog implements GenericDialog.GenericDialogListener {
    public static final int EDIT_DIALOG_ID = R.layout.edit_dialog;

    GenericDialog       mDialog;
    CalculatorActivity  mActivity;
    String              mFilename;

    EditScriptDialog(CalculatorActivity activity, String filename) {
        mActivity = activity;
        mFilename = filename;
        mDialog = new GenericDialog(EDIT_DIALOG_ID, null, false, mActivity.getString(R.string.save), mActivity.getString(R.string.cancel));
        mDialog.setListener(this);
        mDialog.show(mActivity.getFragmentManager(), activity.getString(R.string.edit_script));
    }

    @Override
    public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
        EditText text = (EditText)mDialog.getField(R.id.filename);
        text.setText(mFilename);
        try {
            String script = mActivity.readFile(mFilename);
            text = (EditText)mDialog.getField(R.id.text);
            text.setText(script);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
        EditText text = (EditText)mDialog.getField(R.id.filename);
        String newFilename = text.getText().toString();
        String pathStr = newFilename.contains("/") ? newFilename.substring(0, newFilename.lastIndexOf("/") + 1) : "";
        if (pathStr.equals("") || !new File(pathStr).exists()) {
            mActivity.doDisplayMessage(mActivity.getString(R.string.invalid_path) + pathStr);
            text.setText(mFilename);
            return false;
        }
        text = (EditText)mDialog.getField(R.id.text);
        return mActivity.writeFile(newFilename, text.getText().toString());
    }

    @Override
    public void onDismiss(int mDialogId, GenericDialog mFragment, View mView) {
    }
}
