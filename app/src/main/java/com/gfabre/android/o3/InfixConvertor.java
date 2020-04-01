package com.gfabre.android.o3;

import java.util.Stack;
import java.util.StringTokenizer;

public class InfixConvertor {
    private static final String FUNCTION_CALL_MARKER = "fc@";
    private static final String MATH_CALL_MARKER = "mc@";

    String mInfix;           // sanitized infixed expression
    String mPostfix;         // post fixed
    String mRpnScript;       // rpn script (with \n after each operand/operator)


    public InfixConvertor(String infix) {
        mInfix = infix;
        Sanitize();
        mPostfix = "";
        convertToPostfixed();
        mRpnScript = "";
        convertToRpnScript();
    }

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

    private boolean isFunctionCall(String token) {
        return token.startsWith(FUNCTION_CALL_MARKER);
    }

    private boolean isMathCall(String token) {
        return token.startsWith(MATH_CALL_MARKER);
    }

    private int preced(String token) {

        switch (token) {
            case "(":
            case ",":
            case ")":
                return 1;

            case "*":
            case "/":
            case "%":
                return 2;

            case "-":
            case "+":
                return 3;

            default:
                // is that a function call?
                if (isFunctionCall(token) || isMathCall(token))
                    return 4;
        }

        return 0;
    }

    private boolean isOperator(String token) {
        return preced(token) > 0;
    }

    private void convertToPostfixed() {
        if (mInfix.isEmpty())
            return;

        String postfix = "";
        Stack<String> operator = new Stack<>();
        String popped, token;

        StringTokenizer tokenizer = new StringTokenizer(mInfix);

        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();

            if (!isOperator(token))
                postfix += " " + token;
            else if (token.equals(")"))
                while (!operator.isEmpty() && !(popped = operator.pop()).equals("(") && !popped.equals(","))
                    postfix += " " + popped;
            else {
                while (!operator.isEmpty() && !token.equals("(") && !token.equals(",") && preced(operator.peek()) >= preced(token))
                    postfix += " " + operator.pop();

                operator.push(token);
            }
        }

        // pop any remaining operator
        while (!operator.isEmpty()) {
            popped = operator.pop();
            if (!popped.equals("(") &&
                !popped.equals(")") &&
                !popped.equals(","))
            postfix += " " + popped;
        }

        mPostfix = postfix;
    }

    private void convertToRpnScript() {
        if (mPostfix.isEmpty())
            return;

        // script engine needs one line per operand/operator
        mRpnScript = mPostfix.replaceAll("(\\s)+", "\n") + "\n";

        // replace function_calls
        mRpnScript = mRpnScript.replaceAll("fc@", "funcall ");
        mRpnScript = mRpnScript.replaceAll("mc@", "math_call ");
    }

    public String getPostfix() {
        return mPostfix;
    }

    public String getRpnScript() {
        return mRpnScript;
    }
}
