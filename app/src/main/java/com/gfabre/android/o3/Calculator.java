package com.gfabre.android.o3;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Stack;

public class Calculator {
    private static Method[] mMethods = null;             // all found java math methods
    private String mHistory = "";                        // all actions history from beginning of time.
    private Stack<BigDecimal> mStack = new Stack<>();    // values stack
    private String mValue = "";                          // value currently edited

    private static CalculatorActivity mActivity;         // the associated activity

    public Calculator(CalculatorActivity activity) {
        mActivity = activity;                           // need a reference to activity for UI access
    }

    // ================================  Stack Management ==========================================
    public void clearStack() {
        mStack.clear();
    }

    public boolean hasValueOnStack() {
        return !mStack.isEmpty();
    }

    public int getStackSize() {
        return mStack.size();
    }

    private boolean pushStackSize(boolean fromEngine) {
        mStack.push(BigDecimal.valueOf(mStack.size()));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    /**
     * If present, pushes the currently edited value onto the stack.
     */
    public void pushValueOnStack() {
        if (mValue.isEmpty())
            return;

        // make sure we're pushing a properly formatted double
        try {
            mHistory += mValue + "\n";
            mStack.push(new BigDecimal(mValue, MathContext.UNLIMITED));
            mActivity.updateStackView(mStack);
        } catch (Exception e) {
            mActivity.displayMessage(mActivity.getString(R.string.invalid_number) + mValue);
        } finally {
            mValue = "";
            mActivity.setValueField(mValue);
        }
    }

    public void updateStackView() {
        mActivity.updateStackView(mStack);
    }

    public Stack<BigDecimal> getStack() {
        return mStack;
    }

    // ===========================  History Management =============================================
    public void appendHistory(String action) {
        mHistory += action;
    }

    /**
     * Sets the calculator history.
     *
     * @param history is the new calculator history
     */
    public void setHistory(String history) {
        mHistory = history;
    }

    public boolean isHistoryEmpty() {
        return mHistory.isEmpty();
    }

    public String getHistory() {
        return mHistory;
    }

    // ============================   Value Management =============================================
    public String getValue() {
        return mValue;
    }

    public void setValue(String value) {
        mValue = value;
    }

    public boolean isValueEmpty() {
        return mValue.isEmpty();
    }

    public void appendValue(String symbol) {
        mValue += symbol;
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
                mActivity.displayMessage(mActivity.getString(R.string.java_math_inspection_error) + e.getLocalizedMessage());
            }
        }

