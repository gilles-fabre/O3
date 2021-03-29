package com.gfabre.android.o3;

import java.util.Stack;
import java.util.StringTokenizer;

class InfixConvertor {
    private static final String FUNCTION_CALL_MARKER = "fc@";
    private static final String MATH_CALL_MARKER = "mc@";

    private String mInfix;           // sanitized infixed expression
    private String mPostfix;         // post fixed
    private String mRpnScript;       // rpn script (with \n after each operand/operator)


    /**
     * The constructor builds :
     *  1. a "sanitized" expression where all tokens are separated by spaces.
     *  2. a postfix expression using a stack and precedence computation.
     *  3. an rpn script the engine can interpret.
     *
     * @param infix is a well formed infixed expression, containing basic operators,
     *        variables and function or math function calls.
     */
    InfixConvertor(String infix) {
        mInfix = infix;
        Sanitize();
        mPostfix = "";
        convertToPostfixed();
        mRpnScript = "";
        convertToRpnScript();
    }

    /**
     * Produces a string where all tokens are separated by spaces.
     */
    private void Sanitize() {
        if (mInfix.isEmpty())
            return;

        String result = "";
        for (int i = 0; i < mInfix.length(); i++) {
            char c = mInfix.charAt(i);
            if (c == '(' ||
                c == ')' ||
                c == '+' ||
                c == '-' ||
                c == '/' ||
                c == '%' ||
                c == '*' ||
                c == '^' ||
                c == ',') {
                // insert spaces around operator where needed
                int rLen = result.length();
                if (rLen >= 1) {
                    if (result.charAt(rLen - 1) != ' ')
                        result += " ";
                }
                result += c + " ";
            } else if (c != ' ')
                result += c;
        }

        mInfix = result;
    }

    /**
     * Return the arithmetic precedence of the passed token.
     *
     * @param token is the operand or operator which precedence has to be returned
     * @return the arithmetic precedence of the token.
     */
    private int precedence(String token) {

        switch (token) {
            case "(":
            case ",": // just so it is considered as an operator
            case ")":
                return 1;

            case "*":
            case "/":
            case "%":
                return 2;

            case "-":
            case "+":
                return 3;
        }

        return 0;
    }

    /**
     * Test a token type.
     *
     * @param token is the item to be tested.
     * @return true if the token is an operator.
     */
    private boolean isOperator(String token) {
        return precedence(token) > 0;
    }

    /**
     * Move the functions to their postfix position in the infix expression.
     *
     * @param infix is an infixed expression.
     * @return an expression where functions are moved after the associated closing parenthesis.
     */
    private String moveFunctions(String infix) {
        String  result = "";
        int     infixLen = infix.length();
        int     eI = 0, // end index
                fI;     // next function index

        // find next fc@ or mc@ from eI
        if ((fI = infix.indexOf(FUNCTION_CALL_MARKER, eI)) == -1 &&
            (fI = infix.indexOf(MATH_CALL_MARKER, eI)) == -1)
            return infix;

        // iterate until we reach the end of mInfix
        while (eI < infixLen) {
            // copy everything into result up to function
            result += infix.substring(eI, fI);

            // seek closing parenthesis
            int oI = infix.indexOf('(', fI);
            if (oI != -1) {
                // keep function
                String function = infix.substring(fI, oI);

                // start counting opening/closing parenthesis
                int p = 1;
                int cI = oI;
                while (p != 0 && cI < infixLen - 1) {
                    if (infix.charAt(++cI) == '(')
                        ++p;
                    if (infix.charAt(cI) == ')')
                        --p;
                }
                if (p == 0) {
                    // we found the place where the function should go
                    eI = cI + 1; // continue after closing parenthesis
                    result += moveFunctions(infix.substring(oI, eI)); // process inner exp.
                    result += " " + function;
                }
                else
                    break; // missing closing parenthesis

                if ((fI = infix.indexOf(FUNCTION_CALL_MARKER, eI)) == -1 &&
                    (fI = infix.indexOf(MATH_CALL_MARKER, eI)) == -1) {
                    // copy trailing part
                    result += infix.substring(eI, infixLen);
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Converts mInfixed to a postfix expression and stores it into mPostFix.
     */
    private void convertToPostfixed() {
        if (mInfix.isEmpty())
            return;

        String postfix = "";
        Stack<String> operator = new Stack<>();
        String popped, token;

        // first need to move functions past their associated parenthesis.
        mInfix = moveFunctions(mInfix);

        StringTokenizer tokenizer = new StringTokenizer(mInfix);
        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();

            // if an operand, append it to the postfix result
            if (!isOperator(token))
                postfix += " " + token;
            // if reaching a new part of a function call (',') or the end of a sub expression (')')
            // process it until we reach a new stacked sub-expression or the end of the stack
            else if (token.equals(")") || token.equals(",")) {
                while (!operator.isEmpty() && !(popped = operator.pop()).equals("("))
                    postfix += " " + popped;
            } else {
                // we've encountered an arithmetic operator of a new expression
                // until we reach the end of the stack or a new expression, unstack and append
                // more prioritary operators to the postfix result
                while (!operator.isEmpty() && !token.equals("(") && precedence(operator.peek()) >= precedence(token))
                    postfix += " " + operator.pop();

                // stack operator or function
                operator.push(token); // note : ',' & ')' not pushed on purpose
            }
        }

        // pop any remaining operator
        while (!operator.isEmpty()) {
            popped = operator.pop();
            if (!popped.equals("("))
                postfix += " " + popped;
        }

        mPostfix = postfix;
    }

    /**
     * Converts the postfix mPostfix expression into an rpn script and stores it in mRpnScript.
     */
    private void convertToRpnScript() {
        if (mPostfix.isEmpty())
            return;

        // script engine needs one line per operand/operator
        mRpnScript = mPostfix.replaceAll("(\\s)+", "\n") + "\n";

        // replace function_calls
        mRpnScript = mRpnScript.replaceAll("fc@", "funcall ");
        mRpnScript = mRpnScript.replaceAll("mc@", "math_call ");
    }

    String getPostfix() {
        return mPostfix;
    }

    String getRpnScript() {
        return mRpnScript;
    }
}
