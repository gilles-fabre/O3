package com.gfabre.android.o3;

import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import com.gfabre.android.utilities.widgets.GenericDialog;
import com.gfabre.android.utilities.widgets.GenericDialog.GenericDialogListener;

/**
 * A simple math function chooser. Uses introspection to list the java.math
 * functions.
 *
 * @author gilles fabre
 * @date December, 2018
 */
public class MathFunctionChooser implements GenericDialogListener {
    private CalculatorActivity  mCalculator;
    private GenericDialog       mDialog;
    private EditText            mFunction;
    private ListView            mFunctionList;
    private ArrayAdapter        mFunctionsAdapter;
    private ArrayList<Method>   mMethods = new ArrayList();
    private Method              mMethod = null;
    private boolean             mSeekFunction = true;

    public static final int MATHS_FUNCTIONS_DIALOG_ID = R.layout.maths_functions_dialog;

    private void selectMethod(int position) {
        mMethod = mMethods.get(position);
        mSeekFunction = false;
        mFunction.setText(mMethod.getName());
        mFunction.setSelection(mFunction.getText().length());
        mFunctionList.setItemChecked(position, true);
        mSeekFunction = true;
    }

    private void seekAndSelectMethod(String function) {
        if (!mSeekFunction)
            return;

        if (function.isEmpty()) {
            mFunctionList.clearChoices();
            mFunctionsAdapter.notifyDataSetChanged();
            mFunctionList.smoothScrollToPosition(0);
            mMethod = null;
            return;
        }

        for (int i = 0; i < mMethods.size(); i++)
            if (mMethods.get(i).getName().startsWith(function)) {
                mFunctionList.setItemChecked(i, true);
                mFunctionList.smoothScrollToPosition(i);
                mMethod = mMethods.get(i);
                return;
            }
    }

    private String getParamList(Method method) {
        if (method == null)
            return "";

        String list = "(";
        Class<?>[] params = method.getParameterTypes();
        for (int j = 0; j < params.length; j++) {
            list += params[j].getSimpleName();
            if (j < params.length - 1)
                list += ", ";
        }
        list += ")";
        return list;
    }

    /**
     * Provides the dialog with the list of available functions. Only static functions
     * are listed (all math functions are static).
     *
     */
    private void populateFunctionList() {
        if (mFunctionList == null)
            return;

        mFunctionsAdapter.clear();

        // retrieve math functions
        Method[] methods = mCalculator.getJavaMathMethods();

        for (Method method : methods) {
            // only want maths stuff, and all are static
            if (!Modifier.isStatic(method.getModifiers()))
                    continue;

            String signature = method.getGenericReturnType().toString();
            signature += "\n\t";
            signature += method.getName();
            signature += getParamList(method);
            mFunctionsAdapter.add(signature);
            mMethods.add(method);
        }
    }

    public MathFunctionChooser(CalculatorActivity activity) {
        mCalculator = activity;

        mDialog = new GenericDialog(MATHS_FUNCTIONS_DIALOG_ID, activity.getString(R.string.pick_math_function), false);
        mDialog.setListener(this);
        mDialog.show(mCalculator.getFragmentManager(), activity.getString(R.string.pick_math_function));
    }

    @Override
    public void onDialogPositiveClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogNegativeClick(int Id, GenericDialog dialog, View view) {
    }

    @Override
    public void onDialogInitialize(int Id, GenericDialog dialog, View view) {
        // get the function search edit field
        mFunction = view.findViewById(R.id.input_value);
        mFunction.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                seekAndSelectMethod(s.toString());
            }
        });

        // get the list and function search field references
        mFunctionList = view.findViewById(R.id.stack);

        // set the selection type and the adapter
        mFunctionList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mFunctionsAdapter = new ArrayAdapter<>(mCalculator, R.layout.simple_row, new ArrayList<String>());
        mFunctionList.setAdapter(mFunctionsAdapter);
        mFunctionList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectMethod(position);
            }
        });
        mFunctionList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
             @Override
             public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                 selectMethod(position);
                 if (mMethod == null)
                     return false;

                 String helpUrl = "https://docs.oracle.com/javase/7/docs/api/java/lang/Math.html#" + mMethod.getName();
                 helpUrl += getParamList(mMethod);
                 Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl));
                 mCalculator.startActivity(intent);

                 return true;
             }
         });

        // populate the list of math functions
        populateFunctionList();
    }

    @Override
    public boolean onDialogValidate(int Id, GenericDialog dialog, View view) {
        if (mMethod != null) {
            mCalculator.invokeAndHistorizeMathFunction(mMethod);
            return true;
        }
        return false;
    }

    @Override
    public void onDismiss(int Id, GenericDialog dialog, View mView) {
    }
}
