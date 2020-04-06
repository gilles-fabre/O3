package com.gfabre.android.o3;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.gfabre.android.utilities.widgets.ColorLogView;
import com.gfabre.android.utilities.widgets.FileChooser;
import com.gfabre.android.utilities.widgets.GenericDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.Semaphore;

/**
 * The main RPN calculator activity. Handles the value input field, the values stack
 * and the num + basic functions keypad.
 *
 * @author gilles fabre
 * @date December, 2018
 */
public class CalculatorActivity extends AppCompatActivity implements GenericDialog.GenericDialogListener {

    private static final int        RUN_SCRIPT_DIALOG_ID = 1;
    private static final int        DEBUG_SCRIPT_DIALOG_ID = 2;
    private static final int        EDIT_SCRIPT_DIALOG_ID = 3;
    private static final int        INIT_SCRIPT_DIALOG_ID = 6;
    private static final int        HELP_DIALOG_ID = R.layout.log_view;
    private static final int        GRAPH_DIALOG_ID = R.layout.graph_view;
    private static final String     SCRIPT_EXTENSIONS = ".o3s .txt";
    private static final String     INIT_SCRIPT_NAME = "InitScriptFilename";

    private static final String     FUNCTION_SCRIPTS_KEY = "FunctionScripts";
    private static final String     FUNCTION_TITLES_KEY = "FunctionTitles";
    private static final String     STACK_CONTENT_KEY = "StackContent";
    private static final String     EDITED_VALUE_KEY = "EditedValue";
    private static final String     HISTORY_SCRIPT_KEY = "HistoryScript";
    private static final String     HISTORY_SCRIPT_NAME = "HistoryScript.o3s";

    private static final int        NUM_FUNC_BUTTONS = 15;

    private static final int        DISPLAY_PROGRESS_MESSAGE = 0;
    private static final int        UPDATE_STACK_MESSAGE = 1;
    private static final int        DISPLAY_MESSAGE = 2;
    private static final int        PROMPT_MESSAGE = 3;

    private static final int        SHOW_DEBUG_VIEW = 4;
    private static final int        HIDE_DEBUG_VIEW = 5;

    private static final int        SCRIPT_ENGINE_PRIORITY = Process.THREAD_PRIORITY_URGENT_AUDIO;
    private static final int        UI_YIELD_MILLISEC_DELAY = 3; // needed time for the UI to get scheduled :/

    private static Method[] mMethods = null;

    private static CalculatorActivity mActivity;                // this reference

    private String        mHistory = "";                        // all actions history from beginning of time.
    private Stack<Double> mStack = new Stack<>();               // values stack
    private String        mValue = "";                          // value currently edited
    private EditText      mValueField = null;                   // value edit field
    private ListView      mStackView = null;                    // stack view
    private ArrayAdapter  mStackAdapter = null;                 // stack view adapter
    private GraphView     mGraphView = null;                    // canvas for graphical functions
    private Menu          mScriptFunctionsMenu = null;          // dynamic script functions menu
    private String        mInitScriptName = null;               // init script, if set, run upon calculator start

    private String        mFunctionScripts[] = new String[NUM_FUNC_BUTTONS];
    private String        mFunctionTitles[] = new String[NUM_FUNC_BUTTONS];

    /**
     * All interactions with the UI must be done from the main UI thread, hence
     * thru a message handler, invoked from the background thread running the scripts.
     */
    private Handler       mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case DISPLAY_PROGRESS_MESSAGE:
                    // display script progress
                    mValueField.setText((String)inputMessage.obj);
                    break;

                case UPDATE_STACK_MESSAGE:
                    // update the stack
                    updateStackView();
                    break;

                case DISPLAY_MESSAGE:
                    // display a dialog with a message
                    GenericDialog.displayMessage(mActivity, (String)inputMessage.obj);
                    break;

                case PROMPT_MESSAGE:
                    // prompt the user to enter a value
                    PromptForValueMessage prompt = (PromptForValueMessage)inputMessage.obj;
                    prompt.mValue =
                            Double.valueOf(GenericDialog.promptMessage(mActivity,
                                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                                    prompt.mMessage,
                                    null));

                    prompt.mWaitForValue.release();
                    break;

                case SHOW_DEBUG_VIEW:
                    // show the dialog
                    getDebugView().show();
                    ((Semaphore)(inputMessage.obj)).release();
                    break;

                case HIDE_DEBUG_VIEW:
                    // hide the dialog
                    getDebugView().hide();
                    break;