        return mMethods;
    }

    // ============================  Java Math Management  =========================================
    /**
     * Returns an instance of the given type.
     *
     * @param classname
     * @return on object corresponding to the passed  className
     */
    private Object castBigDecimalToType(String classname, BigDecimal value) {
        switch (classname) {
            case "Long":
            case "long":
                return Long.valueOf(value.longValue());

            case "Integer":
            case "int":
                return Integer.valueOf(value.intValue());

            case "Float":
            case "float":
                return Float.valueOf(value.floatValue());

            case "BigDecimal":
                return value;

            case "Double":
            case "double":
                return value.doubleValue();
        }

        return null;
    }

    /**
     * Returns an instance of BigDecimal from the given type.
     *
     * @param classname
     * @return on object corresponding to the passed  className
     */
    private BigDecimal castTypeToBigDecimal(String classname, Object value) {
        switch (classname) {
            case "Long":
                return BigDecimal.valueOf((Long) value);

            case "long":
                return BigDecimal.valueOf((long) value);

            case "Integer":
                return BigDecimal.valueOf((Integer) value);

            case "int":
                return BigDecimal.valueOf((int) value);

            case "Float":
                return BigDecimal.valueOf((Float) value);

            case "float":
                return BigDecimal.valueOf((float) value);

            case "BigDecimal":
                return (BigDecimal)value;

            case "Double":
                return BigDecimal.valueOf((Double) value);

            case "double":
                return BigDecimal.valueOf((double) value);
        }

        return null;
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

    /**
     * Invokes the given Java (math) Method, picking the required
     * arguments from the stack.
     *
     * @param method is the java math method to call
     * @param fromEngine is true when called by a script
     * @return true if the method was successfully called, false else.
     */
    private boolean invokeMathMethod(Method method, boolean fromEngine) {
        // invoke the selected function

        // first make sure we have the appropriate number of elements
        // on the stack
        Type[] params = method.getParameterTypes();
        int numParams = params.length;
        if (numParams > mStack.size())
            return false;

        // prepare the parameters.
        Object[] argObjects = new Object[numParams];
        while (--numParams >= 0)
            argObjects[numParams] = castBigDecimalToType(params[numParams].toString(), mStack.pop());

        // invoke the method
        boolean runOk = true;
        try {
            Object result = method.invoke(null, argObjects);
            if (result != null)
                mStack.push(castTypeToBigDecimal(method.getGenericReturnType().toString(), result));
        } catch (Exception e) {
            mActivity.displayMessage(mActivity.getString(R.string.function_call_err) + e.getMessage());
            runOk = false;
        } finally {
            // we've eaten the stack anyway...
            if (!fromEngine)
                mActivity.updateStackView(mStack);
        }

        return runOk;
    }

    /* ------------------------------------- SCRIPT ACTIONS  ---------------------------------------
        ONLY methods starting with 'do' can safely be called from the script engine thread, since
        they interact with the GUI through messages.
    */

    public void doPushValueOnStack(BigDecimal value) {
        mStack.push(value);
    }

    public BigDecimal doPopValueFromStack() {
        return mStack.isEmpty() ? BigDecimal.valueOf(0) : mStack.pop();
    }

    public BigDecimal doPeekValueFromStack() {
        return mStack.isEmpty() ? BigDecimal.valueOf(0) : mStack.peek();
    }

    public boolean doPushStackSize() {
        return pushStackSize(true);
    }

    /**
     * Calls the given java math function if existing
     *
     * @param function is the function to be called.
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
        mActivity.displayMessage(mActivity.getString(R.string.undefined_function) + function);

        return false;
    }

    /**
     * Public Basic Maths Functions
     */
    public boolean doAdd() {
        return add(true);
    }

    public boolean add(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 +
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(v1.add(v2));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doSub() {
        return sub(true);
    }

    public boolean sub(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 -
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(v1.subtract(v2));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doDiv() {
        return div(true);
    }

    public boolean div(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 /
        // uses Double because BigDecimal does not handle divide very well (rounding up to programmer)
        Double v2 = mStack.pop().doubleValue();
        Double v1 = mStack.pop().doubleValue();
        BigDecimal result = BigDecimal.valueOf(v1 / v2);

        mStack.push(result);
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doMul() {
        return mul(true);
    }

    public boolean mul(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 *
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(v1.multiply(v2));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doNeg() {
        return neg(true);
    }

    public boolean neg(boolean fromEngine) {
        // either neg the edited value if any
        if (!mValue.isEmpty()) {
            if (mValue.startsWith("-"))
                mValue = mValue.substring(1);
            else
                mValue = "-" + mValue;
            mActivity.setValueField(mValue);

            return true;
        }

        if (!mStack.isEmpty()) {
            // or the top of the stack if present
            mStack.push(mStack.pop().negate());
            if (!fromEngine)
                mActivity.updateStackView(mStack);

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
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(v1.remainder(v2));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doEqual() {
        return equal(true);
    }

    private boolean equal(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 =
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) == 0 ? 1 : 0));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doNotEqual() {
        return notEqual(true);
    }

    private boolean notEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 !=
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) == 0 ? 0 : 1));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doLessThan() {
        return lessThan(true);
    }

    private boolean lessThan(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 <
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) < 0 ? 1 : 0));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doLessThanOrEqual() {
        return lessThanOrEqual(true);
    }

    private boolean lessThanOrEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 <=
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) <= 0 ? 1 : 0));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doGreaterThan() {
        return greaterThan(true);
    }

    private boolean greaterThan(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 >
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) > 0 ? 1 : 0));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doGreaterThanOrEqual() {
        return greaterThanOrEqual(true);
    }

    private boolean greaterThanOrEqual(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        // v1 v2 >=
        BigDecimal v2 = mStack.pop();
        BigDecimal v1 = mStack.pop();
        mStack.push(BigDecimal.valueOf(v1.compareTo(v2) >= 0 ? 1 : 0));
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    /**
     * Public Basic Stack Functions
     */
    public boolean doRollN() {
        return rollN(true);
    }

    public boolean rollN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        int i = mStack.pop().intValue();
        if (i >= mStack.size())
            return false;

        BigDecimal val = mStack.pop();
        Stack<BigDecimal> st = new Stack<>();
        while (--i >= 0)
            st.push(mStack.pop());

        mStack.push(val);
        while (!st.isEmpty())
            mStack.push(st.pop());

        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doDup() {
        return dup(true);
    }

    public boolean dup(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        mStack.push(mStack.peek());
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doDupN() {
        return dupN(true);
    }

    public boolean dupN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        int i = mStack.pop().intValue();
        if (mStack.isEmpty())
            return false;

        BigDecimal val = mStack.peek();
        while (--i >= 0)
            mStack.push(val);

        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doDrop() {
        return drop(true);
    }

    public boolean drop(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        mStack.pop();
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doDropN() {
        return dropN(true);
    }

    public boolean dropN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        int i = mStack.pop().intValue();
        if (i > mStack.size())
            return false;

        while (--i >= 0)
            mStack.pop();

        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doSwap() {
        return swap(true);
    }

    public boolean swap(boolean fromEngine) {
        if (mStack.size() < 2)
            return false;

        BigDecimal v1 = mStack.pop();
        BigDecimal v2 = mStack.pop();
        mStack.push(v1);
        mStack.push(v2);

        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doSwapN() {
        return swapN(true);
    }

    public boolean swapN(boolean fromEngine) {
        if (mStack.isEmpty())
            return false;

        int i = mStack.pop().intValue();
        if (i > mStack.size())
            return false;

        BigDecimal v1 = mStack.elementAt(0);
        BigDecimal v2 = mStack.elementAt(i - 1);
        mStack.setElementAt(v1, i - 1);
        mStack.setElementAt(v2, 0);

        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }

    public boolean doClear() {
        return clear(true);
    }

    public boolean clear(boolean fromEngine) {
        mStack = new Stack<>();
        if (!fromEngine)
            mActivity.updateStackView(mStack);

        return true;
    }
}
