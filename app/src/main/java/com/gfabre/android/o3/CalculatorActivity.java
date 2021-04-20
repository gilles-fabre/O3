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
import com.gfabre.android.utilities.widgets.MessageDisplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
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

    private static final int RUN_SCRIPT_DIALOG_ID = 1;
    private static final int DEBUG_SCRIPT_DIALOG_ID = 2;
    private static final int EDIT_SCRIPT_DIALOG_ID = 3;
    private static final int INIT_SCRIPT_DIALOG_ID = 6;
    private static final int HELP_DIALOG_ID = R.layout.log_view;
    private static final int GRAPH_DIALOG_ID = R.layout.graph_view;
    private static final String SCRIPT_EXTENSION = ".o3s";
    private static final String DEFAULT_INIT_SCRIPT_NAME = "InitScript";
    private static final String ARRAY_TEST_SCRIPT_NAME = "ArrayTest";
    private static final String BUTTERFLY_SCRIPT_NAME  = "Butterfly";
    private static final String DECREMENT_SPEED_TEST_SCRIPT_NAME  = "DecrementSpeedTest";
    private static final String DRAW_CIRCLES_SCRIPT_NAME = "DrawCircles";
    private static final String DRAW_LOG2_SCRIPT_NAME = "DrawLog2";
    private static final String DRAW_POLYGON_SCRIPT_NAME = "DrawPolygon";
    private static final String DRAW_POW2_SCRIPT_NAME = "DrawPow2";
    private static final String DRAW_SINE_SCRIPT_NAME = "DrawSine";
    private static final String DRAW_SINE_COLOR_SCRIPT_NAME = "DrawSineColor";
    private static final String DRAW_SINE_COLOR3D_SCRIPT_NAME = "DrawSineColor3D";
    private static final String INNER_WHILE_TEST_SCRIPT_NAME = "InnerWhileTest";
    private static final String RAND_PLOT_SCRIPT_NAME = "RandPlot";
    private static final String WHIRL_SCRIPT_NAME = "Whirl";
    private static final String X_POW_Y_SCRIPT_NAME = "x^y";

    private static final String READ_HELP_FIRST_FLAG = "ReadHelpFirst";
    private static final String FUNCTION_SCRIPTS_KEY = "FunctionScripts";
    private static final String FUNCTION_TITLES_KEY = "FunctionTitles";
    private static final String STACK_CONTENT_KEY = "StackContent";
    private static final String EDITED_VALUE_KEY = "EditedValue";
    private static final String HISTORY_SCRIPT_KEY = "HistoryScript";
    private static final String HISTORY_SCRIPT_NAME = "HistoryScript";

    private static final int NUM_FUNC_BUTTONS = 35;

    private static final int DISPLAY_PROGRESS_MESSAGE = 0;
    private static final int UPDATE_STACK_MESSAGE = 1;
    private static final int DISPLAY_MESSAGE = 2;
    private static final int PROMPT_MESSAGE = 3;
    private static final int SHOW_DEBUG_VIEW_MESSAGE = 4;
    private static final int HIDE_DEBUG_VIEW_MESSAGE = 5;

    private static final int SCRIPT_ENGINE_PRIORITY = Process.THREAD_PRIORITY_URGENT_AUDIO;
    private static final int UI_YIELD_MILLISEC_DELAY = 3; // needed time for the UI to get scheduled :/

    private static CalculatorActivity mActivity;         // this reference
    private static Calculator mCalculator;

    private EditText mValueField = null;                 // value edit field
    private ListView mStackView = null;                  // stack view
    private ArrayAdapter mStackAdapter = null;           // stack view adapter
    private GraphView mGraphView = null;                 // canvas for graphical functions
    private Menu mScriptFunctionsMenu = null;            // dynamic script functions menu
    private String mInitScriptName = null;               // init script, if set, run upon calculator start

    private String mFunctionScripts[] = new String[NUM_FUNC_BUTTONS];
    private String mFunctionTitles[] = new String[NUM_FUNC_BUTTONS];

    /**
     * All interactions with the UI must be done from the main UI thread, hence
     * thru a message handler, invoked from the background thread running the scripts.
     */
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case DISPLAY_PROGRESS_MESSAGE:
                    // display script progress
                    mValueField.setText((String) inputMessage.obj);
                    break;

                case UPDATE_STACK_MESSAGE:
                    // update the stack
                    mCalculator.updateStackView();
                    break;

                case DISPLAY_MESSAGE:
                    // presents the user a message to read
                    DisplayMessage message = (DisplayMessage) inputMessage.obj;
                    displayMessage(message.mMessage);

                    message.mWaitForRead.release();
                    break;

                case PROMPT_MESSAGE:
                    // prompt the user to enter a value
                    PromptForValueMessage prompt = (PromptForValueMessage) inputMessage.obj;
                    try {
                        prompt.mValue = new BigDecimal(GenericDialog.promptMessage(mActivity,
                                        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                                        prompt.mMessage,
                                        null), MathContext.UNLIMITED);
                    } catch (NumberFormatException e) {
                        displayMessage(getString(R.string.invalid_number) + e.getMessage());
                    }
                    prompt.mWaitForValue.release();
                    break;

                case SHOW_DEBUG_VIEW_MESSAGE:
                    // show the dialog
                    getDebugView().show();
                    ((Semaphore) (inputMessage.obj)).release();
                    break;

                case HIDE_DEBUG_VIEW_MESSAGE:
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

    /**
     * Fills the stack view with the values currently held on the computation stack
     */
    public void updateStackView(Stack<BigDecimal> stack) {
        if (mStackView == null)
            return;

        mStackAdapter.clear();
        int depth = 1;
        Object[] values = stack.toArray();
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
                    mCalculator.pushValueOnStack();
                    String funcall = "funcall " + f + "\n";
                    mCalculator.appendHistory(funcall);
                    mActivity.executeScript(funcall);
                    return true;
                }
            });
        }

        return true;
    }


    /**
     *
     * @return the default path where data should go.
     */
    private String getDefaultDataPath() {
        File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return dir == null ? "/" : dir.getPath();
    }

    private String getDefaultDataAbsolutePath() {
        File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return dir == null ? "/" : dir.getAbsolutePath();
    }

    /**
     * Creates the application's menu, sets the listeners and eventually handle
     * the associated user actions.
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
                mCalculator.setHistory("");
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
                if (mCalculator.isHistoryEmpty())
                    return true;

                executeScript(mCalculator.getHistory());
                mCalculator.updateStackView();
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
                new HistoryDialog(mActivity, mCalculator, mCalculator.getHistory());
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
                String historyFile = getFilesDir() + "/" + HISTORY_SCRIPT_NAME + SCRIPT_EXTENSION;
                writeFile(historyFile, mCalculator.getHistory());
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
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("rolln\n");
                mCalculator.rollN(false);
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
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("swapn\n");
                mCalculator.swapN(false);
                return true;
            }
        });

        /**
         * DUPLICATEs the top value N times on the stack
         */
        item = submenu.add(getString(R.string.dupn_stack));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("dupn\n");
                mCalculator.dupN(false);
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
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("dropn\n");
                mCalculator.dropN(false);
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
                mCalculator.pushValueOnStack();
                new MathFunctionChooser(mActivity, mCalculator);
                return true;
            }
        });

        // dynamic script functions menu
        mScriptFunctionsMenu = submenu.addSubMenu(getString(R.string.script_functions));

        // scripts menu
        submenu = menu.addSubMenu(R.string.scripts);

        /**
         * Pick and debug a script
         */
        item = submenu.add(getString(R.string.debug_script));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // path to the external download directory if available, internal one else.
                new FileChooser(DEBUG_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSION, getDefaultDataPath());
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
                mCalculator.pushValueOnStack();
                new FileChooser(RUN_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSION, getDefaultDataPath());
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
                displayMessage(getString(R.string.evaluating_label) + "\n" + ctor.getPostfix());

                // and run it
                mCalculator.appendHistory("infixed " + infixed + "\n");
                interpretScript(ctor.getRpnScript());
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
                new FileChooser(EDIT_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSION, getDefaultDataPath());
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
                new EditScriptDialog(mActivity, getDefaultDataAbsolutePath() + "/" + getString(R.string.new_script_name));
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
         * Generate sample scripts
         */
        item = submenu.add(getString(R.string.generate_samples));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                generateSampleScripts();
                return true;
            }
        });

        /**
         * graph view menu
         */
        submenu = menu.addSubMenu(R.string.graph);

        /**
         * Draws a user defined function
         */
        item = submenu.add(getString(R.string.draw_function));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String expression = GenericDialog.promptMessage(mActivity,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        getString(R.string.infixed_expression), null);

                if (!expression.isEmpty())
                    drawFunction(expression);
                return true;
            }
        });

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

                try {
                    filename = getDefaultDataAbsolutePath() + "/" + filename;
                    mGraphView.saveToPngFile(filename);
                    displayMessage(getString(R.string.saved_png) + filename);
                } catch (IOException e) {
                    displayMessage(getString(R.string.error_saving_png) + e.getLocalizedMessage());
                }
                return true;
            }
        });

        /**
         * Loads the graph view from a png
         */
        item = submenu.add(getString(R.string.load_graph));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String filename = GenericDialog.promptMessage(mActivity,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        getString(R.string.enter_png_filename), null);

                filename = getDefaultDataAbsolutePath() + "/" + filename;
                mGraphView.loadFromPngFile(filename);
                displayMessage(getString(R.string.loaded_png) + filename);

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

        savedInstanceState.putString(EDITED_VALUE_KEY, mCalculator.getValue());
        String[] valuesArray = new String[mCalculator.getStackSize()];
        for (int i = 0; i < mCalculator.getStackSize(); i++)
            valuesArray[i] = mCalculator.getStack().get(i).toEngineeringString();
        savedInstanceState.putStringArray(STACK_CONTENT_KEY, valuesArray);

        // history
        savedInstanceState.putString(HISTORY_SCRIPT_KEY, mCalculator.getHistory());
    }

    /**
     * Manually pops the execution context when getting back to life.
     */
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;

        super.onRestoreInstanceState(savedInstanceState);

        mCalculator.setValue(savedInstanceState.getString(EDITED_VALUE_KEY));
        String[] valuesArray = savedInstanceState.getStringArray(STACK_CONTENT_KEY);
        mCalculator.clearStack();

        if (valuesArray != null) {
            for (String s : valuesArray)
                mCalculator.doPushValueOnStack(new BigDecimal(s, MathContext.UNLIMITED));
        }

        mValueField.setText(mCalculator.getValue());
        mCalculator.updateStackView();

        // history
        mCalculator.setHistory(savedInstanceState.getString(HISTORY_SCRIPT_KEY));
    }

    /**
     * Read a complete file into a String and return it.
     *
     * @param fileName is the name of the file to be loaded
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
     * Write a string as the full content of the given file.
     *
     * @param fileName is the file to be (over)written
     * @param content  is the new file content
     * @return true if the operation was successful, false else.
     */
    public boolean writeFile(String fileName, String content) {
        try {
            FileWriter fw = new FileWriter(fileName);
            fw.write(content);
            fw.close();
        } catch (Exception e) {
            displayMessage(e.getMessage());
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
            case RUN_SCRIPT_DIALOG_ID: {
                // run the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("run_script " + filename + "\n");
                doExecuteScriptFile(filename);
            }
            break;

            case DEBUG_SCRIPT_DIALOG_ID: {
                // run the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                mCalculator.pushValueOnStack();
                // no history here, we're debugging
                doDebugScriptFile(filename);
            }
            break;

            case EDIT_SCRIPT_DIALOG_ID: {
                // edit the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                new EditScriptDialog(this, filename);
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
            case HELP_DIALOG_ID: {
                TextView textView = (TextView) dialog.getField(R.id.color_log_view);
                if (textView != null)
                    setHelpText(textView);
            }
            break;

            case GRAPH_DIALOG_ID: {
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

        // notice about help was shown, do not present it again
        editor.putBoolean(READ_HELP_FIRST_FLAG, true);

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

                // generate the default init script if it doesn't exist
                // WARNING : this resets the mInitScriptName to its default
                generateDefaultInitScript();
            }
        }
    }

    public void onRequestPermissionsResult (int requestCode,
                                            String[] permissions,
                                            int[] grantResults) {
        if (requestCode == 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            // generate the default init script if it doesn't exist
            // WARNING : this resets the mInitScriptName to its default
            generateDefaultInitScript();
    }

    /**
     * Sets the given function call button's listeners and associated script and name.
     *
     * @param button is the button to be set.
     * @param index  is the button index.
     */
    private void setFunctionButton(Button button, int index) {
        final int _index = index;

        if (mFunctionTitles[_index] != null &&
            !mFunctionTitles[_index].isEmpty())
            button.setText(mFunctionTitles[_index]);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mFunctionScripts[_index] != null && !mFunctionScripts[_index].isEmpty()) {
                    mCalculator.pushValueOnStack();
                    mCalculator.appendHistory(mFunctionScripts[_index] + "\n");
                    executeScript(mFunctionScripts[_index]);
                } else {
                    // display a message explaining how to set a button function
                    new MessageDisplayer(mActivity, getString(R.string.button_help), -1).run();
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
        mCalculator = new Calculator(this);
        mGraphView = new GraphView(getApplicationContext());

        // set up handlers
        // edit field
        mValueField = findViewById(R.id.input_value);

        // backspace button
        ImageButton backButton = findViewById(R.id.backspaceButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCalculator.isValueEmpty())
                    return;
                String value = mCalculator.getValue();
                value = value.substring(0, value.length() - 1);
                mCalculator.setValue(value);
                mValueField.setText(value);
            }
        });
        backButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mCalculator.isValueEmpty())
                    return false;
                mCalculator.setValue("");
                mValueField.setText("");
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
                mCalculator.setValue(mCalculator.getStack().get(mCalculator.getStackSize() - position - 1).toString());
                mValueField.setText(mCalculator.getValue());
            }
        });

        // restore potential stack and edited value
        onRestoreInstanceState(savedInstanceState);

        // calcpad handlers
        Button button = findViewById(R.id.button_0);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("0");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_1);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("1");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_2);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("2");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_3);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("3");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("4");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_5);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("5");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_6);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("6");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_7);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("7");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_8);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("8");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_9);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.appendValue("9");
                mValueField.setText(mCalculator.getValue());
            }
        });
        button = findViewById(R.id.button_add);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("+\n");
                mCalculator.add(false);
            }
        });
        button = findViewById(R.id.button_sub);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("-\n");
                mCalculator.sub(false);
            }
        });
        button = findViewById(R.id.button_mul);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("*\n");
                mCalculator.mul(false);
            }
        });
        button = findViewById(R.id.button_div);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("/\n");
                mCalculator.div(false);
            }
        });
        button = findViewById(R.id.button_enter);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
            }
        });
        button = findViewById(R.id.button_dot);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String value = mCalculator.getValue();
                if (!value.isEmpty() && !value.contains(".")) {
                    mCalculator.appendValue(".");
                    mValueField.setText(mCalculator.getValue());
                }
            }
        });
        button = findViewById(R.id.button_neg);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // neg must be added to history only when affecting the stack
                // else, it would affect the value twice (when negating the value)
                // then when the negated value is pushed on the stack...
                if (mCalculator.isValueEmpty())
                    mCalculator.appendHistory("neg\n");
                mCalculator.neg(false);
            }
        });
        button = findViewById(R.id.button_dup);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("dup\n");
                mCalculator.dup(false);
            }
        });
        button = findViewById(R.id.button_drop);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("drop\n");
                mCalculator.drop(false);
            }
        });
        button = findViewById(R.id.button_swap);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("swap\n");
                mCalculator.swap(false);
            }
        });
        button = findViewById(R.id.button_clear);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCalculator.pushValueOnStack();
                mCalculator.appendHistory("clear\n");
                mCalculator.clear(false);
            }
        });

        // get the preferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

        // initial dialog proposing to read the doc:
        if (!prefs.contains(READ_HELP_FIRST_FLAG))
            new MessageDisplayer(mActivity, getString(R.string.read_help_first), -1).run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // get the preferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

        if (mInitScriptName != null) {
            // run the init script
            try {
                executeScript(readFile(mInitScriptName));
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
        button = findViewById(R.id.button_fn16);
        setFunctionButton(button, 15);
        button = findViewById(R.id.button_fn17);
        setFunctionButton(button, 16);
        button = findViewById(R.id.button_fn18);
        setFunctionButton(button, 17);
        button = findViewById(R.id.button_fn19);
        setFunctionButton(button, 18);
        button = findViewById(R.id.button_fn20);
        setFunctionButton(button, 19);
        button = findViewById(R.id.button_fn21);
        setFunctionButton(button, 20);
        button = findViewById(R.id.button_fn22);
        setFunctionButton(button, 21);
        button = findViewById(R.id.button_fn23);
        setFunctionButton(button, 22);
        button = findViewById(R.id.button_fn24);
        setFunctionButton(button, 23);
        button = findViewById(R.id.button_fn25);
        setFunctionButton(button, 24);
        button = findViewById(R.id.button_fn26);
        setFunctionButton(button, 25);
        button = findViewById(R.id.button_fn27);
        setFunctionButton(button, 26);
        button = findViewById(R.id.button_fn28);
        setFunctionButton(button, 27);
        button = findViewById(R.id.button_fn29);
        setFunctionButton(button, 28);
        button = findViewById(R.id.button_fn30);
        setFunctionButton(button, 29);
        button = findViewById(R.id.button_fn31);
        setFunctionButton(button, 30);
        button = findViewById(R.id.button_fn32);
        setFunctionButton(button, 31);
        button = findViewById(R.id.button_fn33);
        setFunctionButton(button, 32);
        button = findViewById(R.id.button_fn34);
        setFunctionButton(button, 33);
        button = findViewById(R.id.button_fn35);
        setFunctionButton(button, 34);

    }

    /**
     * Draws a function of x.
     *
     * @param expression is a mathematical funtion of x (expressed as an infixed expression).
     */
    void drawFunction(String expression) {
        String script =
                    "?\"" + getString(R.string.min_x) + "\n" +
                    "dup\n" +
                    "?min_x\n" +
                    "?x\n" +
                    "\n" +
                    "?\"" + getString(R.string.max_x) + "\n" +
                    "?max_x\n" +
                    "\n" +
                    "?\"" + getString(R.string.min_y) + "\n" +
                    "?min_y\n" +
                    "\n" +
                    "?\"" + getString(R.string.max_y) + "\n" +
                    "?max_y\n" +
                    "\n" +
                    "0\n" +
                    "0\n" +
                    "0\n" +
                    "erase\n" +
                    "\n" +
                    "255\n" +
                    "255\n" +
                    "255\n" +
                    "color\n" +
                    "\n" +
                    "!min_x\n" +
                    "!max_x\n" +
                    "!min_y\n" +
                    "!max_y\n" +
                    "range\n" +
                    "\n" +
                    "!min_x\n" +
                    "0\n" +
                    "!max_x\n" +
                    "0\n" +
                    "line\n" +
                    "0\n" +
                    "!min_y\n" +
                    "0\n" +
                    "!max_y\n" +
                    "line\n" +
                    "\n" +
                    "255\n" +
                    "0\n" +
                    "0\n" +
                    "color\n" +
                    "\n" +
                    "!x\n" +
                    "!max_x\n" +
                    "<=\n" +
                    "while\n" +
                    "    drop\n" +
                    "    infixed " + expression + "\n" +
                    "    !x\n" +
                    "    swap\n" +
                    "    plot\n" +
                    "    !x\n" +
                    "    0.025\n" +
                    "    +\n" +
                    "    dup\n" +
                    "    ?x\n" +
                    "    !max_x\n" +
                    "    <=\n" +
                    "end_while\n" +
                    "drop\n";

        executeScript(script);
    }

    private void generateScript(int Id, String filename) {
        try {
            InputStream is = getResources().openRawResource(Id);
            InputStreamReader isr = new InputStreamReader(is);
            String s = "";
            int c;
            while ((c = isr.read()) != -1)
                s += (char) c;
            writeFile(filename, s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateDefaultInitScript() {
        // create the init script out of the resources if it doesn't exist yet
        String initScriptName = getDefaultDataAbsolutePath() + "/" + DEFAULT_INIT_SCRIPT_NAME + SCRIPT_EXTENSION;
        if (!new File(initScriptName).exists()) {
            // set the init script name upon initial script creation only
            // so it can be modified by the user
            generateScript(R.raw.functions, initScriptName);
        }
        mInitScriptName = initScriptName;
    }

    private void generateSampleScripts() {
        // generate all the samples if they do not exist yet
        generateScript(R.raw.array_test, getDefaultDataAbsolutePath() + "/" + ARRAY_TEST_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.butterfly, getDefaultDataAbsolutePath() + "/" + BUTTERFLY_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.decrement_speed_test, getDefaultDataAbsolutePath() + "/" + DECREMENT_SPEED_TEST_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_circles, getDefaultDataAbsolutePath() + "/" + DRAW_CIRCLES_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_log2, getDefaultDataAbsolutePath() + "/" + DRAW_LOG2_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_polygon, getDefaultDataAbsolutePath() + "/" + DRAW_POLYGON_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_pow2, getDefaultDataAbsolutePath() + "/" + DRAW_POW2_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_sine, getDefaultDataAbsolutePath() + "/" + DRAW_SINE_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_sine_color, getDefaultDataAbsolutePath() + "/" + DRAW_SINE_COLOR_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.draw_sine_color3d, getDefaultDataAbsolutePath() + "/" + DRAW_SINE_COLOR3D_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.inner_while_test, getDefaultDataAbsolutePath() + "/" + INNER_WHILE_TEST_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.rand_plot, getDefaultDataAbsolutePath() + "/" + RAND_PLOT_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.whirl, getDefaultDataAbsolutePath() + "/" + WHIRL_SCRIPT_NAME + SCRIPT_EXTENSION);
        generateScript(R.raw.x_pow_y, getDefaultDataAbsolutePath() + "/" + X_POW_Y_SCRIPT_NAME + SCRIPT_EXTENSION);

        displayMessage(getString(R.string.scripts_generated));
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
        helpView.appendText("\t\t'...' buttons can be programmed (long press) to call a script, a java math (eg math_call sqrt) or script function (eg funcall average) , via a single line script.\n", 0, false);
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
        helpView.appendText("\n\nScripts/Debug.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be debugged (see .o3s provided examples). Functions defined (fundef) in debug mode can't be used outside of the debugged script (they're volatile).\n", 0, false);
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
        helpView.appendText("\t\tPops up a dialog where one can enter an infixed expression to be evaluated (with proper operator precedence). Don't prefix the expression with 'infixed'.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Stop menu :\n", 0x008888, true);
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
        helpView.appendText("\n\nScripts/Show Samples.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tOpens a web browser on the o3 scripts pages on github.\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Generate Samples.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tGenerates sample scripts on the phone so you can play with them (try debugging them).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nGraph View/Draw Function.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tDraws an infixed expression (with proper operator precedence), between the given min x, max x, min y and max y. Don't prefix the expression with 'infixed'.\n", 0, false);
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

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nGraph View/Load.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPrompts the user for a .png filename to load the graph from (no path, no extension expected).\n", 0, false);
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
        helpView.appendText("\t\t&lt&gt, &lt, &lt=, &gt, &gt= : pop the two topmost values and pushes the result (0 means false, any other value means true) of their comparison on the stack.\n", 0, false);
        helpView.appendText("\t\tif, [else], end_if : conditional block[s], if and else do not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\twhile, end_while : iteration block[s], while does not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\tfundef _f, end_fundef : defines a function _f which can later be invoked (until deleted) from any script during the session.\n", 0, false);
        helpView.appendText("\t\tfundel _f : deletes (forgets) the _f function.\n", 0, false);
        helpView.appendText("\t\tfuncall _f : calls the script function _f.\n", 0, false);
        helpView.appendText("\t\t!\"_message : displays _message in a blocking modal dialog.\n", 0, false);
        helpView.appendText("\t\t?\"_prompt : displays _prompt message in a value prompting & blocking modal dialog. Pushes the value entered by the user on the stack.\n", 0, false);
        helpView.appendText("\t\tinfixed _expression : converts and evaluates the provided infixed _expression. The debugger can step into the evaluation. variables (!_v) can be used in infixed expressions. prefix function calls with fc@ and math calls with mc@ (eg : infixed fc@logN(2,mc@pow(2, 8)))\n", 0, false);
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
        helpView.appendText("\t\tdebug_break : when in debug mode, pops up a modal dialog reading various debugging information. The standard 'step over', 'step in', 'step out' are supported. 'resume' resumes the script execution and ends the debugging session. 'exit' terminates the script..\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\n\nHave fun using O3. Gilles Fabre(c) 2019.\n\n", 0x00FFFF, true);
        helpView.resetFontSize();
    }

    public void setValueField(String value) {
        mValueField.setText(value);
    }

    /**
     * This class carries the necessary data to present the user a message
     * to read. This is instanciated in the background script engine thread
     * and passed over to the main UI thread through a message.
     */
    class DisplayMessage {
        String mMessage;
        Semaphore mWaitForRead;

        public DisplayMessage(String message) {
            mMessage = message;
            mWaitForRead = new Semaphore(0);
        }
    }

    /**
     * CALL FROM UI or NON UI thread : Displays a message in an application modal (blocking) dialog
     *
     * @param message is the message to be displayed.
     */
    public void displayMessage(String message) {
        if (Looper.getMainLooper().isCurrentThread()) {
            GenericDialog.displayMessage(this, message);
        } else {
            doDisplayMessage(message);
        }
    }

    /**
     * DO NOT CALL FROM UI THREAD : Displays a message in an application modal (blocking) dialog.
     *
     * @param message is the message to be displayed.
     */
    public void doDisplayMessage(String message) {
        DisplayMessage displayMessage = new DisplayMessage(message);
        mHandler.obtainMessage(DISPLAY_MESSAGE, displayMessage).sendToTarget();
        try {
            displayMessage.mWaitForRead.acquire();
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
        BigDecimal mValue;
        String mMessage;
        Semaphore mWaitForValue;

        public PromptForValueMessage(String message) {
            mValue = BigDecimal.valueOf(0);
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
        mCalculator.doPushValueOnStack(prompt.mValue);
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
     * Runs the given script.
     *
     * @param script is the script text.
     */
    public void interpretScript(String script) {
        if (ScriptEngine.isRunning())
            return;

        final ScriptEngine engine = new ScriptEngine(mActivity, mCalculator, script);
        new Thread() {
            @Override
            public void run() {
                // Moves the current Thread into the background
                android.os.Process.setThreadPriority(SCRIPT_ENGINE_PRIORITY);
                try {
                    engine.interpretScript();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * Compiles and execute the given script.
     *
     * @param script is the script text.
     */
    public void executeScript(String script) {
        if (ScriptEngine.isRunning())
            return;

        final ScriptEngine engine = new ScriptEngine(mActivity, mCalculator, script);
        new Thread() {
            @Override
            public void run() {
                // Moves the current Thread into the background
                android.os.Process.setThreadPriority(SCRIPT_ENGINE_PRIORITY);
                try {
                    engine.executeScript();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     *Debugs the given script.
     *
     * @param script is the script text.
     */
    public void debugScript(String script) {
        if (ScriptEngine.isRunning())
            return;

        final ScriptEngine engine = new ScriptEngine(mActivity, mCalculator, script);
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
    }

    /**
     * Debugs the given script file
     *
     * @param filename is the fully qualified script filename.
     * @return the script was found and spawned.
     */
    public boolean doDebugScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            debugScript(script);
            found = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return found;
    }

    /**
     * Runs the given script file
     *
     * @param filename is the fully qualified script filename.
     * @return the script was found and spawned.
     */
    public boolean doInterpretScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            interpretScript(script);
            found = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return found;
    }

    /**
     * Runs the given script file
     *
     * @param filename is the fully qualified script filename.
     * @return the script was found and spawned.
     */
    public boolean doExecuteScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            executeScript(script);
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
    public boolean doInterpretInnerScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            new ScriptEngine(mActivity, mCalculator, script).interpretScript();
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
    public boolean doExecuteInnerScriptFile(String filename) {
        boolean found = false;
        try {
            String script = readFile(filename);
            new ScriptEngine(mActivity, mCalculator, script).executeScript();
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
        if (mCalculator.getStackSize() < 2)
            return false;

        BigDecimal y = mCalculator.getStack().pop();
        BigDecimal x = mCalculator.getStack().pop();

        mGraphView.doPlot(x.doubleValue(), y.doubleValue());

        return true;
    }

    public boolean doPlot3D() {
        if (mCalculator.getStackSize() < 3)
            return false;

        BigDecimal z = mCalculator.getStack().pop();
        BigDecimal y = mCalculator.getStack().pop();
        BigDecimal x = mCalculator.getStack().pop();

        mGraphView.doPlot3D(x.doubleValue(), y.doubleValue(), z.doubleValue());

        return true;
    }

    public boolean doLine() {
        if (mCalculator.getStackSize() < 4)
            return false;

        BigDecimal y1 = mCalculator.getStack().pop();
        BigDecimal x1 = mCalculator.getStack().pop();
        BigDecimal y0 = mCalculator.getStack().pop();
        BigDecimal x0 = mCalculator.getStack().pop();

        mGraphView.doLine(x0.doubleValue(), y0.doubleValue(), x1.doubleValue(), y1.doubleValue());

        return true;
    }

    public boolean doLine3D() {
        if (mCalculator.getStackSize() < 6)
            return false;

        BigDecimal z1 = mCalculator.getStack().pop();
        BigDecimal y1 = mCalculator.getStack().pop();
        BigDecimal x1 = mCalculator.getStack().pop();
        BigDecimal z0 = mCalculator.getStack().pop();
        BigDecimal y0 = mCalculator.getStack().pop();
        BigDecimal x0 = mCalculator.getStack().pop();

        mGraphView.doLine3D(x0.doubleValue(), y0.doubleValue(), z0.doubleValue(), x1.doubleValue(), y1.doubleValue(), z1.doubleValue());

        return true;
    }

    public boolean doErase() {
        if (mCalculator.getStackSize() < 3)
            return false;

        BigDecimal b = mCalculator.getStack().pop();
        BigDecimal g = mCalculator.getStack().pop();
        BigDecimal r = mCalculator.getStack().pop();

        mGraphView.doErase(r.doubleValue(), g.doubleValue(), b.doubleValue());

        return true;
    }

    public boolean doSetRange() {
        if (mCalculator.getStackSize() < 4)
            return false;

        BigDecimal yMax = mCalculator.getStack().pop();
        BigDecimal yMin = mCalculator.getStack().pop();
        BigDecimal xMax = mCalculator.getStack().pop();
        BigDecimal xMin = mCalculator.getStack().pop();

        mGraphView.setRange(xMin.doubleValue(), xMax.doubleValue(), yMin.doubleValue(), yMax.doubleValue());

        return true;
    }

    public boolean doSetPov3D() {
        if (mCalculator.getStackSize() < 3)
            return false;

        BigDecimal z = mCalculator.getStack().pop();
        BigDecimal y = mCalculator.getStack().pop();
        BigDecimal x = mCalculator.getStack().pop();

        mGraphView.doPov3D(x.doubleValue(), y.doubleValue(), z.doubleValue());

        return true;
    }

    public boolean doSetColor() {
        if (mCalculator.getStackSize() < 3)
            return false;

        BigDecimal b = mCalculator.getStack().pop();
        BigDecimal g = mCalculator.getStack().pop();
        BigDecimal r = mCalculator.getStack().pop();

        mGraphView.setColor(r.doubleValue(), g.doubleValue(), b.doubleValue());

        return true;
    }

    public boolean doSetDotSize() {
        if (mCalculator.getStackSize() < 1)
            return false;

        BigDecimal s = mCalculator.getStack().pop();

        mGraphView.setDotSize(s.doubleValue());

        return true;
    }

    /* ---------------------------  DEBUG HANDLING -----------------------------------------------*/

    private static DebugView mDebugView = null;

    /**
     * Returns a string containing the stack content.
     *
     * @return the stack content.
     */
    public String getStackDebugInfo() {
        StringBuilder stack = new StringBuilder();
        for (int i = mCalculator.getStackSize() - 1; i >= 0; i--)
            stack.append("stack(").append(i).append(") : ").append(mCalculator.getStack().get(i)).append("\n");

        return stack.toString();
    }

    public DebugView.DebugState getDebugState() {
        return getDebugView().getDebugState();
    }

    public void doUpdateDebugInfo(int yyline, String script, String toString, String stackDebugInfo) {
        getDebugView().updateDebugInfo(yyline, script, toString, stackDebugInfo);
    }

    public void doShowDebugView() {
        Semaphore userAction = new Semaphore(0);
        mHandler.obtainMessage(SHOW_DEBUG_VIEW_MESSAGE, userAction).sendToTarget();
        try {
            userAction.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void doHideDebugView() {
        mHandler.obtainMessage(HIDE_DEBUG_VIEW_MESSAGE).sendToTarget();
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
