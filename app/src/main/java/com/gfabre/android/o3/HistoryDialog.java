package com.gfabre.android.o3;

import android.view.View;
import android.widget.EditText;

import com.gfabre.android.utilities.widgets.GenericDialog;

/**
 * A simple modal dialog used to edit/[save] history.
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class HistoryDialog implements GenericDialog.GenericDialogListener {
    public static final int HISTORY_DIALOG_ID = R.layout.history_dialog;

    GenericDialog           mDialog;
    CalculatorActivity      mActivity;
    Calculator              mCalculator;
    String                  mHistory;

    HistoryDialog(CalculatorActivity activity, Calculator calculator, String history) {
        mCalculator = calculator;
        mActivity = activity;
        mHistory = history;
        mDialog = new GenericDialog(HISTORY_DIALOG_ID, null, false, mActivity.getString(R.string.apply), mActivity.getString(R.string.cancel));
        mDialog.setListener(this);
        mDialog.show(activity.getFragmentManager(), activity.getString(R.string.edit_script));
    }

    @Override
    public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
        EditText text = (EditText)mDialog.getField(R.id.text);
        text.setText(mHistory);
    }

    @Override
    public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
        EditText text = (EditText)mDialog.getField(R.id.text);
        mCalculator.setHistory(text.getText().toString());
        return true;
    }

    @Override
    public void onDismiss(int mDialogId, GenericDialog mFragment, View mView) {
    }
}
