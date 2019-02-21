package com.gfabre.android.o3;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import java_cup.runtime.Symbol;

import static com.gfabre.android.o3.DebugView.DebugState.exit;
import static com.gfabre.android.o3.DebugView.DebugState.step_in;
import static com.gfabre.android.o3.DebugView.DebugState.step_over;

/**
 * A simple script interpreter, providing debugging capabilities. Uses jflex as a lexical analyzer backend.
 *
 *  @author gilles fabre
 *  @date December, 2018
 */
public class ScriptEngine {
    // analysis context, this is used to do the syntactical analysis, track the execution
    // and debugging context
    static private Stack<Context>   mContexts = new Stack<>();
    static private class Context {
        enum State {
            RUNNING,
            FUNDEF_BLOCK_ANALYSIS,
            WHILE_BLOCK_ANALYSIS,
            IF_BLOCK_ANALYSIS,
            ELSE_BLOCK_ANALYSIS
        }

        State                mState;
        String               mBlockId;
        int                  mBlockStart;    // fundef - while - if - else_if
        int                  mBlockEnd;
        ScriptLexer          mLexer;
        DebugView.DebugState mDebugState;

        Context(State state) {
            mState = state;
            mBlockStart = mBlockEnd = 0;
            mBlockId = "";
            mLexer = null;
            mDebugState = DebugView.DebugState.none;
        }
    }

    private String                      mScript;
    private CalculatorActivity          mCalculator;
    private ScriptEngine                mParent = null; // lookup for arrays and vars

    private static DebugView            mDebugView = null;

    // variables are 'in-scope' only
    private HashMap<String, Double>     mVariables = new HashMap<>();
    private HashMap<String, ArrayList<Double>>  mArrays = new HashMap<>();

    // functions are globally defined
    static private HashMap<String, String>      mFunctions = new HashMap<>();

    ScriptEngine(CalculatorActivity calculator, String script) {
        mScript = script;
        mCalculator = calculator;
        if (mDebugView == null)
            mDebugView = new DebugView(mCalculator);
    }

    /**
     * Private constructor used to recursively execute a script block or script function.
     *
     * @param parent is the calling ScriptEngine
     * @param calculator is the host calculator activity
     * @param script is the script to be executed
     */
    private ScriptEngine(ScriptEngine parent, CalculatorActivity calculator, String script) {
        mParent = parent;
        mScript = script;
        mCalculator = calculator;
    }

    /**
     * Fills the passed array entries with null values up to the given index. Used to set a value
     * at any given position in the array, at any time.
     *
     * @param array is the target array
     * @param index is the position[ where to stop filling the array
     */
    private void extendArrayToIndex(ArrayList<Double> array,int index) {
        for (int i = array.size(); i <= index; i++)
            array.add(i, null);
    }

    /**
     * If existing, looks up and return the given array starting from the current engine, and
     * up the engines hierarchy.
     *
     * @param id is the name of the array we're looking for
     * @return the found array or null if it doesn't exist
     */
    private ArrayList<Double> lookupForArray(String id) {
        if (mArrays.containsKey(id))
            return mArrays.get(id);
        if (mParent != null)
            return mParent.lookupForArray(id);
        return null;
    }

    /**
     * Sets the (looked up) given array's entry at position index with the
     * passed value.
     *
     * @param id is the name of the target array
     * @param index is the position of the entry to set in the array
     * @param value is the value to set
     */
    private void setArrayValue(String id, int index, Double value) {
        // need to create the array?
        ArrayList<Double> array = lookupForArray(id);
        if(array == null) {
            array = new ArrayList<Double>();
            mArrays.put(id, array);
        }

        // set the array value
        extendArrayToIndex(array, index);
        array.set(index, value);
    }

