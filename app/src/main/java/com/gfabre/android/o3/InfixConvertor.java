package com.gfabre.android.o3;

import java.util.Stack;

public class InfixConvertor {
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
                    c == '^') {
                // insert space before and after operator
                result += " " + c + " ";
            } else
                result += c;
        }

        mInfix = "";
        mInfix += result.charAt(0);
        for (int i = 1; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c != ' ')
                mInfix += c;
            else if (result.charAt(i - 1) != ' ')
                mInfix += c; // only first white space added, other skipped
        }
    }

    private int preced(char c) {

        switch (c) {
            case '(':
            case ')':
                return 1;

            case '*':
            case '/':
            case '%':
                return 2;

            case '-':
            case '+':
                return 3;
        }

        return 0;
    }

    private boolean isOperator(char i) {
        return preced(i) > 0;
    }

    private void convertToPostfixed() {
        if (mInfix.isEmpty())
            return;

        String postfix = "";
        Stack<Character> operator = new Stack<>();
        char popped;


        for (int i = 0; i < mInfix.length(); i++) {
            char c = mInfix.charAt(i);

            if (!isOperator(c))
                postfix += c;
            else if (c == ')')
                while ((popped = operator.pop()) != '(')
                    postfix += " " + popped;
            else {
                while (!operator.isEmpty() && c != '(' && preced(operator.peek()) >= preced(c))
                    postfix += " " + operator.pop();

                operator.push(c);
            }
        }

        // pop any remaining operator
        while (!operator.isEmpty())
            postfix += " " + operator.pop();

        mPostfix = postfix;
    }

    private void convertToRpnScript() {
        if (mPostfix.isEmpty())
            return;

        // script engine needs one line per operand/operator
        mRpnScript = mPostfix.replaceAll("(\\s)+", "\n") + "\n";
    }

    public String getPostfix() {
        return mPostfix;
    }

    public String getRpnScript() {
        return mRpnScript;
    }
}