                default:
                    /*
                     * Pass along other messages from the UI
                     */
                    super.handleMessage(inputMessage);
            }
        }
    };

    public boolean hasValueOnStack() {
        return !mStack.isEmpty();
    }

    public void doPushValueOnStack(Double value) {
        mStack.push(value);
    }

    public Double doPopValueFromStack() {
        return mStack.isEmpty() ? Double.NaN : mStack.pop();
    }

    public Double doPeekValueFromStack() {
        return mStack.isEmpty() ? Double.NaN : mStack.peek();
    }

    /**
     * If present, pushes the currently edited value onto the stack.
     */
    private void pushValueOnStack() {
        if (mValue.isEmpty())
            return;

        // make sure we're pushing a properly formatted double
        try {
            mHistory += mValue + "\n";
            mStack.push(Double.valueOf(mValue));
            updateStackView();
        } catch (Exception e) {
            // there ain't much we can do here
            GenericDialog.displayMessage(mActivity, getString(R.string.invalid_number) + mValue);
        } finally {
            mValue = "";
            mValueField.setText(mValue);
        }
    }

    /**
     * Fills the stack view with the values currently held on the computation stack
     */
    private void updateStackView()  {
        if (mStackView == null)
            return;

        mStackAdapter.clear();
        int depth = 1;
        Object[] values = mStack.toArray();
        for (int i = values.length - 1; i >= 0; i--) {
            mStackAdapter.add(depth + ": " + values[i].toString());
            depth++;
        }
        mStackAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mScriptFunctionsMenu == null)
            return false;

        mScriptFunctionsMenu.clear();
        String[] functions = ScriptEngine.getFunctions();
        for (final String f : functions) {
            MenuItem item = mScriptFunctionsMenu.add(f);
            item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    pushValueOnStack();
                    mHistory += "funcall " + f + "\n";
                    mActivity.runScript(f);
                    return true;
                }
            });
        }

        return true;
    }

    /**
     * Creates the application's menu, sets the listeners and eventually handle
     * the associated user actions.
     *
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // history menu
        Menu submenu = menu.addSubMenu(getString(R.string.history));

        /**
         * Clears the complete history
         */
        MenuItem item = submenu.add(getString(R.string.clear_history));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mHistory = "";
                return true;
            }
        });

        /**
         * Runs the complete history
         */
        item = submenu.add(getString(R.string.run_history));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (mHistory.isEmpty())
                    return true;

                runScript(mHistory);
                return true;
            }
        });

        /**
         * Edit the history
         */
        item = submenu.add(getString(R.string.edit_history));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // saves history in a temporary file
                new HistoryDialog(mActivity, mHistory);
                return true;
            }
        });

        /**
         * Edit & Saves the history
         */
        item = submenu.add(getString(R.string.save_history));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // saves history in a temporary file
                String historyFile = getFilesDir() + "/" + HISTORY_SCRIPT_NAME;
                writeFile(historyFile, mHistory);
                new EditScriptDialog(mActivity, historyFile);
                return true;
            }
        });

        // stack menu
        submenu = menu.addSubMenu(getString(R.string.stack));

        /**
         * ROLLs the top value N position down the stack
         */
        item = submenu.add(getString(R.string.rolln_stack));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                pushValueOnStack();
                mHistory += "rolln\n";
                rollN(false);
                return true;
            }
        });

        /**
         * SWAPs the top value with N's position value on the stack
         */
        item = submenu.add(getString(R.string.swapn_stack));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                pushValueOnStack();
                mHistory += "swapn\n";
                swapN(false);
                return true;
            }
        });

        /**
         * DUPLICATEs the top value N times on the stack
         */
        item = submenu.add(getString(R.string.dupn_stack) );
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                pushValueOnStack();
                mHistory += "dupn\n";
                dupN(false);
                return true;
            }
        });

        /**
         * DROPs the first N top values from the stack
         */
        item = submenu.add(getString(R.string.dropn_stack));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                pushValueOnStack();
                mHistory += "dropn\n";
                dropN(false);
                return true;
            }
        });

        // functions menu
        submenu = menu.addSubMenu(getString(R.string.functions));

        /**
         *  Brings the maths functions dialog, let the pick a value and
         *  eventually apply the selected function to the stack.
         */
        item = submenu.add(getString(R.string.maths_functions));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // pops up a dialog to pick a java math func
                pushValueOnStack();
                new MathFunctionChooser(mActivity);
                return true;
            }
        });

        // dynamic script functions menu
        mScriptFunctionsMenu = submenu.addSubMenu(getString(R.string.script_functions));

        // scripts menu
        submenu = menu.addSubMenu(R.string.scripts);

        /**
         * Pick an init script
         */
        item = submenu.add(getString(R.string.init_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new FileChooser(INIT_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSIONS, dir == null ? "/" : dir.getPath());
                return true;
            }
        });

        /**
         * Remove init script
         */
        item = submenu.add(getString(R.string.cancel_init_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mInitScriptName = null;
                return true;
            }
        });

        /**
         * Pick and debug a script
         */
        item = submenu.add(getString(R.string.debug_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new FileChooser(DEBUG_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSIONS, dir == null ? "/" : dir.getPath());
                return true;
            }
        });

        /**
         * Pick and execute a script
         */
        item = submenu.add(getString(R.string.run_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                pushValueOnStack();
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new FileChooser(RUN_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSIONS, dir == null ? "/" : dir.getPath());
                return true;
            }
        });

        /**
         * Evaluate an infixed expression
         */
        item = submenu.add(getString(R.string.evaluate_infixed));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // prompt the user to enter a value
                String infixed = GenericDialog.promptMessage(mActivity,
                                InputType.TYPE_CLASS_TEXT,
                                getString(R.string.input_infixed),
                                null);

                InfixConvertor ctor = new InfixConvertor(infixed);

                // display the postfix expression
                doDisplayMessage(getString(R.string.evaluating_label) + "\n" + ctor.getPostfix());

                // and run it
                mHistory += "infixed\"" + infixed + "\n";
                runScript(ctor.getRpnScript());
                return true;
            }
        });

        /**
         * Stops the running script (if any)
         */
        item = submenu.add(getString(R.string.stop_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                ScriptEngine.stop();
                return true;
            }
        });

        /**
         * Pick and edit a script
         */
        item = submenu.add(getString(R.string.edit_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new FileChooser(EDIT_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSIONS, dir == null ? "/" : dir.getPath());
                return true;
            }
        });

        /**
         * Create a new Script
         */
        item = submenu.add(getString(R.string.new_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new EditScriptDialog(mActivity, dir.getAbsolutePath() + "/" + getString(R.string.new_script_name));
                return true;
            }
        });

        /**
         * Show sample scripts
         */
        item = submenu.add(getString(R.string.show_samples));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String samplesUrl = "https://github.com/gilles-fabre/O3/tree/master/Scripts";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(samplesUrl));
                mActivity.startActivity(intent);
                return true;
            }
        });

        /**
         * graph view menu
         */
        submenu = menu.addSubMenu(R.string.graph);

        /**
         * Displays the graph view
         */
        item = submenu.add(getString(R.string.show_graph));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                GenericDialog graphDialog = new GenericDialog(R.layout.graph_view, getString(R.string.graph_view), true);
                graphDialog.show(mActivity.getFragmentManager(), getString(R.string.graph_view));
                return true;
            }
        });

        /**
         * Saves the graph view as png
         */
        item = submenu.add(getString(R.string.save_graph));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String filename = GenericDialog.promptMessage(mActivity,
                                                              InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                                                               getString(R.string.enter_png_filename), null);

                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                try {
                    filename = dir.getAbsolutePath() + "/" + filename;
                    mGraphView.saveToPngFile(filename);
                    doDisplayMessage(getString(R.string.saved_png) + filename);
                } catch (IOException e) {
                    doDisplayMessage(getString(R.string.error_saving_png) + e.getLocalizedMessage());
                }
                return true;
            }
        });

        /**
         * The usual about/help menu
         */
        item = menu.add(getString(R.string.help));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showHelp();
                return true;
            }
        });

        return true;
    }

    /**
     * Manually pushes the execution context when killed.
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;

        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString(EDITED_VALUE_KEY, mValue);
        double[] doubleArray = new double[mStack.size()];
        for (int i = 0; i < mStack.size(); i++)
            doubleArray[i] = mStack.get(i);
        savedInstanceState.putDoubleArray(STACK_CONTENT_KEY, doubleArray);

        // history
        savedInstanceState.putString(HISTORY_SCRIPT_KEY, mHistory);
    }

    /**
     * Manually pops the execution context when getting back to life.
     */
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;

        super.onRestoreInstanceState(savedInstanceState);

        mValue = savedInstanceState.getString(EDITED_VALUE_KEY);
        double[] doubleArray = savedInstanceState.getDoubleArray(STACK_CONTENT_KEY);
        mStack.clear();

        if (doubleArray != null) {
            for (double d : doubleArray)
                mStack.push(Double.valueOf(d));
        }

        mValueField.setText(mValue);
        updateStackView();

        // history
        mHistory = savedInstanceState.getString(HISTORY_SCRIPT_KEY);
    }

    /**
     * Read a complete file into a String and return it.
     * @param fileName is the name of the file to be loaded
     *
     * @return the content of the given file as a String.
     * @throws IOException
     */
    public String readFile(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        }
    }

    /**
     * Sets the calculator history.
     *
     * @param history is the new calculator history
     */
    public boolean setHistory(String history) {
        mHistory = history;
        return true;
    }

    /**
     * Write a string as the full content of the given file.
     *
     * @param fileName is the file to be (over)written
     * @param content is the new file content
     * @return true if the operation was successful, false else.
     */
    public boolean writeFile(String fileName, String content) {
        try {
            FileWriter fw = new FileWriter(fileName);
            fw.write(content);
            fw.close();
        } catch (Exception e) {
            doDisplayMessage(e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * dialog callbacks
     */
    @Override
    public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
        switch (Id) {
            case RUN_SCRIPT_DIALOG_ID : {
                // run the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                pushValueOnStack();
                mHistory += "run_script " + filename + "\n";
                doRunScriptFile(filename);
            }
            break;

            case DEBUG_SCRIPT_DIALOG_ID : {
                // run the selected script
                pushValueOnStack();
                // no history here, we're debugging
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                String script;
                try {
                    script = readFile(filename);
                    final ScriptEngine engine = new ScriptEngine(mActivity, script);
                    new Thread() {
                        @Override
                        public void run() {
                            // Moves the current Thread into the background
                            android.os.Process.setThreadPriority(SCRIPT_ENGINE_PRIORITY);
                            try {
                                engine.debugScript();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            break;

            case EDIT_SCRIPT_DIALOG_ID :  {
                // edit the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                new EditScriptDialog(this, filename);
            }
            break;

            case INIT_SCRIPT_DIALOG_ID : {
                // saves the init script name in the preferences
                // it'll be run every time the program starts
                mInitScriptName = dialog.getBundle().getString(FileChooser.FILENAME);

                // run the init script
                try {
                    runScript(readFile(mInitScriptName));
                } catch (Exception e) {
                    doDisplayMessage(getString(R.string.init_script_error) + e.getLocalizedMessage());
                    mInitScriptName = null;
                }
            }
            break;
        }
    }

    @Override
    public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
        switch (Id) {
            case HELP_DIALOG_ID : {
                TextView textView = (TextView)dialog.getField(R.id.color_log_view);
                if (textView != null)
                    setHelpText(textView);
            }
            break;

            case GRAPH_DIALOG_ID : {
                FrameLayout layout = (FrameLayout) dialog.getField(R.id.graphView);
                if (layout != null) {
                    if (mGraphView.getParent() != null)
                        ((ViewGroup) mGraphView.getParent()).removeView(mGraphView);
                    layout.addView(mGraphView);
                }
            }
            break;
        }
    }

    @Override
    public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
        return true;
    }

    @Override
    public void onDismiss(int Id, GenericDialog dialog, View mView) {
    }

    @Override
    protected void onStop() {
        // save the init script into the preferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(INIT_SCRIPT_NAME, mInitScriptName);

        // save the function buttons
        for (int i = 0; i < NUM_FUNC_BUTTONS; i++) {
            editor.putString(FUNCTION_TITLES_KEY + i, mFunctionTitles[i] == null ? "" : mFunctionTitles[i]);
            editor.putString(FUNCTION_SCRIPTS_KEY + i, mFunctionScripts[i] == null ? "" : mFunctionScripts[i]);
        }

        editor.commit();

        super.onStop();
    }


    @Override
    // called before onCreate, no GUI ready
    protected void onStart() {
        super.onStart();
        // permission in manifest ain't enough now...
        // Here, thisActivity is the current activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getApplicationContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                } else {
                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            0);

                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                    // app-defined int constant. The callback method gets the
                    // result of the request.
                }
            } else {
                // Permission has already been granted
            }
        }
    }

    /**
     * Sets the given function call button's listeners and associated script and name.
     *
     * @param button is the button to be set.
     * @param index is the button index.
     */
    private void setFunctionButton(Button button, int index) {
        final int _index = index;

        if (mFunctionTitles[_index] != null &&
            !mFunctionTitles[_index].isEmpty())
            button.setText(mFunctionTitles[_index]);
            button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mFunctionScripts[_index] != null && !mFunctionScripts[_index].isEmpty()) {
                    pushValueOnStack();
                    mHistory += mFunctionScripts[_index] + "\n";
                    runScript(mFunctionScripts[_index]);
                }
            }
        });
        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // prompt the user for a call script line
                mFunctionScripts[_index] = GenericDialog.promptMessage(mActivity,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        getString(R.string.enter_call_script), mFunctionScripts[_index]);
                // prompt the user for a text
                mFunctionTitles[_index] = GenericDialog.promptMessage(mActivity,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        getString(R.string.enter_button_title), mFunctionTitles[_index]);

                // reset by user?
                if (mFunctionTitles[_index] == null ||
                    mFunctionTitles[_index].isEmpty() ||
                    mFunctionScripts[_index] == null ||
                    mFunctionScripts[_index].isEmpty()) {
                    mFunctionScripts[_index] = null;
                    mFunctionTitles[_index] = getString(R.string.undefined_button);
                }

                ((Button) v).setText(mFunctionTitles[_index]);
                return true;
            }
        });
    }

    @Override
    // called after onStart, prepare GUI, but then onResume will be called before the UI is ready.
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // #### lock screen in portrait mode
        // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // application runs only in portrait mode
        setContentView(R.layout.calculator_view);

        mActivity = this;
        mGraphView = new GraphView(getApplicationContext());

        // set up handlers
        // edit field
        mValueField = findViewById(R.id.input_value);

        // backspace button
        ImageButton backButton = findViewById(R.id.backspaceButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mValue.isEmpty())
                    return;
                mValue = mValue.substring(0, mValue.length() - 1);
                mValueField.setText(mValue);
            }
        });
        backButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mValue.isEmpty())
                    return false;
                mValue = "";
                mValueField.setText(mValue);
                return true;
            }
        });

        // stack
        mStackView = findViewById(R.id.stack);

        mStackAdapter = new ArrayAdapter<>(this, R.layout.simple_row, new ArrayList<String>());
        mStackAdapter.setNotifyOnChange(false); // to improve performances
        mStackView.setAdapter(mStackAdapter);
        mStackView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
             @Override
             public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mValue = mStack.get(mStack.size() - position - 1).toString();
                mValueField.setText(mValue);
             }
         });

        // restore potential stack and edited value
        onRestoreInstanceState(savedInstanceState);

        // calcpad handlers
        Button button = findViewById(R.id.button_0);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "0";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_1);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "1";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_2);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "2";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_3);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "3";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "4";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_5);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "5";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_6);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "6";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_7);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "7";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_8);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "8";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_9);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mValue += "9";
                mValueField.setText(mValue);
            }
        });
        button = findViewById(R.id.button_add);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "+\n";
                add(false);
            }
        });
        button = findViewById(R.id.button_sub);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "-\n";
                sub(false);
            }
        });
        button = findViewById(R.id.button_mul);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "*\n";
                mul(false);
            }
        });
        button = findViewById(R.id.button_div);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "/\n";
                div(false);
            }
        });
        button = findViewById(R.id.button_enter);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
            }
        });
        button = findViewById(R.id.button_dot);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!mValue.isEmpty() && !mValue.contains(".")) {
                    mValue += ".";
                    mValueField.setText(mValue);
                }
            }
        });
        button = findViewById(R.id.button_neg);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHistory += "neg\n";
                neg(false);
            }
        });
        button = findViewById(R.id.button_dup);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "dup\n";
                dup(false);
            }
        });
        button = findViewById(R.id.button_drop);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "drop\n";
                drop(false);
            }
        });
        button = findViewById(R.id.button_swap);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "swap\n";
                swap(false);
            }
        });
        button = findViewById(R.id.button_clear);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pushValueOnStack();
                mHistory += "clear\n";
                clear(false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // get the preferences

        // init script
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        if (prefs.contains(INIT_SCRIPT_NAME)) {
            mInitScriptName = prefs.getString(INIT_SCRIPT_NAME, null);

            // run the init script
            try {
                runScript(readFile(mInitScriptName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // function buttons
        for (int i = 0; i < NUM_FUNC_BUTTONS; i++) {
            mFunctionTitles[i] = prefs.getString(FUNCTION_TITLES_KEY + i, null);
            mFunctionScripts[i] = prefs.getString(FUNCTION_SCRIPTS_KEY + i, null);
        }

        // function buttons handlers
        Button button = findViewById(R.id.button_fn1);
        setFunctionButton(button, 0);
        button = findViewById(R.id.button_fn2);
        setFunctionButton(button, 1);
        button = findViewById(R.id.button_fn3);
        setFunctionButton(button, 2);
        button = findViewById(R.id.button_fn4);
        setFunctionButton(button, 3);
        button = findViewById(R.id.button_fn5);
        setFunctionButton(button, 4);
        button = findViewById(R.id.button_fn6);
        setFunctionButton(button, 5);
        button = findViewById(R.id.button_fn7);
        setFunctionButton(button, 6);
        button = findViewById(R.id.button_fn8);
        setFunctionButton(button, 7);
        button = findViewById(R.id.button_fn9);
        setFunctionButton(button, 8);
        button = findViewById(R.id.button_fn10);
        setFunctionButton(button, 9);
        button = findViewById(R.id.button_fn11);
        setFunctionButton(button, 10);
        button = findViewById(R.id.button_fn12);
        setFunctionButton(button, 11);
        button = findViewById(R.id.button_fn13);
        setFunctionButton(button, 12);
        button = findViewById(R.id.button_fn14);
        setFunctionButton(button, 13);
        button = findViewById(R.id.button_fn15);
        setFunctionButton(button, 14);
    }

    /**
     * Returns an instance of the given type.
     *
     * @param classname
     * @return on object corresponding to the passed  className
     */
    private Object castDoubleToType(String classname, Double value) {
        switch(classname) {
            case "Long":
            case "long":
                return Long.valueOf(value.longValue());

            case "Integer":
            case "int":
                return Integer.valueOf(value.intValue());

            case "Float":
            case "float":
                return Float.valueOf(value.floatValue());

            case "Double":
            case "double":
                return value;
        }

        return null;
    }

    /**
     * Returns an instance of Double from the given type.
     *
     * @param classname
     * @return on object corresponding to the passed  className
     */
    private Double castTypeToDouble(String classname, Object value) {
        switch(classname) {
            case "Long":
                return Double.valueOf((Long)value);

            case "long":
                return Double.valueOf((long)value);

            case "Integer":
                return Double.valueOf((Integer)value);

            case "int":
                return Double.valueOf((int)value);

            case "Float":
                return Double.valueOf((Float)value);

            case "float":
                return Double.valueOf((float)value);

            case "Double":
                return (Double)value;

            case "double":
                return Double.valueOf((double)value);
        }

        return null;
    }

    /**
     * Returns an array containing all of the static java math methods.
     *
     * @return the java math methods array.
     */
    public Method[] getJavaMathMethods() {
        if (mMethods == null) {
            Class math = null;
            try {
                math = Class.forName("java.lang.Math");
                mMethods = math.getMethods();
            } catch (ClassNotFoundException e) {
                doDisplayMessage(getString(R.string.java_math_inspection_error) + e.getLocalizedMessage());
            }
        }

        return mMethods;
    }

    /**
     * Calls into the selected Java.math passed method using introspection.
     *
     * @param method is the math method to be called per user request.
     */
    public boolean invokeAndHistorizeMathFunction(Method method) {
        pushValueOnStack();
        mHistory += "math_call " + method.getName() + "\n";
        return invokeMathMethod(method, false);
    }

    private boolean invokeMathMethod(Method method, boolean fromEngine) {
        // invoke the selected function

        // first make sure we have the appropriate number of elements
        // on the stack
        Type[] params = method.getParameterTypes();
        int numParams = params.length;
        if (numParams > mStack.size())
            return false;

        // prepare the parameters.
        Object []argObjects = new Object[numParams];
        while (--numParams >= 0)
            argObjects[numParams] = castDoubleToType(params[numParams].toString(), mStack.pop());

        // invoke the method
        boolean runOk = true;
        try {
            Object result = method.invoke(null, argObjects);
            if (result != null)
                mStack.push(castTypeToDouble(method.getGenericReturnType().toString(), result));
        } catch (Exception e) {
            GenericDialog.displayMessage(this, getString(R.string.function_call_err) + e.getMessage());
            runOk = false;
        } finally {
            // we've eaten the stack anyway...
            if (!fromEngine)
                updateStackView();
        }

        return runOk;
    }

    /**
     * Displays the help dialog
     */
    private void showHelp() {
        GenericDialog dialog = new GenericDialog(HELP_DIALOG_ID, getString(R.string.about_o3), true);
        dialog.show(getFragmentManager(), getString(R.string.about_o3));
    }

    /**
     * Place the formatted help in the help color log view
     */
    private void setHelpText(TextView view) {
        if (view == null)
            return;

        ColorLogView helpView = new ColorLogView(view);

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\nO3 Calculator", 0x008888, true);
        helpView.resetFontSize();

        PackageInfo pinfo = null;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            helpView.setFontSize(ColorLogView.BIG_FONT);
            helpView.appendText("\nVersion : " + pinfo.versionName, 0x008888, true);
            helpView.resetFontSize();
        } catch (PackageManager.NameNotFoundException e) {
            // nop
        }

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\n<a href=\"url\">https://github.com/gilles-fabre/O3</a>\n\n", 0x00FFFF, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("Basic functionalities :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tBasic RPN calculation. Use the numeric pad, and the following functions :\n", 0, false);
        helpView.appendText("\t\t'0..9/.' appends the selected symbol to the currently edited value\n", 0, false);
        helpView.appendText("\t\t'ENTER' pushes the currently edited value onto the stack\n", 0, false);
        helpView.appendText("\t\t'/' divides the 2nd value on the stack by the 1st one, pushes the result back on the stack\n", 0, false);
        helpView.appendText("\t\t'*' multiplies the 2nd value on the stack by the 1st one, pushes the result back on the stack\n", 0, false);
        helpView.appendText("\t\t'+' adds the 2nd value on the stack to the 1st one, pushes the result back on the stack\n", 0, false);
        helpView.appendText("\t\t'-' subtracts the 1st value on the stack from the 2nd one, pushes the result back on the stack\n", 0, false);
        helpView.appendText("\t\t'N' negates the value currently edited if existing else the 1st value on stack\n", 0, false);
        helpView.appendText("\t\t'SWAP' swaps the two topmost values on the stack\n", 0, false);
        helpView.appendText("\t\t'DUP' pushes on the stack a copy of its topmost value\n", 0, false);
        helpView.appendText("\t\t'DROP' drops the topmost value off the stack\n", 0, false);
        helpView.appendText("\t\t'CLEAR' clears the whole stack\n", 0, false);
        helpView.appendText("\t\t'...' buttons can be programmed (long press) to call a script, a java math or script function, via a single line script.\n", 0, false);
        helpView.appendText("\nNOTE: all of the these actions, except 'N', first push the edited value (if present) on the stack. Selecting a value in the stack copies it to the edit value field.\n", 0, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nHistory menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\t'Clear' clears all the history registered so far (since last clear/startup).\n", 0, false);
        helpView.appendText("\t\t'Run' runs the history registered so far.\n", 0, false);
        helpView.appendText("\t\t'Edit..' Pops up a dialog where one can edit the history.\n", 0, false);
        helpView.appendText("\t\t'Save..' Pops up a dialog where one can edit and save the history into a file.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nStack menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\t'Roll N' rolls the 2nd value down the stack by the number of positions given by the 1st value on the stack.\n", 0, false);
        helpView.appendText("\t\t'Swap N' swaps the 2nd value on the stack with the one at the position given by the 1st value on the stack.\n", 0, false);
        helpView.appendText("\t\t'Dup N' duplicates the 2nd value on the stack the number of times given by the 1st value on the stack.\n", 0, false);
        helpView.appendText("\t\t'Drop N' drops as many values off the stack as the number given by the 1st value on the stack.\n", 0, false);
        helpView.appendText("\nNOTE: all of the these actions first push the edited value (if present) on the stack.\n", 0, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nFunctions/Java Math Functions.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\t'Pops up a dialog listing all the java.math functions. The selected function, if any, will be applied to the stack. The edit field can be used to search through the functions. A long press on a function brings help for the latter.\n", 0, false);
        helpView.appendText("\nNOTE: all of the these actions, except 'N' first push the edited value (if present) on the stack. \n", 0, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nFunctions/Script Functions menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\t'Menu listing all the functions defined (see defun) in scripts which have been run. The selected function, if any, will be applied to the stack.\n", 0, false);
        helpView.appendText("\nNOTE: all of the these actions, except 'N' first push the edited value (if present) on the stack. \n", 0, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Select Init Script.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be run upon o3 start.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Cancel Init Script.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tThe previously selected init script won't be run anymore upon o3 start.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Debug.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be debugged (see .o3s provided examples).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Run.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be run (see .o3s provided examples).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Evaluate Infixed.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can enter an infixed expression to be evaluated (with proper operator precedence).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Stop :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tStops the currently running O3 script (if any).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Edit.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be edited (see .o3s provided examples).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/New.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can edit a new O3 script, then save it under a new name.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nGraph View/Show.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog containing the graphical (using orthonormal coordinates) result of the run o3s scripts (if containing graphical calls).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nGraph View/Save.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPrompts the user for a .png filename to save the graph to (no path, no extension expected).\n", 0, false);
        helpView.resetFontSize();

        helpView.appendText("\nNOTE: running any script first pushes the edited value (if present) on the stack.\n", 0, true);

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts syntax :\n", 0x008888, true);
        helpView.appendText("\t\trolln, swap, swapn, dup, dupn, clear : have the same effect as their UI peers.\n", 0, false);
        helpView.appendText("\t\tstack_size : pushes the stack size onto the stack.\n", 0, false);
        helpView.appendText("\t\tneg : inverts the edited (if any) and top stack value(s).\n", 0, false);
        helpView.appendText("\t\t_value : pushes the _value onto the stack.\n", 0, false);
        helpView.appendText("\t\t+, -, *, / : have the same effect as their UI equivalents.\n", 0, false);
        helpView.appendText("\t\t% : Pushes the modulus (remaining part of division) on the stack.\n", 0, false);
        helpView.appendText("\t\t&lt&gt, &lt, &lt=, &gt, &gt= : pop the two topmost values and pushes the result (0 means false, not 0.0 means true) of their comparison on the stack.\n", 0, false);
        helpView.appendText("\t\tif, [else], end_if : conditional block[s], if and else do not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\twhile, end_while : iteration block[s], while does not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\tfundef _f, end_fundef : defines a function _f which can later be invoked (until deleted) from any script during the session.\n", 0, false);
        helpView.appendText("\t\tfundel _f : deletes (forgets) the _f function.\n", 0, false);
        helpView.appendText("\t\tfuncall _f : calls the script function _f.\n", 0, false);
        helpView.appendText("\t\t!\"_message : displays _message in a blocking modal dialog.\n", 0, false);
        helpView.appendText("\t\t?\"_prompt : displays _prompt message in a value prompting & blocking modal dialog. Pushes the value entered by the user on the stack.\n", 0, false);
        helpView.appendText("\t\tinfixed\"_expression : converts and evaluates the provided infixed _expression. The debugger can step into the evaluation. variables (!_v) can be used in infixed expressions. prefix function calls with fc@ and math calls with mc@ (eg : infixed\"fc@logN(2,mc@pow(2, 8)))\n", 0, false);
        helpView.appendText("\t\t?_variable : pops the topmost value off the stack into the given _variable.\n", 0, false);
        helpView.appendText("\t\t!_variable : pushes the given _variable's value onto the stack.\n", 0, false);
        helpView.appendText("\t\t?[]_array : pops the 2nd value off the stack into the given _array at the index given by the 1st value.\n", 0, false);
        helpView.appendText("\t\t![]_array : pushes on the stack the value from the _array at the index given by the stack's topmost value.\n", 0, false);
        helpView.appendText("\t\terase : clears the graphical view background with the r,g,b indexes given by the first three values of the stack.\n", 0, false);
        helpView.appendText("\t\tcolor : sets the graphical view drawing color with the r,g,b indexes given by the first three values of the stack.\n", 0, false);
        helpView.appendText("\t\tdot_size : sets the point drawing size to that given by the topmost value of the stack.\n", 0, false);
        helpView.appendText("\t\trange : sets the graphical view virtual orthonormal extent to the xMin, xMax, yMin, yMax given by the first fours values of the stack.\n", 0, false);
        helpView.appendText("\t\tpov3D : sets the 3D p.oint o.f v.iew at the x, y, z coordinates given by the three topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tplot : draws a dot with the current color, dot size, at the x, y coordinates given by the two topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tplot3D : draws a dot with the current color, dot size, at the x, y, z coordinates given by the three topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tline : draws a line with the current color, dot size, at the x0, y0, x1, y1 coordinates given by the four topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tline3D : draws a line with the current color, dot size, at the x0, y0, z0, x1, y1, z1 coordinates given by the six topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tmath_call _f : calls the java maths function _f.\n", 0, false);
        helpView.appendText("\t\trun_script _s : runs the script _s.\n", 0, false);
        helpView.appendText("\t\tdebug_break : pops up a modal dialog reading various debugging information. The standard 'step over', 'step in', 'step out' are supported. 'resume' resumes the script execution and ends the debugging session. 'exit' terminates the script..\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\n\nHave fun using O3. Gilles Fabre(c) 2019.\n\n", 0x00FFFF, true);
        helpView.resetFontSize();
    }

    /**
     * Calls the given java math function if existing
     *
     * @param function is the function to be called.
     *
     * @return true if the function was found, false else.
     */
    public boolean doJavaMathCall(String function) {
        Method[] methods = getJavaMathMethods();
        if (methods == null)
            return false;

        // lookup function
        // for (Method method : methods) too slow
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (function.equals(m.getName()))
                return invokeMathMethod(m, true);
        }

        // the function was not found
        doDisplayMessage(getString(R.string.undefined_function) + function);

        return false;
    }

    /**
     * Public Basic Maths Functions
     *
     */
    public boolean doAdd() {
        return add(true);
    }
    private boolean add(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 +
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 + v2);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doSub() {
        return sub(true);
    }
    private boolean sub(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 - v2);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doDiv() {
        return div(true);
    }
    private boolean div(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 /
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 / v2);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doMul() {
        return mul(true);
    }
    private boolean mul(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 *
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 * v2);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doNeg() {
        return neg(true);
    }
    private boolean neg(boolean fromEngine) {
        // either neg the edited value if any
        if (!mValue.isEmpty()) {
            if (mValue.startsWith("-"))
                mValue = mValue.substring(1);
            else
                mValue = "-" + mValue;
            mValueField.setText(mValue);

            return true;
        }

        if (!mStack.isEmpty()) {
            // or the top of the stack if present
            mStack.push(-mStack.pop());
            if (!fromEngine)
                updateStackView();

            return true;
        }

        return false;
    }

    public boolean doModulo() {
        return modulo(true);
    }
    private boolean modulo(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 %
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 % v2);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doEqual() {
        return equal(true);
    }
    private boolean equal(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 =
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1.doubleValue() == v2.doubleValue() ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doNotEqual() {
        return notEqual(true);
    }
    private boolean notEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 !=
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1.doubleValue() != v2.doubleValue() ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doLessThan() {
        return lessThan(true);
    }
    private boolean lessThan(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 <
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 < v2 ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doLessThanOrEqual() {
        return lessThanOrEqual(true);
    }
    private boolean lessThanOrEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 <=
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 <= v2 ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doGreaterThan() {
        return greaterThan(true);
    }
    private boolean greaterThan(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 >
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 > v2 ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doGreaterThanOrEqual() {
        return greaterThanOrEqual(true);
    }
    private boolean greaterThanOrEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 >=
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 >= v2 ? 1.0 : 0.0);
        if (!fromEngine)
            updateStackView();

        return true;
    }

    /**
     * Public Basic Stack Functions
     *
     */
    public boolean doRollN() {
        return rollN(true);
    }
    private boolean rollN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i >= mStack.size())
            return false;

        Double val = mStack.pop();
        Stack<Double> st = new Stack<>();
        while (--i >= 0)
            st.push(mStack.pop());

        mStack.push(val);
        while (!st.isEmpty())
            mStack.push(st.pop());

        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doDup() {
        return dup(true);
    }
    private  boolean dup(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        mStack.push(mStack.peek());
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doDupN() {
        return dupN(true);
    }
    private boolean dupN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (mStack.isEmpty())
            return false;

        Double val = mStack.peek();
        while (--i >= 0)
            mStack.push(val);

        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doDrop() {
        return drop(true);
    }
    private boolean drop(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        mStack.pop();
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doDropN() {
        return dropN(true);
    }
    private boolean dropN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i > mStack.size())
            return false;

        while (--i >= 0)
            mStack.pop();

        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doSwap() {
        return swap(true);
    }
    private boolean swap(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        Double v1 = mStack.pop();
        Double v2 = mStack.pop();
        mStack.push(v1);
        mStack.push(v2);

        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doSwapN() {
        return swapN(true);
    }
    private boolean swapN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i > mStack.size())
            return false;

        Double v1 = mStack.elementAt(0);
        Double v2 = mStack.elementAt(i.intValue() - 1);
        mStack.setElementAt(v1, i.intValue() - 1);
        mStack.setElementAt(v2, 0);

        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doClear() {
        return clear(true);
    }
    private boolean clear(boolean fromEngine) {
        mStack = new Stack<>();
        if (!fromEngine)
            updateStackView();

        return true;
    }

    public boolean doStackSize() {
        return stackSize(true);
    }
    private boolean stackSize(boolean fromEngine) {
        mStack.push(Double.valueOf(mStack.size()));
        if (!fromEngine)
            updateStackView();

        return true;
    }

    /**
     * Displays a message in an application modal (blocking) dialog
     *
     * @param message is the message to be displayed.
     */
    public void doDisplayMessage(String message) {
        mHandler.obtainMessage(DISPLAY_MESSAGE, message).sendToTarget();
        try {
            Thread.sleep(UI_YIELD_MILLISEC_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Displays a progress short text in a calculator input field
     *
     * @param text is the string to be displayed.
     */
    public void doDisplayProgressMessage(String text) {
        mHandler.obtainMessage(DISPLAY_PROGRESS_MESSAGE, text).sendToTarget();
        try {
            Thread.sleep(UI_YIELD_MILLISEC_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * This class carries the necessary data to prompt the user with a message
     * to input data. This is instanciated in the background script engine thread
     * and passed over to the main UI thread through a message.
     */
    class PromptForValueMessage {
        double      mValue;
        String      mMessage;
        Semaphore   mWaitForValue;

        public PromptForValueMessage(String message) {
            mValue = 0;
            mMessage = message;
            mWaitForValue = new Semaphore(0);
        }
    }

    /**
     * Displays a message in an application modal (blocking) dialog and prompts
     * the user for a double value. The value is pushed on the stack.
     *
     * @param message is the message to be displayed.
     */
    public void doPromptForValue(String message) {
        PromptForValueMessage prompt = new PromptForValueMessage(message);
        mHandler.obtainMessage(PROMPT_MESSAGE, prompt).sendToTarget();
        try {
            prompt.mWaitForValue.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mStack.push(prompt.mValue);
        updateStackView();
    }

    /**
     * Updates the Calculator's stack view.
     */
    public void doUpdateStack() {
        mHandler.obtainMessage(UPDATE_STACK_MESSAGE).sendToTarget();
        try {
            Thread.sleep(UI_YIELD_MILLISEC_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a string containing the stack content.
     *
     * @return the stack content.
     */
    public String getStackDebugInfo() {
        StringBuilder stack = new StringBuilder();
        for (int i = mStack.size() - 1; i >= 0; i--)
            stack.append("stack(").append(i).append(") : ").append(mStack.get(i)).append("\n");

        return stack.toString();
    }

    /**
     * Runs the given script.
     *
     * @param script is the script text.
     */
    public void runScript(String script) {
        if (ScriptEngine.isRunning()) {
            doDisplayMessage(mActivity.getString(R.string.script_running));
            return;
        }

        final ScriptEngine engine = new ScriptEngine(mActivity, script);
                new Thread() {
                    @Override
                    public void run() {
                        // Moves the current Thread into the background
                        android.os.Process.setThreadPriority(SCRIPT_ENGINE_PRIORITY);
                        try {
                            engine.runScript();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
    }

    /**
     * Runs the given script file
     *
     * @param filename is the fully qualified script filename.
     * @return the script was found and spawned.
     */
    public boolean doRunScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            runScript(script);
            found = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return found;
    }

    /**
     * Run (synchronously) a script from within another script.
     *
     * @param filename is the name of the script file to run
     * @return true if the file was found, false else.
     */
    public boolean doRunInnerScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            new ScriptEngine(mActivity, script).runScript();
            found = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return found;
    }

    /**
     * Graphical functions
     */
    public boolean doPlot() {
        if (mStack.size() < 2)
            return false;

        Double y = mStack.pop();
        Double x = mStack.pop();

        mGraphView.doPlot(x, y);

        return true;
    }

    public boolean doPlot3D() {
        if (mStack.size() < 3)
            return false;

        Double z = mStack.pop();
        Double y = mStack.pop();
        Double x = mStack.pop();

        mGraphView.doPlot3D(x, y, z);

        return true;
    }

    public boolean doLine() {
        if (mStack.size() < 4)
            return false;

        Double y1 = mStack.pop();
        Double x1 = mStack.pop();
        Double y0 = mStack.pop();
        Double x0 = mStack.pop();

        mGraphView.doLine(x0, y0, x1, y1);

        return true;
    }

    public boolean doLine3D() {
        if (mStack.size() < 6)
            return false;

        Double z1 = mStack.pop();
        Double y1 = mStack.pop();
        Double x1 = mStack.pop();
        Double z0 = mStack.pop();
        Double y0 = mStack.pop();
        Double x0 = mStack.pop();

        mGraphView.doLine3D(x0, y0, z0, x1, y1, z1);

        return true;
    }

    public boolean doErase() {
        if (mStack.size() < 3)
            return false;

        Double b = mStack.pop();
        Double g = mStack.pop();
        Double r = mStack.pop();

        mGraphView.doErase(r, g, b);

        return true;
    }

    public boolean doSetRange() {
        if (mStack.size() < 4)
            return false;

        Double yMax = mStack.pop();
        Double yMin = mStack.pop();
        Double xMax = mStack.pop();
        Double xMin = mStack.pop();

        mGraphView.setRange(xMin, xMax, yMin, yMax);

        return true;
    }

     public boolean doSetPov3D() {
        if (mStack.size() < 3)
            return false;

        Double z = mStack.pop();
        Double y = mStack.pop();
        Double x = mStack.pop();

        mGraphView.doPov3D(x, y, z);

        return true;
    }

    public boolean doSetColor() {
        if (mStack.size() < 3)
            return false;

        Double b = mStack.pop();
        Double g = mStack.pop();
        Double r = mStack.pop();

        mGraphView.setColor(r, g, b);

        return true;
    }

    public boolean doSetDotSize() {
        if (mStack.size() < 1)
            return false;

        Double s = mStack.pop();

        mGraphView.setDotSize(s);

        return true;
    }

    /* ---------------------------  DEBUG HANDLING -----------------------------------------------*/

    private static DebugView mDebugView = null;

    public DebugView.DebugState getDebugState() {
        return getDebugView().getDebugState();
    }

    public void doUpdateDebugInfo(int yyline, String script, String toString, String stackDebugInfo) {
        getDebugView().updateDebugInfo(yyline, script, toString, stackDebugInfo);
    }

    public void doShowDebugView() {
        Semaphore userAction = new Semaphore(0);
        mHandler.obtainMessage(SHOW_DEBUG_VIEW, userAction).sendToTarget();
        try {
            userAction.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void doHideDebugView() {
        mHandler.obtainMessage(HIDE_DEBUG_VIEW).sendToTarget();
        while (isDebugViewShown())
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    public boolean isDebugViewShown() {
        return getDebugView().isShown();
    }

    private static DebugView getDebugView() {
        if (mDebugView == null)
            mDebugView = new DebugView(mActivity);

        return mDebugView;
    }

    /* -------------------------------------------------------------------------------------------*/
}
