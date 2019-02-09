package com.gfabre.android.o3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
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
    private static final int        INIT_SCRIPT_DIALOG_ID = 4;
    private static final int        HELP_DIALOG_ID = R.layout.log_view;
    private static final int        GRAPH_DIALOG_ID = R.layout.graph_view;
    private static final String     SCRIPT_EXTENSIONS = ".o3s .txt";
    private static final String     INIT_SCRIPT_NAME = "InitScriptFilename";

    private static  Method[] mMethods = null;

    private Stack<Double>   mStack = new Stack<Double>();
    private String          mValue = "";
    private EditText        mValueField = null;
    private ListView        mStackView = null;
    private ArrayAdapter    mStackAdapter = null;
    private Activity        mActivity;
    private GraphView mGraphView = null;
    private Menu            mScriptFunctionsMenu = null;
    private String          mInitScriptName = null;

    public boolean hasValueOnStack() {
        return !mStack.isEmpty();
    }

    public void pushValueOnStack(Double value) {
        mStack.push(value);
        populateStackView();
    }

    public Double popValueFromStack() {
        return mStack.isEmpty() ? Double.NaN : mStack.pop();
    }

    public Double peekValueFromStack() {
        return mStack.isEmpty() ? Double.NaN : mStack.peek();
    }

    /**
     * If present, pushes the currently edited value onto the stack.
     */
    public void pushValueOnStack() {
        if (mValue.isEmpty())
        return;

        // make sure we're pushing a properly formatted double
        try {
            Double val = new Double(mValue);
            pushValueOnStack(val);
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
    private void populateStackView()  {
        if (mStackView == null)
            return;

        mStackAdapter.clear();
        Integer depth = 1;
        for (int i = mStack.size() - 1; i >= 0; i--) {
            mStackAdapter.add(depth.toString() + ": " + mStack.elementAt(i).toString());
            depth++;
        }
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
                    return ScriptEngine.runFunction((CalculatorActivity)mActivity, f);
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

        // stack menu
        Menu submenu = menu.addSubMenu(getString(R.string.stack));

        /**
         * ROLLs the top value N position down the stack
         */
        MenuItem item = submenu.add(getString(R.string.rolln));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                doRollN();
                return true;
            }
        });

        /**
         * SWAPs the top value with N's position value on the stack
         */
        item = submenu.add(getString(R.string.swapn));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                doSwapN();
                return true;
            }
        });

        /**
         * DUPLICATEs the top value N times on the stack
         */
        item = submenu.add(getString(R.string.dupn));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                doDupN();
                return true;
            }
        });

        /**
         * DROPs the first N top values from the stack
         */
        item = submenu.add(getString(R.string.dropn));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                doDropN();
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
                new MathFunctionChooser((CalculatorActivity)mActivity);
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
                File dir = Environment.getExternalStorageState() == null ? Environment.getDataDirectory() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                new FileChooser(RUN_SCRIPT_DIALOG_ID, mActivity, SCRIPT_EXTENSIONS, dir == null ? "/" : dir.getPath());
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
                new EditDialog((CalculatorActivity) mActivity, dir.getAbsolutePath() + "/" + getString(R.string.new_script_name));
                return true;
            }
        });

        /**
         * Displays the graph view
         */
        item = submenu.add(R.string.graph);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                GenericDialog graphDialog = new GenericDialog(R.layout.graph_view, getString(R.string.graph_view), true);
                graphDialog.show(mActivity.getFragmentManager(), getString(R.string.graph_view));
                return true;
            }
        });

        /**
         * The usual about/help menu
         */
        item = menu.add(getString(R.string.about));
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

        savedInstanceState.putString("VALUE", mValue);
        double[] doubleArray = new double[mStack.size()];
        for (int i = 0; i < mStack.size(); i++)
            doubleArray[i] = mStack.get(i);
        savedInstanceState.putDoubleArray("STACK", doubleArray);
    }

    /**
     * Manually pops the execution context when getting back to life.
     */
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;

        super.onRestoreInstanceState(savedInstanceState);

        mValue = savedInstanceState.getString("VALUE");
        double[] doubleArray = savedInstanceState.getDoubleArray("STACK");
        mStack.clear();
        for (int i = 0; i < doubleArray.length; i++)
            mStack.push(new Double(doubleArray[i]));

        mValueField.setText(mValue);
        populateStackView();
    }

    /**
     * Read a complete file into a String and return it.
     * @param fileName is the name of the file to be loaded
     *
     * @return the content of the given file as a String.
     * @throws IOException
     */
    public String readFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
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
                pushValueOnStack();

                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                String script = null;
                try {
                    script = readFile(filename);
                    final ScriptEngine engine = new ScriptEngine((CalculatorActivity)mActivity, script);
                    new Runnable(){
                        @Override
                        public void run() {
                            // Moves the current Thread into the background
                            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            try {
                                engine.runScript();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            break;

            case DEBUG_SCRIPT_DIALOG_ID : {
                // run the selected script
                pushValueOnStack();

                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                String script = null;
                try {
                    script = readFile(filename);
                    final ScriptEngine engine = new ScriptEngine((CalculatorActivity)mActivity, script);
                    new Runnable(){
                        @Override
                        public void run() {
                            // Moves the current Thread into the background
                            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            try {
                                engine.debugScript();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            break;

            case EDIT_SCRIPT_DIALOG_ID :  {
                // edit the selected script
                String filename = dialog.getBundle().getString(FileChooser.FILENAME);
                new EditDialog(this, filename);
            }
            break;

            case INIT_SCRIPT_DIALOG_ID : {
                // saves the init script name in the preferences
                // it'll be run every time the program starts
                mInitScriptName = dialog.getBundle().getString(FileChooser.FILENAME);

                // run the init script
                try {
                    new ScriptEngine(this, readFile(mInitScriptName)).runScript();
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
        // get the init script from the preferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(INIT_SCRIPT_NAME, mInitScriptName);
        editor.commit();

        super.onStop();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onStart() {
        super.onStart();
        // permission in manifest ain't enough now...
        // Here, thisActivity is the current activity
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

        // get the init script from the preferences
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        if (prefs.contains(INIT_SCRIPT_NAME)) {
            mInitScriptName = prefs.getString(INIT_SCRIPT_NAME, null);

            // run the init script
            try {
                new ScriptEngine(this, readFile(mInitScriptName)).runScript();
            } catch (Exception e) {
                //doDisplayMessage(getString(R.string.init_script_error) + e.getLocalizedMessage()); can't be run at this stage
                mInitScriptName = null;
            }
        }
    }

    /**
     * Called upon activity start to setup the whole thingy.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // application runs only in portrait mode
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
                doAdd();
            }
        });
        button = findViewById(R.id.button_sub);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doSub();
            }
        });
        button = findViewById(R.id.button_mul);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doMul();
            }
        });
        button = findViewById(R.id.button_div);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiv();
            }
        });
        button =   findViewById(R.id.button_enter);
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
                doNeg();
            }
        });
        button = findViewById(R.id.button_dup);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDup();
            }
        });
        button = findViewById(R.id.button_drop);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDrop();
            }
        });
        button = findViewById(R.id.button_swap);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doSwap();
            }
        });
        button = findViewById(R.id.button_clear);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doClear();
            }
        });
    }

    /**
     * Returns an instance of the given type.
     *
     * @param classname
     * @return on object corresponding to the passed  className
     */
    protected Object castDoubleToType(String classname, Double value) throws ClassNotFoundException {
        switch(classname) {
            case "Long":
            case "long":
                return new Long(value.longValue());

            case "Integer":
            case "int":
                return new Integer(value.intValue());

            case "Float":
            case "float":
                return new Float(value);

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
    protected Double castTypeToDouble(String classname, Object value) throws ClassNotFoundException {
        switch(classname) {
            case "Long":
                return new Double((Long)value);

            case "long":
                return new Double((long)value);

            case "Integer":
                return new Double((Integer)value);

            case "int":
                return new Double((int)value);

            case "Float":
                return new Double((Float)value);

            case "float":
                return new Double((float)value);

            case "Double":
                return (Double)value;

            case "double":
                return new Double((double)value);
        }

        return null;
    }

    public Method[] getJavaMathMethods() {
        if (mMethods == null) {
            Class math = null;
            try {
                math = Class.forName("java.lang.Math");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            mMethods = math.getMethods();
        }

        return mMethods;
    }

    /**
     * Calls into the selected Java.math passed method using introspection.
     *
     * @param method is the math method to be called per user request.
     */
    public boolean invokeMathFunction(Method method) {
        // invoke the selected function

        // first make sure we have the appropriate number of elements
        // on the stack
        pushValueOnStack();
        Type[] params = method.getParameterTypes();
        int numParams = params.length;
        if (numParams > mStack.size())
            return false;

        // prepare the parameters.
        Object []argObjects = new Object[numParams];
        while (--numParams >= 0) {
            try {
                argObjects[numParams] = castDoubleToType(params[numParams].toString(), mStack.pop());
            } catch (ClassNotFoundException e) {
                GenericDialog.displayMessage(this, getString(R.string.param_cast_err) + e.getMessage());
                return false;
            }
        }

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
            populateStackView();
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
        helpView.appendText("\nO3: Operand Operand Operator.. calc", 0x00FFFF, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\n<a href=\"url\">https://github.com/gilles-fabre/O3</a>\n\n", 0xCCCCCC, true);
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
        helpView.appendText("\nNOTE: all of the these actions, except 'N', first push the edited value (if present) on the stack. Selecting a value in the stack copies it to the edit value field.\n", 0, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nStack menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\t'Roll N' rolls the 2nd value down the stack by the number of positions given by the 1st value on the stack\n", 0, false);
        helpView.appendText("\t\t'Swap N' swaps the 2nd value on the stack with the one at the position given by the 1st value on the stack\n", 0, false);
        helpView.appendText("\t\t'Dup N' duplicates the 2nd value on the stack the number of times given by the 1st value on the stack\n", 0, false);
        helpView.appendText("\t\t'Drop N' drops as many values off the stack as the number given by the 1st value on the stack\n", 0, false);
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
        helpView.appendText("\n\nScripts/Run.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be run (see .o3s provided examples).\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts/Debug.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog where one can pick an O3 script to be debugged (see .o3s provided examples).\n", 0, false);
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
        helpView.appendText("\n\nScripts/Graph View.. menu :\n", 0x008888, true);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.SMALL_FONT);
        helpView.appendText("\t\tPops up a dialog containing the graphical (using orthonormal coordinates) result of the run o3s scripts (if containing graphical calls).\n", 0, false);
        helpView.resetFontSize();

        helpView.appendText("\nNOTE: running any script first pushes the edited value (if present) on the stack.\n", 0, true);

        helpView.setFontSize(ColorLogView.MEDIUM_FONT);
        helpView.appendText("\n\nScripts syntax :\n", 0x008888, true);
        helpView.appendText("\t\trolln, swap, dup, dupn, clear : have the same effect as their UI peers.\n", 0, false);
        helpView.appendText("\t\tstack_size : pushes the stack size onto the stack.\n", 0, false);
        helpView.appendText("\t\t_value : pushes the _value onto the stack.\n", 0, false);
        helpView.appendText("\t\t+, -, *, / : have the same effect as their UI equivalents.\n", 0, false);
        helpView.appendText("\t\t% : Pushes the modulus (remaining part of division) on the stack.\n", 0, false);
        helpView.appendText("\t\t&lt&gt, &lt, &lt=, &gt, &gt= : pop the two topmost values and pushes the result (0 means false, not 0.0 means true) of their comparison on the stack.\n", 0, false);
        helpView.appendText("\t\tif, [else], end_if : conditional block[s], if and else do not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\twhile, end_while : iteration block[s], while does not pop the 'test' value off the stack.\n", 0, false);
        helpView.appendText("\t\tfundef, end_fundef : defines a function which can later be invoked (until deleted) from any script during the session.\n", 0, false);
        helpView.appendText("\t\tfundel : deletes (forgets) the given function.\n", 0, false);
        helpView.appendText("\t\t!\"_message : displays _message in a blocking modal dialog.\n", 0, false);
        helpView.appendText("\t\t?\"_prompt : displays _prompt message in a value prompting & blocking modal dialog. Pushes the value entered by the user on the stack.\n", 0, false);
        helpView.appendText("\t\t?\"_variable : pops the topmost value off the stack into the given _variable.\n", 0, false);
        helpView.appendText("\t\t!\"_variable : pushes the given _variable's value onto the stack.\n", 0, false);
        helpView.appendText("\t\t?\"[]_array : pops the 2nd value off the stack into the given _array at the index given by the 1st value.\n", 0, false);
        helpView.appendText("\t\t!\"[]_array : pushes on the stack the value from the _array at the index given by the stack's topmost value.\n", 0, false);
        helpView.appendText("\t\terase : clears the graphical view background with the r,g,b indexes given by the first three values of the stack.\n", 0, false);
        helpView.appendText("\t\tcolor : sets the graphical view drawing color with the r,g,b indexes given by the first three values of the stack.\n", 0, false);
        helpView.appendText("\t\tdot_size : sets the point drawing size to that given by the topmost value of the stack.\n", 0, false);
        helpView.appendText("\t\trange : sets the graphical view virtual orthonormal extent to the xMin, xMax, yMin, yMax given by the first fours values of the stack.\n", 0, false);
        helpView.appendText("\t\tplot : draws with the current color, dot size, at the x, y coordinates given by the two topmost values of the stack.\n", 0, false);
        helpView.appendText("\t\tdebug_break : pops up a modal dialog reading various debugging information. The standard 'step over', 'step in', 'step out' are supported. 'done' resumes the script execution and ends the debugging session. 'exit' terminates the script..\n", 0, false);
        helpView.resetFontSize();

        helpView.setFontSize(ColorLogView.BIG_FONT);
        helpView.appendText("\n\nHave fun using O3. Gilles Fabre(c) 2019.\n\n", 0x00FFFF, true);
        helpView.resetFontSize();
    }

    /**
     * Java math call
     */
    public boolean doJavaMathCall(String function) {
        Method[] methods = getJavaMathMethods();
        if (methods == null)
            return false;

        // lookup function
        for (Method method : methods) {
            if (function.equals(method.getName())) {
                return invokeMathFunction(method);
            }
        }

        return false;
    }

    /**
     * Public Basic Maths Functions
     *
     */
    public boolean doAdd() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 +
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 + v2);
        populateStackView();

        return true;

    }

    public boolean doSub() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 /
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 - v2);
        populateStackView();

        return true;
    }

    public boolean doDiv() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 / v2);
        populateStackView();

        return true;
    }

    public boolean doMul() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 *
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 * v2);
        populateStackView();

        return true;
    }

    public boolean doNeg() {
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
            populateStackView();

            return true;
        }

        return false;
    }

    public boolean doModulo() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 % v2);
        populateStackView();

        return true;
    }

    public boolean doEqual() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 == v2 ? 1.0 : 0.0);
        populateStackView();

        return true;
    }

    public boolean doNotEqual() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 == v2 ? 0.0 : 1.0);
        populateStackView();

        return true;
    }

    public boolean doLessThan() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 < v2 ? 1.0 : 0.0);
        populateStackView();

        return true;
    }

    public boolean doLessThanOrEqual() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 <= v2 ? 1.0 : 0.0);
        populateStackView();

        return true;
    }

    public boolean doGreaterThan() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 > v2 ? 1.0 : 0.0);
        populateStackView();

        return true;
    }

    public boolean doGreaterThanOrEqual() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        Double v2 = mStack.pop();
        Double v1 = mStack.pop();
        mStack.push(v1 >= v2 ? 1.0 : 0.0);
        populateStackView();

        return true;
    }

    /**
     * Public Basic Stack Functions
     *
     */
    public boolean doRollN() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i >= mStack.size())
            return false;

        Double val = mStack.pop();
        Stack<Double> st = new Stack<Double>();
        while (--i >= 0)
            st.push(mStack.pop());

        mStack.push(val);
        while (!st.isEmpty())
            mStack.push(st.pop());

        populateStackView();

        return true;
    }

    public boolean doDup() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        mStack.push(mStack.peek());
        populateStackView();

        return true;
    }

    public boolean doDupN() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (mStack.isEmpty())
            return false;

        Double val = mStack.peek();
        while (--i >= 0)
            mStack.push(val);
        populateStackView();

        return true;
    }

    public boolean doDrop() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        mStack.pop();
        populateStackView();

        return true;
    }

    public boolean doDropN() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i > mStack.size())
            return false;

        while (--i >= 0)
            mStack.pop();
        populateStackView();

        return true;
    }

    public boolean doSwap() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        Double v1 = mStack.pop();
        Double v2 = mStack.pop();
        mStack.push(v1);
        mStack.push(v2);
        populateStackView();

        return true;
    }

    public boolean doSwapN() {
        pushValueOnStack();
        if (mStack.isEmpty())
            return false;

        Double i = mStack.pop();
        if (i > mStack.size())
            return false;

        Double v1 = mStack.elementAt(0);
        Double v2 = mStack.elementAt(i.intValue() - 1);
        mStack.setElementAt(v1, i.intValue() - 1);
        mStack.setElementAt(v2, 0);
        populateStackView();

        return true;
    }

    public void doClear() {
        pushValueOnStack();
        mStack = new Stack<Double>();
        populateStackView();
    }

    public void doStackSize() {
        mStack.push(new Double(mStack.size()));
        populateStackView();
    }

    public void doDisplayMessage(String message) {
        GenericDialog.displayMessage(this, message);
    }

    public void doPromptMessage(String message) {
        Double value = new Double(GenericDialog.promptMessage(this,
                                                              InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                                                               message));
        mStack.push(value);
        doUpdate();
    }

    public void doUpdate() {
        // TODO : find why the UI ain't updated here
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStackView.invalidate();
            }
        });
    }

    public String getStackDebugInfo() {
        String stack = "";
        for (int i = mStack.size() - 1; i >= 0; i--) {
            stack += "stack(" + i + ") : " + mStack.get(i) + "\n";
        }

        return stack;
    }

    /**
     * Graphical functions
     */
    public boolean doPlot() {
        pushValueOnStack();
        if (mStack.size() < 2)
            return false;

        Double y = mStack.pop();
        Double x = mStack.pop();

        mGraphView.doPlot(x, y);

        return true;
    }

    public boolean doErase() {
        pushValueOnStack();
        if (mStack.size() < 3)
            return false;

        Double b = mStack.pop();
        Double g = mStack.pop();
        Double r = mStack.pop();

        mGraphView.doErase(r, g, b);

        return true;
    }

    public boolean doRange() {
        pushValueOnStack();
        if (mStack.size() < 4)
            return false;

        Double yMax = mStack.pop();
        Double yMin = mStack.pop();
        Double xMax = mStack.pop();
        Double xMin = mStack.pop();

        mGraphView.doRange(xMin, xMax, yMin, yMax);

        return true;
    }

    public boolean doColor() {
        pushValueOnStack();
        if (mStack.size() < 3)
            return false;

        Double b = mStack.pop();
        Double g = mStack.pop();
        Double r = mStack.pop();

        mGraphView.doColor(r, g, b);

        return true;
    }

    public boolean doDotSize() {
        pushValueOnStack();
        if (mStack.size() < 1)
            return false;

        Double s = mStack.pop();

        mGraphView.doDotSize(s);

        return true;
    }
}
