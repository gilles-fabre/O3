package com.gfabre.android.o3;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import java_cup.runtime.Symbol;

import static com.gfabre.android.o3.DebugView.DebugState.exit;
import static com.gfabre.android.o3.DebugView.DebugState.none;
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
    private static Stack<Context> mContexts = new Stack<>();

    public static void stop() {
        mStopRequired = true;
    }

    private static class Context {
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

    private String               mScript;
    private int                  mInnerIf;
    private int                  mInnerWhile;
    private int                  mInnerFundef;
    private CalculatorActivity   mCalculator;
    private ScriptEngine         mParent = null; // lookup for arrays and vars
    private static volatile boolean mStopRequired;

    // variables are 'in-scope' only
    private HashMap<String, Double>     mVariables = new HashMap<>();
    private HashMap<String, ArrayList<Double>>  mArrays = new HashMap<>();

    // functions are globally defined
    private static HashMap<String, String>                     mFunctions = new HashMap<>();
    private static HashMap<String, ArrayList<ScriptOperation>> mCompiledFunctions = new HashMap<>(); // compiled counterparts

    // the compiled code (lambda) expression contract
    private interface ScriptOperation {
        boolean execute();
    }

    ScriptEngine(CalculatorActivity calculator, String script) {
        mScript = script;
        mInnerIf = 0;
        mInnerWhile = 0;
        mInnerFundef = 0;
        mCalculator = calculator;
        mStopRequired = false;
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
        mInnerIf = mParent.mInnerIf;
        mInnerWhile = mParent.mInnerWhile;
        mInnerFundef = mParent.mInnerFundef;
        mCalculator = calculator;
    }

    /**
     * Fills the passed array entries with null values up to the given index. Used to set a value
     * at any given position in the array, at any time.
     *
     * @param array is the target array
     * @param index is the position[ where to stop filling the array
     */
    private void extendArrayToIndex(ArrayList<Double> array, int index) {
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
            array = new ArrayList<>();
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
     * Compiles the passed script block into the global compiled functions
     * hashmap and saves the block in the global functions hashmap, both associated with
     * the function name key.
     *
     * @param function is the name of the function to compile & save
     * @param block is the script block for the function
     */
    private boolean compileAndSaveFunction(String function, String block) {
        ArrayList<ScriptOperation> functionLambdaCode = new ArrayList<ScriptOperation>();
        try {
            if (new ScriptEngine(this, mCalculator, block).compileScript(functionLambdaCode)) {
                mCompiledFunctions.put(function, functionLambdaCode);
                mFunctions.put(function, block);

                return true;
            }
        } catch (IOException e) {
            return false;
        }

        return false;
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
            runOk = new ScriptEngine(this, mCalculator, mFunctions.get(function)).interpretScript();
        } catch (IOException e) {
            // ignored on purpose
        }

        return runOk;
    }

    // compiled counterpart
    private boolean compileFunctionCall(String function, ArrayList<ScriptOperation> lambdaCode) {
        if (!mCompiledFunctions.containsKey(function)) {
            mCalculator.doDisplayMessage(mCalculator.getString(R.string.undefined_function) + function);
            return false;
        }

        // compile the function call in the context of a new engine
        /*
        lambdaCode.add(() -> {
            executeScript(mCompiledFunctions.get(function));
            return true;
        });
        */
        // inlined version
        lambdaCode.addAll(mCompiledFunctions.get(function));

        return true;
    }

    /**
     * Execute the script block associated with the function name key outside the context of
     * an existing scriptEngine.
     *
     * @param function is the name of the function to execute
     *
     * @return the block execution result or false if the function doesn't exist
     */
    public static boolean interpretFunction(CalculatorActivity calculator, String function) {
        boolean runOk = false;

        if (!mFunctions.containsKey(function)) {
            calculator.doDisplayMessage(calculator.getString(R.string.undefined_function) + function);
            return false;
        }

        // run the function in the context of a new engine
        try {
            runOk = new ScriptEngine(calculator, mFunctions.get(function)).interpretScript();
        } catch (IOException e) {
            // ignored on purpose
        }

        return runOk;
    }

    /**
     * @return the sorted list of ('defuned') functions in ascending order.
     */
    public static String[] getFunctions() {
        String []functions = mFunctions.keySet().toArray(new String[0]);;
        Arrays.sort(functions);
        return functions;
    }

    /**
     * Execute the passed script if block.
     *
     * @param block is the script block to execute
     *
     * @return the block execution result
     */
    private boolean interpretIfBlock(String block) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the if block in the context of a new engine
        try {
            if (mCalculator.doPeekValueFromStack() != 0.0)
                return new ScriptEngine(this, mCalculator, block).interpretScript();
        } catch (IOException e) {
            // ignored on purpose
        }

        return true;
    }

    // compiled counterpart
    private boolean compileIfBlock(String block, ArrayList<ScriptOperation> lambdaCode) {
        // compile the if block in the context of a new engine
        ArrayList<ScriptOperation> ifBlockLambdaCode = new ArrayList<>();
        try {
            new ScriptEngine(this, mCalculator, block).compileScript(ifBlockLambdaCode);

            lambdaCode.add(() ->
            {
                if (!mCalculator.hasValueOnStack())
                    return false;

                if (mCalculator.doPeekValueFromStack() != 0.0)
                    return executeScript(ifBlockLambdaCode);

                return true;
            });
        } catch (IOException e) {
            // ignore on purpose
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
    private boolean interpretIfElseBlock(String ifBlock, String elseBlock) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the if/else block in the context of a new engine
        try {
            if (mCalculator.doPeekValueFromStack() == 0.0)
                // run the the else block
                return new ScriptEngine(this, mCalculator, elseBlock).interpretScript();
            else {
                // run the if block
                return new ScriptEngine(this, mCalculator, ifBlock).interpretScript();
            }
        } catch (IOException e) {
            // ignored on purpose
        }

        return true;
    }

    // compiled counterpart
    private boolean compileIfElseBlock(String ifBlock, String elseBlock, ArrayList<ScriptOperation> lambdaCode) {
        // compile the if else blocks in the context of a new engine
        ArrayList<ScriptOperation> ifBlockLambdaCode = new ArrayList<>();
        ArrayList<ScriptOperation> elseBlockLambdaCode = new ArrayList<>();
        try {
            new ScriptEngine(this, mCalculator, ifBlock).compileScript(ifBlockLambdaCode);
            new ScriptEngine(this, mCalculator, elseBlock).compileScript(elseBlockLambdaCode);

            lambdaCode.add(() ->
            {
                if (!mCalculator.hasValueOnStack())
                    return false;

                if (mCalculator.doPeekValueFromStack() != 0.0)
                    return executeScript(ifBlockLambdaCode);
                else
                    return executeScript(elseBlockLambdaCode);
            });
        } catch (IOException e) {
            // ignore on purpose
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
    private boolean interpretWhileBlock(String block) {
        if (!mCalculator.hasValueOnStack())
            return false;

        // run the while block in the context of a new engine
        boolean runOk = true;
        try {
            while ((runOk = mCalculator.hasValueOnStack()) && mCalculator.doPeekValueFromStack() != 0.0) {
                if (!(runOk = new ScriptEngine(this, mCalculator, block).interpretScript()))
                    break;
            }
        } catch (IOException e) {
            // ignored on purpose
        }

        return runOk;
    }

    // compiled counterpart
    private boolean compileWhileBlock(String block, ArrayList<ScriptOperation> lambdaCode) {
        // compile the if block in the context of a new engine
        ArrayList<ScriptOperation> whileBlockLambdaCode = new ArrayList<>();
        try {
            new ScriptEngine(this, mCalculator, block).compileScript(whileBlockLambdaCode);

            lambdaCode.add(() ->
            {
                boolean runOk;
                while ((runOk = mCalculator.hasValueOnStack()) && mCalculator.doPeekValueFromStack() != 0.0) {
                    if (!(runOk = executeScript(whileBlockLambdaCode)))
                        break;
                }
                return runOk;
            });
        } catch (IOException e) {
            // ignore on purpose
        }

        return true;
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
        mCompiledFunctions.remove(function);
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
            char c = mScript.charAt(offset++);
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
            char c = mScript.charAt(o++);
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
     * @return the token string value
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
        StringBuilder variables = new StringBuilder();
        Iterator<Map.Entry<String, Double>> i = lookupVariables().entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Double> pair = i.next();
            variables.append(pair.getKey()).append(":").append(pair.getValue()).append("\n");
        }
        Iterator<Map.Entry<String, ArrayList<Double>>> j = lookupArrays().entrySet().iterator();
        while (j.hasNext()) {
            Map.Entry<String, ArrayList<Double>> pair = j.next();
            ArrayList array = pair.getValue();
            for (int k = 0; k < array.size(); k++)
                if (array.get(k) != null)
                    variables.append(pair.getKey()).append("[").append(k).append("] : ").append(array.get(k)).append("\n");
        }
        mCalculator.doUpdateDebugInfo(mContexts.peek().mLexer.yyline(), mScript, variables.toString(), mCalculator.getStackDebugInfo());
        mCalculator.doShowDebugView();
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
        boolean runOk = interpretScript();
        mContexts.pop();

        // close the debug dialog
        if (mCalculator.isDebugViewShown())
            mCalculator.doHideDebugView();

        return runOk;
    }

    /**
     * Executes the current script in the current execution/analysis context.
     *
     * @return true if the script was executed correctly, else return false
     *
     * @throws IOException
     */
    private static final AtomicInteger mCounter = new AtomicInteger(0);
    public static boolean isRunning() {
        return mCounter.get() > 0;
    }

    public boolean interpretScript() throws IOException {
        Symbol  symbol;
        boolean runOk = true, stop = false;

        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.interpreting_script));

        // a (new) script is running
        mCounter.incrementAndGet();

        Context newContext;
        Context curContext = mContexts.isEmpty() ? null : mContexts.peek();

        /*
        // #### debug contexts
        System.out.println("\n\n\n >>>> on interpretScript PREPARE entry: ");
        System.out.println("\n\t contexts : " + mContexts.size() + "\n");
        for (Context c : mContexts)
            System.out.println("\t\t context debug state : " + c.mDebugState + "\n");
        System.out.println("\n\n");
        // ####
        */

        // we enter here to either execute a complete script or a script sub-block (if/else/while/funcall)
        newContext = new Context(Context.State.RUNNING);
        newContext.mLexer = new ScriptLexer(new StringReader(mScript));
        if (curContext != null) {
            switch (newContext.mDebugState = curContext.mDebugState) {
                case none:
                    // not in a debug session
                case step_out:
                    // no debug here, down from step_over or up from step_out
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
                    stop = true;
                    break;
            }
        }
        mContexts.push(newContext);

        /*
        // #### debug contexts
        System.out.println("\n\n\n >>>> on interpretScript entry: ");
        System.out.println("\n\t contexts : " + mContexts.size() + "\n");
        for (Context c : mContexts)
            System.out.println("\t\t context debug state : " + c.mDebugState + "\n");
        System.out.println("\n\n");
        // ####
        */

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

                        case IF:
                            // skip inner if's end_if
                            ++mInnerIf;
                            break;

                        case END_IF:
                            if (mInnerIf == 0) {
                                mContexts.pop(); // closes and executes inner most if/else block
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                runOk = interpretIfBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            } else
                                --mInnerIf;
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

                        case IF:
                            // skip inner if's end_if
                            ++mInnerIf;
                            break;

                        case END_IF:
                            if (mInnerIf == 0) {
                                mContexts.pop(); // closes and executes inner most if/else block
                                String elseBlock = mScript.substring(curContext.mBlockStart, computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()));
                                curContext = mContexts.pop();
                                String ifBlock = mScript.substring(curContext.mBlockStart, curContext.mBlockEnd);
                                runOk = interpretIfElseBlock(ifBlock, elseBlock);
                            } else
                                --mInnerIf;
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

                        case WHILE:
                            // skip inner while's end_while
                            ++mInnerWhile;
                            break;

                        case END_WHILE:
                            if (mInnerWhile == 0) {
                                mContexts.pop(); // closes and executes inner most while block
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                runOk = interpretWhileBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            } else
                                --mInnerWhile;
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
                            // skip inner fundef's end_fundef
                            ++mInnerFundef;
                            break;

                        case END_FUNDEF:
                            if (mInnerFundef == 0) {
                                mContexts.pop();
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                saveFunction(curContext.mBlockId, mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            } else
                                --mInnerFundef;
                            break;
                    }
                    break;

                case RUNNING:
                    /*
                    // #### debug contexts
                    System.out.println("\n\n\n #### interpretScript hits : " + ScriptLexer.sym.values()[symbol.sym] + "\n\n\n");
                    // ####
                    */
                    switch (curContext.mDebugState) {
                        case none:
                            // not in a debug session
                        case step_out:
                            // no debug here, down from step_over or from step_out
                            break;

                        case step_over:
                            // will push a gc in step_out when getting down into
                            // inner func_call/while/if/else block
                        case step_in:
                            if (ScriptLexer.sym.values()[symbol.sym] != ScriptLexer.sym.EOF) {
                                // debug here
                                displayDebugInfo();
                                curContext.mDebugState = mCalculator.getDebugState();
                                switch (curContext.mDebugState) {
                                    case exit:
                                        runOk = false;
                                        stop = true;
                                        break;

                                    case none:
                                        mCalculator.doHideDebugView();
                                        break;
                                }
                            }
                            break;

                        case exit:
                            // step out until we're done with all the scripts stack
                            runOk = false;
                            stop = true;
                            break;
                    }

                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case DOUBLE_LITERAL:
                            mCalculator.doPushValueOnStack(curLexer.doubleValue);
                            break;

                        case PUSH_ARRAY_VALUE:
                            mCalculator.doPushValueOnStack(getArrayValue(curLexer.identifier, mCalculator.doPopValueFromStack().intValue()));
                            break;

                        case PUSH_IDENTIFIER:
                            mCalculator.doPushValueOnStack(getVariableValue(curLexer.identifier));
                            break;

                        case POP_ARRAY_VALUE:
                            setArrayValue(curLexer.identifier, mCalculator.doPopValueFromStack().intValue(), mCalculator.doPopValueFromStack());
                            break;

                        case POP_IDENTIFIER:
                            setVariableValue(curLexer.identifier, mCalculator.doPopValueFromStack());
                            break;

                        case UPDATE:
                            mCalculator.doUpdateStack();
                            break;

                        case DISPLAY_MESSAGE:
                            mCalculator.doDisplayMessage(curLexer.identifier);
                            break;

                        case PROMPT_MESSAGE:
                            mCalculator.doPromptForValue(curLexer.identifier);
                            break;

                        case INFIXED:
                            InfixConvertor ctor = new InfixConvertor(curLexer.expression);
                            runOk = new ScriptEngine(this, mCalculator, ctor.getRpnScript()).interpretScript();
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

                        case SWAPN:
                            runOk = mCalculator.doSwapN();
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
                            //  all lines until END_FUNDEF are saved into the functions hashmap
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
                            runOk = mCalculator.doJavaMathCall(curLexer.identifier);
                            break;

                        case RUN_SCRIPT:
                            runOk = mCalculator.doInterpretInnerScriptFile(curLexer.filename);
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

                        case PLOT3D:
                            runOk = mCalculator.doPlot3D();
                            break;

                        case LINE:
                            runOk = mCalculator.doLine();
                            break;

                        case LINE3D:
                            runOk = mCalculator.doLine3D();
                            break;

                        case ERASE:
                            runOk = mCalculator.doErase();
                            break;

                        case RANGE:
                            runOk = mCalculator.doSetRange();
                            break;

                        case POV3D:
                            runOk = mCalculator.doSetPov3D();
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

                        case EXIT:
                        case EOF:
                            // normal end of script (either implicit or explicit)
                            stop = true;
                            break;

                        case DEBUG_BREAK:
                            if (!mCalculator.isDebugViewShown()) {
                                // debug break is ignored if already in debug
                                displayDebugInfo();
                                curContext.mDebugState = mCalculator.getDebugState();
                                switch (curContext.mDebugState) {
                                    case exit:
                                        runOk = false;
                                        stop = true;
                                        break;

                                    case none:
                                        mCalculator.doHideDebugView();
                                        break;
                                }
                            }
                            break;
                    }
            }

            // we may need to stop at next iteration if required
            stop |= mStopRequired;
        }

        /*
        // #### debug contexts
        System.out.println("\n\n\n <<<< on interpretScript PREPARE exit: ");
        System.out.println("\n\t contexts : " + mContexts.size() + "\n");
        for (Context c : mContexts)
            System.out.println("\t\t context debug state : " + c.mDebugState + "\n");
        System.out.println("\n\n");
        // ####
        */

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

                case none:
                    // resume
                    parentContext.mDebugState = none;
                    break;
            }
        }

        /*
        // #### debug contexts
        System.out.println("\n\n\n <<<< on interpretScript exit: ");
        System.out.println("\n\t contexts : " + mContexts.size() + "\n");
        for (Context c : mContexts)
            System.out.println("\t\t context debug state : " + c.mDebugState + "\n");
        System.out.println("\n\n");
        // ####
        */
        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.empty_string));

        // if debugging, must close the debug dialog on error/exit/end of top most parent script
        if ((!runOk || mContexts.size() == 0) && mCalculator.isDebugViewShown())
            mCalculator.doHideDebugView();

        // update the stack when exiting the topmost script
        if (mContexts.size() == 0)
            mCalculator.doUpdateStack();

        // we're done with (a) script
        mCounter.decrementAndGet();

        return runOk;
    }

    // compiled counterpart
    public boolean executeScript() throws IOException {
        // a (new) script is running
        mCounter.incrementAndGet();

        boolean result;

        ArrayList<ScriptOperation> lambdaCode = new ArrayList<>();
        if ((result = compileScript(lambdaCode)))
            result = executeScript(lambdaCode);

        // we're done with (a) script
        mCounter.decrementAndGet();

        return result;
    }
    private boolean compileScript(ArrayList<ScriptOperation> lambdaCode) throws IOException {
        Symbol  symbol;
        boolean compileOk = true;
        boolean stop = false;

        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.compiling_script));

        Context newContext;
        Context curContext = mContexts.isEmpty() ? null : mContexts.peek();

        // we enter here to compile a complete script or a script sub-block (if/else/while/funcall)
        newContext = new Context(Context.State.RUNNING);
        newContext.mLexer = new ScriptLexer(new StringReader(mScript));
        mContexts.push(newContext);

        while (compileOk && !stop) {
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
                            compileOk = false; // unexpected EOF
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

                        case IF:
                            // skip inner if's end_if
                            ++mInnerIf;
                            break;

                        case END_IF:
                            if (mInnerIf == 0) {
                                mContexts.pop(); // closes and executes inner most if/else block
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                compileOk = compileIfBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd), lambdaCode);
                            } else
                                --mInnerIf;
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
                            compileOk = false; // unexpected EOF
                            break;

                        case IF:
                            // skip inner if's end_if
                            ++mInnerIf;
                            break;

                        case END_IF:
                            if (mInnerIf == 0) {
                                mContexts.pop(); // closes and executes inner most if/else block
                                String elseBlock = mScript.substring(curContext.mBlockStart, computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()));
                                curContext = mContexts.pop();
                                String ifBlock = mScript.substring(curContext.mBlockStart, curContext.mBlockEnd);
                                compileOk = compileIfElseBlock(ifBlock, elseBlock, lambdaCode);
                            } else
                                --mInnerIf;
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
                            compileOk = false; // unexpected EOF
                            break;

                        case WHILE:
                            // skip inner while's end_while
                            ++mInnerWhile;
                            break;

                        case END_WHILE:
                            if (mInnerWhile == 0) {
                                mContexts.pop(); // closes and executes inner most while block
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                compileOk = compileWhileBlock(mScript.substring(curContext.mBlockStart, curContext.mBlockEnd), lambdaCode);
                            } else
                                --mInnerWhile;
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
                            compileOk = false; // unexpected EOF
                            break;

                        case FUNDEF:
                            // skip inner fundef's end_fundef
                            ++mInnerFundef;
                            break;

                        case END_FUNDEF:
                            if (mInnerFundef == 0) {
                                mContexts.pop();
                                curContext.mBlockEnd = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn());
                                compileOk = compileAndSaveFunction(curContext.mBlockId, mScript.substring(curContext.mBlockStart, curContext.mBlockEnd));
                            } else
                                --mInnerFundef;
                            break;
                    }
                    break;

                case RUNNING:
                    switch (ScriptLexer.sym.values()[symbol.sym]) {
                        case DOUBLE_LITERAL: {
                            Double value = curLexer.doubleValue;
                            lambdaCode.add(() -> {
                                mCalculator.doPushValueOnStack(value);
                                return true;
                            });
                        }
                        break;

                        case PUSH_ARRAY_VALUE: {
                                String id = curLexer.identifier;
                                lambdaCode.add(() -> {
                                    mCalculator.doPushValueOnStack(getArrayValue(id, mCalculator.doPopValueFromStack().intValue()));
                                    return true;
                                });
                            }
                            break;

                        case PUSH_IDENTIFIER: {
                            String id = curLexer.identifier;
                            lambdaCode.add(() -> {
                                mCalculator.doPushValueOnStack(getVariableValue(id));
                                return true;
                            });
                        }
                        break;

                        case POP_ARRAY_VALUE: {
                                String id = curLexer.identifier;
                                lambdaCode.add(() -> {
                                    setArrayValue(id, mCalculator.doPopValueFromStack().intValue(), mCalculator.doPopValueFromStack());
                                    return true;
                                });
                            }
                            break;

                        case POP_IDENTIFIER: {
                            String id = curLexer.identifier;
                            lambdaCode.add(() -> {
                                setVariableValue(id, mCalculator.doPopValueFromStack());
                                return true;
                            });
                        }
                        break;

                        case UPDATE:
                            lambdaCode.add(() -> {
                                mCalculator.doUpdateStack();
                                return true;
                            });
                            break;

                        case DISPLAY_MESSAGE: {
                                String id = curLexer.identifier;
                                lambdaCode.add(() -> {
                                    mCalculator.doDisplayMessage(id);
                                    return true;
                                });
                            }
                            break;

                        case PROMPT_MESSAGE: {
                                String id = curLexer.identifier;
                                lambdaCode.add(() -> {
                                    mCalculator.doPromptForValue(id);
                                    return true;
                                });
                            }
                            break;

                        case INFIXED:
                            InfixConvertor ctor = new InfixConvertor(curLexer.expression);
                            compileOk = new ScriptEngine(this, mCalculator, ctor.getRpnScript()).compileScript(lambdaCode);
                            break;

                        case ADD:
                            lambdaCode.add(() -> mCalculator.doAdd());
                            break;

                        case SUB:
                            lambdaCode.add(() -> mCalculator.doSub());
                            break;

                        case DIV:
                            lambdaCode.add(() -> mCalculator.doDiv());
                            break;

                        case MOD:
                            lambdaCode.add(() -> mCalculator.doModulo());
                            break;

                        case EQ:
                            lambdaCode.add(() -> mCalculator.doEqual());
                            break;

                        case NEQ:
                            lambdaCode.add(() -> mCalculator.doNotEqual());
                            break;

                        case LT:
                            lambdaCode.add(() -> mCalculator.doLessThan());
                            break;

                        case LTE:
                            lambdaCode.add(() -> mCalculator.doLessThanOrEqual());
                            break;

                        case GT:
                            lambdaCode.add(() -> mCalculator.doGreaterThan());
                            break;

                        case GTE:
                            lambdaCode.add(() -> mCalculator.doGreaterThanOrEqual());
                            break;

                        case MUL:
                            lambdaCode.add(() -> mCalculator.doMul());
                            break;

                        case NEG:
                            lambdaCode.add(() -> mCalculator.doNeg());
                            break;

                        case DUP:
                            lambdaCode.add(() -> mCalculator.doDup());
                            break;

                        case DUPN:
                            lambdaCode.add(() -> mCalculator.doDupN());
                            break;

                        case DROP:
                            lambdaCode.add(() -> mCalculator.doDrop());
                            break;

                        case DROPN:
                            lambdaCode.add(() -> mCalculator.doDropN());
                            break;

                        case SWAP:
                            lambdaCode.add(() -> mCalculator.doSwap());
                            break;

                        case SWAPN:
                            lambdaCode.add(() -> mCalculator.doSwapN());
                            break;

                        case ROLLN:
                            lambdaCode.add(() -> mCalculator.doRollN());
                            break;

                        case STACK_SIZE:
                            lambdaCode.add(() -> mCalculator.doStackSize());
                            break;

                        case CLEAR:
                            lambdaCode.add(() -> mCalculator.doClear());
                            break;

                        case FUNDEF:
                            //  all lines until END_FUNDEF are saved into the functions hashmap
                            newContext = new Context(Context.State.FUNDEF_BLOCK_ANALYSIS);
                            newContext.mBlockId = curLexer.identifier;
                            newContext.mBlockStart = computeOffsetFromStartOfBlock(curLexer.yyline(), curLexer.yycolumn()) + curLexer.yylength();
                            newContext.mLexer = curLexer;
                            mContexts.push(newContext);
                            break;

                        case FUNDEL: {
                                String id = curLexer.identifier;
                                // the given function is removed from the functions hashmap
                                lambdaCode.add(() -> {
                                    mFunctions.remove(id);
                                    return true;
                                });
                            }
                            break;

                        case FUNCALL:
                            // interprets the given script, from the functions hashmap, using a new engine
                            compileOk = compileFunctionCall(curLexer.identifier, lambdaCode);
                            break;

                        case JAVA_MATH_CALL: {
                                String id = curLexer.identifier;
                                lambdaCode.add(() -> mCalculator.doJavaMathCall(id));
                            }
                            break;

                        case RUN_SCRIPT:{
                                String id = curLexer.filename;
                                lambdaCode.add(() -> mCalculator.doExecuteInnerScriptFile(id));
                            }
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
                            lambdaCode.add(() -> mCalculator.doPlot());
                            break;

                        case PLOT3D:
                            lambdaCode.add(() -> mCalculator.doPlot3D());
                            break;

                        case LINE:
                            lambdaCode.add(() -> mCalculator.doLine());
                            break;

                        case LINE3D:
                            lambdaCode.add(() -> mCalculator.doLine3D());
                            break;

                        case ERASE:
                            lambdaCode.add(() -> mCalculator.doErase());
                            break;

                        case RANGE:
                            lambdaCode.add(() -> mCalculator.doSetRange());
                            break;

                        case POV3D:
                            lambdaCode.add(() -> mCalculator.doSetPov3D());
                            break;

                        case COLOR:
                            lambdaCode.add(() -> mCalculator.doSetColor());
                            break;

                        case DOT_SIZE:
                            lambdaCode.add(() -> mCalculator.doSetDotSize());
                            break;

                        case ELSE:
                        case END_IF:
                        case END_WHILE:
                        case END_FUNDEF:
                        case SYNTAX_ERROR:
                            syntaxError(curContext);
                            compileOk = false; // get outa here!
                            // fall into

                        case EXIT:
                        case EOF:
                            // normal end of script (either implicit or explicit)
                            stop = true;
                            break;

                        case DEBUG_BREAK:
                            // nop
                            break;
                    }
            }

            // we may need to stop at next iteration if required
            stop |= mStopRequired;
        }

        // pops the current context
        mContexts.pop();
        Context parentContext = mContexts.isEmpty() ? null : mContexts.peek();
        if (parentContext != null) {
            switch (curContext.mDebugState) {
                case exit:
                    // force parent script to exit too
                    parentContext.mDebugState = exit;
                    compileOk = false;
                    break;

                case none:
                    // resume
                    parentContext.mDebugState = none;
                    break;
            }
        }

        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.empty_string));

        // update the stack when exiting the topmost script
        if (mContexts.size() == 0)
            lambdaCode.add(() -> {
                mCalculator.doUpdateStack();
                return true;
            });

        return compileOk;
    }

    private boolean executeScript(ArrayList<ScriptOperation> lambdaCode) {
        if (lambdaCode == null)
            return false;

        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.executing_script));

        for (ScriptOperation o : lambdaCode)
            if (!o.execute() || mStopRequired)
                break;

        mCalculator.doDisplayProgressMessage(mCalculator.getString(R.string.empty_string));

        return true;
    }
}