    /**
     * Gets the (looked up) given array's value at position index.
     *
     * @param id is the name of the target array
     * @param index is the position of the entry to set in the array
     *
     * @return the value or NaN if no array/value found
     */
    private Double getArrayValue(String id, int index) {
        // return NaN if the array doesn't exist..
        // need to create the array?
        ArrayList<Double> array = lookupForArray(id);
        if(array == null)
            return Double.NaN;

        // or if it's got no index entry
        extendArrayToIndex(array, index);
        Double value = array.get(index);

        return value == null ? Double.NaN : value;
    }

    /**
     * If existing, looks up and return the given variable starting from the current engine, and
     * up the engines hierarchy.
     *
     * @param id is the name of the variable we're looking for
     * @return the found variable or null if it doesn't exist
     */
    private Double lookupForVariable(String id) {
        if (mVariables.containsKey(id))
            return mVariables.get(id);
        if (mParent != null)
            return mParent.lookupForVariable(id);
        return null;
    }

    /**
     * Sets the (looked up) given variable to the passed value.
     *
     * @param id is the name of the target variable
     * @param value is the value to set
     */
    private void setVariableValue(String id, Double value) {
        if (mVariables.containsKey(id))
            mVariables.put(id, value);
        else if (mParent != null && mParent.lookupForVariable(id) != null)
            mParent.setVariableValue(id, value);
        else
            mVariables.put(id, value);
    }

    /**
     * Gets the (looked up) given variable's value.
     *
     * @param id is the name of the target variable
     *
     * @return the value or NaN if no variable found
     */
    private Double getVariableValue(String id) {
        Double variable = lookupForVariable(id);
        return variable == null ? Double.NaN : variable;
    }

    /**
     * Saves the passed script block into the global functions
     * hashmap, associated with the function name key.
     *
     * @param function is the name of the function to save
     * @param block is the script block for the function
     */
    private void saveFunction(String function, String block) {
        mFunctions.put(function, block);
    }

    /**
     * Execute the subscript block associated with the function name key.
     *
     * @param function is the name of the function to execute
     *
     * @return the block execution result or false if the function doesn't exist
     */
    private boolean callFunction(String function) {
        boolean runOk = false;

        if (!mFunctions.containsKey(function)) {
            mCalculator.doDisplayMessage(mCalculator.getString(R.string.undefined_function) + function);
            return runOk;
        }

        // run the function in the context of a new engine
        try {
            runOk = new ScriptEngine(this, mCalculator, mFunctions.get(function)).runScript();
        } catch (IOException e) {
        }

        return runOk;
    }

    /**
     * Execute the script block associated with the function name key outside the context of
     * an existing scriptEngine.
     *
     * @param function is the name of the function to execute
     *
     * @return the block execution result or false if the function doesn't exist
     */
    public static boolean runFunction(CalculatorActivity calculator, String function) {
        boolean runOk = false;

        if (!mFunctions.containsKey(function)) {
            calculator.doDisplayMessage(calculator.getString(R.string.undefined_function) + function);
            return runOk;
        }

        // run the function in the context of a new engine
        try {
            runOk = new ScriptEngine(calculator, mFunctions.get(function)).runScript();
        } catch (IOException e) {
        }

        return runOk;
    }

    /**
     * @return the list of ('defuned') functions.
     */
    static String[] getFunctions() {
        String []keys = mFunctions.keySet().toArray(new String[mFunctions.size()]);
        return keys;
    }

    /**
     * Execute the passed script if block.
     *
     * @param block is the script block to execute
     *
     * @return the block execution result
     */
    private boolean runIfBlock(String block) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the function in the context of a new engine
        try {
            if (mCalculator.peekValueFromStack() != 0.0)
                return new ScriptEngine(this, mCalculator, block).runScript();
        } catch (IOException e) {
        }

