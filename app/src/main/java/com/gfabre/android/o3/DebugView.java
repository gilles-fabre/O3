package com.gfabre.android.o3;

import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.gfabre.android.utilities.widgets.GenericDialog;

import java.util.ArrayList;

/**
 * This class provides support for a modal, synchronous, debug dialog which can
 * be used to control a script execution (through a state automaton in the script
 * automation engine).
 *
 * The debug dialog must be instantiated, then it's updateDebugInfo/show called everytime
 * a new instruction was interpreted, and the dialog's hide method is eventually called.
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class DebugView implements GenericDialog.GenericDialogListener {
    private  static final int DEBUG_DIALOG_ID = R.layout.debug_view;

    private DebugState          mState;
    private final GenericDialog mDialog;
    private CalculatorActivity  mActivity;
    private String              mVariables = "";
    private String              mScript = "";
    private String              mStack = "";
    private int                 mScriptLine;
    private ListView            mScriptList = null;
    private boolean             mIsShown;
    private boolean             mInInnerLoop;

    private ArrayAdapter<String> mStackAdapter = null;
    private ArrayAdapter<String> mScriptAdapter = null;
    private ArrayAdapter<String> mVariablesAdapter = null;

    public enum DebugState {
        none,
        step_in,
        step_over,
        step_out,
        exit
    }

    DebugView(CalculatorActivity activity) {
        mActivity = activity;
        mDialog = new GenericDialog(DEBUG_DIALOG_ID, null, true, activity.getString(R.string.resume));
        mDialog.setListener(this);
        mState = DebugState.none;
    }

    public void updateDebugInfo(int scriptLine, String script, String variables, String stack) {
        mScript = script;
        mVariables = variables;
        mStack = stack;
        mScriptLine = scriptLine;
    }

    public void show() {
        if (!mIsShown) {
            mDialog.show(mActivity.getFragmentManager(), mActivity.getString(R.string.debug_script));
            mIsShown = true;
        } else {
            // here, we won't go through onInitialize again, so
            // updates the lists/selection
            if (mStackAdapter != null &&
                mScriptAdapter != null &&
                mScriptList != null &&
                mVariablesAdapter != null)
            populateListView(mStackAdapter, mStack);
            populateListView(mScriptAdapter, mScript);
            mScriptList.setItemChecked(mScriptLine, true);
            mScriptList.setSelection(mScriptLine);
            populateListView(mVariablesAdapter, mVariables);
        }
        try{
            enterInnerLoop();
        } catch (RuntimeException e) {
            System.out.println(mActivity.getString(R.string.exit_show_on) + e.getMessage());
        }
    }

    public void hide() {
        mDialog.dismiss();
    }

    /**
     * Fills the list view with the text lines passed in elements
     */
    private void populateListView(ArrayAdapter<String> adapter, String lines)  {
        if (adapter == null || lines == null)
            return;

        adapter.clear();
        String []lineArray = lines.split("\n");
        for (String line : lineArray)
            adapter.add(line);

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
    }

    /**
     * called upon dialog show, will initialize the various references to the dialog lists and adapters,
     * and populate the lists.
     *
     * @param Id is the dialog Id
     * @param dialog is the associated GenericDialog
     * @param view is the associated view
     */
    @Override
    public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
        final GenericDialog _dialog = dialog;

        ListView list = (ListView)dialog.getField(R.id.stack);
        if (list != null)  {
            mStackAdapter = new ArrayAdapter<>(mActivity, R.layout.simple_row, new ArrayList<String>());
            list.setAdapter(mStackAdapter);
            populateListView(mStackAdapter, mStack);
        }
        mScriptList = (ListView)dialog.getField(R.id.script);
        if (mScriptList != null)  {
            mScriptList.setEnabled(false);
            mScriptList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mScriptAdapter = new ArrayAdapter<>(mActivity, R.layout.simple_row, new ArrayList<String>());
            mScriptList.setAdapter(mScriptAdapter);
            populateListView(mScriptAdapter, mScript);
            mScriptList.setItemChecked(mScriptLine, true);
            mScriptList.setSelection(mScriptLine);
        }
        list = (ListView)dialog.getField(R.id.variables);
        if (list != null)  {
            mVariablesAdapter = new ArrayAdapter<>(mActivity, R.layout.simple_row, new ArrayList<String>());
            list.setAdapter(mVariablesAdapter);
            populateListView(mVariablesAdapter, mVariables);
        }

        Button button = (Button)dialog.getField(R.id.button_STEP_IN);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mState = DebugState.step_in;
                    exitInnerLoop();
                }
            });
        }

        button = (Button)dialog.getField(R.id.button_STEP_OVER);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mState = DebugState.step_over;
                    exitInnerLoop();
                }
            });
        }

        button = (Button)dialog.getField(R.id.button_STEP_OUT);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mState = DebugState.step_out;
                    exitInnerLoop();
                }
            });
        }

        button = (Button)dialog.getField(R.id.button_EXIT);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mState = DebugState.exit;
                    exitInnerLoop();
                }
            });
        }
    }

    /**
     * Called when the user hits the 'done' button. This resumes the script execution. So
     * just change the debug state to 'none'. The dialog is dismissed.
     *
     * @param Id is the dialog Id
     * @param dialog is the associated GenericDialog
     * @param view is the associated view
     *
     * return true, which means the user has tapped the 'done'/'ok' button
     */
    @Override
    public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
        mState = DebugState.none;
        return true;
    }

    /**
     * Enters a GUI thread inner event distribution loop, which makes the dialog 'blocking'
     */
    private void enterInnerLoop() {
        mInInnerLoop = true;
        Looper.loop();
    }

    /**
     * Exits the GUI thread inner event distribution loop, if in there
     */
    private void exitInnerLoop() {
        if (mInInnerLoop) {
            mDialog.getHandler().sendMessage(mDialog.getHandler().obtainMessage());
            mInInnerLoop = false;
        }
    }

    /**
     * Called upon dialog closure. Must exit the inner loop (we should be in at this point).
     *
     * @param Id is the dialog Id
     * @param dialog is the associated GenericDialog
     * @param view is the associated view
     */
    @Override
    public void onDismiss(int Id, GenericDialog dialog, View view) {
        mIsShown = false;
        exitInnerLoop();
    }

    public DebugState getDebugState() {
        return mState;
    }

    public boolean isShown() {
        return mIsShown;
    }
}