        return true;
    }

    /**
     * Execute the passed script if/else block.
     *
     * @param ifBlock is the script if block to execute if stack.peek() != 0
     * @param elseBlock is the script else block to execute else
     *
     * @return the block execution result
     */
    private boolean runIfElseBlock(String ifBlock, String elseBlock) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the if/else block in the context of a new engine
        try {
            if (mCalculator.peekValueFromStack() == 0.0)
                // run the the else block
                return new ScriptEngine(this, mCalculator, elseBlock).runScript();
            else {
                // run the if block
                return new ScriptEngine(this, mCalculator, ifBlock).runScript();
            }
        } catch (IOException e) {
        }

        return true;
    }

    /**
     * Iterates on the passed script while block.
     *
     * @param block is the script while block to execute while stack.peek() != 0
     *
     * @return the block execution result
     */
    private boolean runWhileBlock(String block) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the while block in the context of a new engine
        boolean runOk = true;
        try {
            while (mCalculator.hasValueOnStack() && mCalculator.peekValueFromStack() != 0.0) {
                if (!(runOk = new ScriptEngine(this, mCalculator, block).runScript()))
                    break;
            }
        } catch (IOException e) {
        }

        return runOk;
    }

    /**
     * Removes the script block associated with the given function key from the global
     * functions hashmap
     *
     * @param function is the name of the function to delete
     */
    private void deleteFunction(String function) {
        if (!mFunctions.containsKey(function))
            return;

        mFunctions.remove(function);
    }

    /**
     * I've found no efficient way to get the offset of the last recognized token
     * from the start of the script. So this returns the byte offset of the character
     * at the given line/column :/
     *
     * @param line is the last recognized token line
     * @param column is the last recognized token start column in line
     *
     * @return the offset of the last recognized token from the start of the script
     */
    private int computeOffsetFromStartOfBlock(int line, int column) {
        int offset = 0;
        int l = 0;
        while (l < line && offset < mScript.length()) {
            Character c = mScript.charAt(offset++);
            if (c == '\n' || c == '\r')
                ++l;
        }
        return offset + column;
    }

    /**
     * Computes the number of the line where the character at the given byte offset is located.
     *
     * @param offset is the character byte offset from the start of the script
     *
     * @return the line corresponding to the given offset
     */
    private int computeLineFromStartOfScript(int offset) {
        if (mParent != null)
            return mParent.computeLineFromStartOfScript(offset);

        int line = 1;
        int o = 0;
        while (o < offset && o < mScript.length()) {
            Character c = mScript.charAt(o++);
            if (c == '\n' || c == '\r')
                ++line;
        }

        return line;
    }

    /**
     * Gets the syntactical token at the given offset.
     *
     * @param offset is the offset of the requested token in the script.
     *
     * @return the taken string value
     */
    private String getTokenAtOffset(int offset) {
        if (mParent != null)
            return mParent.getTokenAtOffset(offset);

        String token = mScript.substring(offset).trim();
        token = token.replace('\n', ' ');
        token = token.replace('\t', ' ');
        token = token.replace('\r', ' ');
        int end = token.indexOf(" ");
        if (end == -1)
            end = token.length() - 1;
        return token.substring(0, end);
    }

    /**
     * Pops up a dialog reading debugging information regarding the current syntax error.
     *
     * @param curContext described the current analysis context where the error was encountered
     */
    private void syntaxError(Context curContext) {
        // script..
        // get offset from start of script
        int offset = computeOffsetFromStartOfBlock(curContext.mLexer.yyline(), curContext.mLexer.yycolumn());
        for (Context c : mContexts)
            offset += c.mBlockStart;
        // get line
        int line = computeLineFromStartOfScript(offset);
        String error = mCalculator.getString(R.string.syntax_error) +
                line + "/" +
                curContext.mLexer.yycolumn() + mCalculator.getString(R.string.unexpected_token) +
                getTokenAtOffset(offset);
        mCalculator.doDisplayMessage(error);
    }

    /**
     * @return a copy of the concatenated hierarchy's variable hashmaps
     */
    private HashMap<String, Double> lookupVariables() {
        HashMap<String, Double> map = new HashMap<>(mVariables);
        if (mParent != null)
            map.putAll(mParent.lookupVariables());
        return map;
    }

    /**
     * @return a copy of the concatenated hierarchy's array hashmaps
     */
    private HashMap<String, ArrayList<Double>> lookupArrays() {
        HashMap<String, ArrayList<Double>> map = new HashMap<>(mArrays);
        if (mParent != null)
            map.putAll(mParent.lookupArrays());
        return map;
    }

    /**
     * Populates and show (or refresh) the debug dialog
     */
    private void displayDebugInfo() {
        String variables = "";
        Iterator<Map.Entry<String, Double>> i = lookupVariables().entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Double> pair = i.next();
            variables += pair.getKey() + ":" + pair.getValue() + "\n";
        }
        Iterator<Map.Entry<String, ArrayList<Double>>> j = lookupArrays().entrySet().iterator();
        while (j.hasNext()) {
            Map.Entry<String, ArrayList<Double>> pair = j.next();
            ArrayList array = pair.getValue();
            for (int k = 0; k < array.size(); k++)
                if (array.get(k) != null)
                    variables += pair.getKey() + "[" + k + "] : " + array.get(k) + "\n";
        }
        mDebugView.updateDebugInfo(mContexts.peek().mLexer.yyline(), mScript, variables, mCalculator.getStackDebugInfo());
        mDebugView.show();
    }

    /**
     * Debugs the given script.
     *
     * @return true if the script was executed correctly, else return false
     *
     * @throws IOException
     */
    public boolean debugScript() throws IOException {
        // we enter here to debug a complete script
        Context newContext = new Context(Context.State.RUNNING);
        newContext.mDebugState = DebugView.DebugState.step_in;

        mContexts.push(newContext);
        boolean runOk = runScript();
        mContexts.pop();

        return runOk;
    }

    /**
     * Executes the current script in the current execution/analysis context.
     *
     * @return true if the script was executed correctly, else return false
     *
     * @throws IOException
     */
    public boolean runScript() throws IOException {
        Context newContext;
        Context curContext = mContexts.isEmpty() ? null : mContexts.peek();

        // we enter here to either execute a complete script or a script sub-block (if/else/while)
        newContext = new Context(Context.State.RUNNING);
        newContext.mLexer = new ScriptLexer(new StringReader(mScript));
        if (curContext != null) {
            switch (newContext.mDebugState = curContext.mDebugState) {
                case none:
                    // not in a debug session
                case step_out:
                    // no debug here, down from step_over or from step_out
                case step_in:
                    // no change down the call stack
                    break;

                case step_over:
                    // push a gc in step_out when getting down into
                    // inner block
                    newContext.mDebugState = DebugView.DebugState.step_out;
                    break;

                case exit:
                    // do not get any deeper, since last debug dialog
                    // was dismissed with an exit request
                    return false;
            }
        }
        mContexts.push(newContext);

        Symbol symbol;
        boolean runOk = true, stop = false;
        while (runOk && !stop) {
            curContext = mContexts.peek();
            ScriptLexer curLexer = curContext.mLexer;
            symbol = curLexer.next_token();
            switch (curContext.mState) {
                case IF_BLOCK_ANALYSIS:
                    // anything except ELSE_BLOCK_ANALYSIS and END will be saved
                    // anything except END will be saved
                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            // fall into

                        case EOF:
                            mContexts.pop();
                            runOk = false; // unexpected EOF
                            break;

                        case ELSE:
                            // the if block stops here :)
                            curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());

                            // must stack this code, and execute upon end if calc's stack top value is 0
                            newContext = new Context(Context.State.ELSE_BLOCK_ANALYSIS);
                            newContext.mBlockId = curLexer.yytext();
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case END_IF:
                            mContexts.pop();
                            curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                            runOk = runIfBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            break;
                    }
                    break;

                case ELSE_BLOCK_ANALYSIS:
                    // anything except END will be saved
                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            // fall into

                        case EOF:
                            mContexts.pop(); // if and else_if contexts were stacked
                            mContexts.pop();
                            runOk = false; // unexpected EOF
                            break;

                        case END_IF:
                            mContexts.pop(); // if and else_if contexts were stacked
                            String elseBlock = mScript.substring(curContext.mBlockStart, computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()));
                            curContext = mContexts.pop();
                            String ifBlock = mScript.substring(curContext.mBlockStart, curContext.mBlockEnd);
                            runOk = runIfElseBlock(ifBlock, elseBlock);
                            break;
                    }
                    break;

                case WHILE_BLOCK_ANALYSIS:
                    // anything except END will be saved
                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            // fall into

                        case EOF:
                            mContexts.pop();
                            runOk = false; // unexpected EOF
                            break;

                        case END_WHILE:
                            mContexts.pop();
                            curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                            runOk = runWhileBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            break;
                    }
                    break;

                case FUNDEF_BLOCK_ANALYSIS:
                    // anything except FUNDEF and END_FUNDEF will be saved
                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            // fall into

                        case EOF:
                            mContexts.pop();
                            runOk = false; // unexpected EOF
                            break;

                        case FUNDEF:
                            //  all lines until END_FUNDEF are save into the functions hashmap
                            newContext = new Context(Context.State.FUNDEF_BLOCK_ANALYSIS);
                            newContext.mBlockId = mContexts.peek().mLexer.identifier;
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case END_FUNDEF:
                            mContexts.pop();
                            curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                            saveFunction(curContext.mBlockId, mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            break;
                    }
                    break;

                case RUNNING:
                    switch (curContext.mDebugState) {
                        case none:
                            // not in a debug session
                        case step_out:
                            // no debug here, down from step_over or from step_out
                            break;

                        case step_over:
                            // push a gc in step_out when getting down into
                            // inner func_call/while/if/else block
                        case step_in:
                            if (ScriptLexer.sym.values()[symbol.sym] != ScriptLexer.sym.EOF) {
                                // debug here
                                displayDebugInfo();
                                curContext.mDebugState = mDebugView.getDebugState();
                                if (curContext.mDebugState == exit)
                                    stop = true;
                            }
                            break;

                        case exit:
                            // step out until we're done with alll the scripts stack
                            runOk = false;
                            stop = true;
                            break;
                    }

                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case DOUBLE_LITERAL:
                            mCalculator.pushValueOnStack(curLexer.doubleValue);
                            break;

                        case PUSH_ARRAY_VALUE:
                            mCalculator.pushValueOnStack(getArrayValue(curLexer.identifier, mCalculator.popValueFromStack().intValue()));
                            break;

                        case PUSH_IDENTIFIER:
                            mCalculator.pushValueOnStack(getVariableValue(curLexer.identifier));
                            break;

                        case POP_ARRAY_VALUE:
                            setArrayValue(curLexer.identifier, mCalculator.popValueFromStack().intValue(), mCalculator.popValueFromStack());
                            break;

                        case POP_IDENTIFIER:
                            setVariableValue(curLexer.identifier, mCalculator.popValueFromStack());
                            break;

                        case UPDATE:
                            mCalculator.doUpdate();
                            break;

                        case DISPLAY_MESSAGE:
                            mCalculator.doDisplayMessage(curLexer.identifier);
                            break;

                        case PROMPT_MESSAGE:
                            mCalculator.doPromptMessage(curLexer.identifier);
                            break;

                        case ADD:
                            runOk = mCalculator.doAdd();
                            break;

                        case SUB:
                            runOk = mCalculator.doSub();
                            break;

                        case DIV:
                            runOk = mCalculator.doDiv();
                            break;

                        case MOD:
                            runOk = mCalculator.doModulo();
                            break;

                        case EQ:
                            runOk = mCalculator.doEqual();
                            break;

                        case NEQ:
                            runOk = mCalculator.doNotEqual();
                            break;

                        case LT:
                            runOk = mCalculator.doLessThan();
                            break;

                        case LTE:
                            runOk = mCalculator.doLessThanOrEqual();
                            break;

                        case GT:
                            runOk = mCalculator.doGreaterThan();
                            break;

                        case GTE:
                            runOk = mCalculator.doGreaterThanOrEqual();
                            break;

                        case MUL:
                            runOk = mCalculator.doMul();
                            break;

                        case NEG:
                            runOk = mCalculator.doNeg();
                            break;

                        case DUP:
                            runOk = mCalculator.doDup();
                            break;

                        case DUPN:
                            runOk = mCalculator.doDupN();
                            break;

                        case DROP:
                            runOk = mCalculator.doDrop();
                            break;

                        case DROPN:
                            runOk = mCalculator.doDropN();
                            break;

                        case SWAP:
                            runOk = mCalculator.doSwap();
                            break;

                        case ROLLN:
                            runOk = mCalculator.doRollN();
                            break;

                        case STACK_SIZE:
                            mCalculator.doStackSize();
                            break;

                        case CLEAR:
                            mCalculator.doClear();
                            break;

                        case FUNDEF:
                            //  all lines until END_FUNDEF are save into the functions hashmap
                            newContext = new Context(Context.State.FUNDEF_BLOCK_ANALYSIS);
                            newContext.mBlockId = curLexer.identifier;
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case FUNDEL:
                            // the given function is removed from the functions hashmap
                            deleteFunction(curLexer.identifier);
                            break;

                        case FUNCALL:
                            // interprets the given script, from the functions hashmap, using a new engine
                            runOk = callFunction(curLexer.identifier);
                            break;

                        case JAVA_MATH_CALL:
                            mCalculator.doJavaMathCall(curLexer.identifier);
                            break;

                        case RUN_SCRIPT:
                            mCalculator.doRunScriptFile(curLexer.filename);
                            break;

                        case WHILE:
                            // must stack this code, and execute upon end if calc's stack top value ain't 0
                            newContext = new Context(Context.State.WHILE_BLOCK_ANALYSIS);
                            newContext.mBlockId = curLexer.yytext();
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case IF:
                            // must stack this code, and execute upon end if calc's stack top value ain't 0
                            newContext = new Context(Context.State.IF_BLOCK_ANALYSIS);
                            newContext.mBlockId = curLexer.yytext();
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case PLOT:
                            runOk = mCalculator.doPlot();
                            break;

                        case ERASE:
                            runOk = mCalculator.doErase();
                            break;

                        case RANGE:
                            runOk = mCalculator.doSetRange();
                            break;

                        case COLOR:
                            runOk = mCalculator.doSetColor();
                            break;

                        case DOT_SIZE:
                            runOk = mCalculator.doSetDotSize();
                            break;

                        case ELSE:
                        case END_IF:
                        case END_WHILE:
                        case END_FUNDEF:
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            runOk = false; // get outa here!
                            // fall into

                        case EOF:
                            stop = true; // normal end of script
                            // if debugging, must close the debug dialog
                            if (mDebugView.isShown())
                                mDebugView.hide();
                            break;

                        case DEBUG_BREAK:
                            if (!mDebugView.isShown()) {
                                // debug break is ignored if already in debug
                                displayDebugInfo();
                                curContext.mDebugState = mDebugView.getDebugState();
                                if (curContext.mDebugState == exit) {
                                    runOk = false;
                                    stop = true;
                                }
                            }
                            break;

                        case EXIT:
                            stop = true;
                            // if debugging, must close the debug dialog
                            if (mDebugView.isShown())
                                mDebugView.hide();
                            break;
                    }
            }
        }

        // pops the current context
        mContexts.pop();
        Context parentContext = mContexts.isEmpty() ? null : mContexts.peek();
        if (parentContext != null) {
            switch (curContext.mDebugState) {
                case exit:
                    // force parent script to exit too
                    parentContext.mDebugState = exit;
                    runOk = false;
                    break;

                case step_out:
                    // force parent script to step_over upon next iteration if was
                    // in step_in
                    if (parentContext.mDebugState == step_in)
                        parentContext.mDebugState = step_over;
                    break;
            }
        }

        // if debugging, must close the debug dialog
        if (!runOk && mDebugView.isShown())
            mDebugView.hide();

        return runOk;
    }
}
