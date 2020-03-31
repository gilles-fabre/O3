package com.gfabre.android.o3;

import java.util.Stack;

public class InfixEvaluator {
    String              mInfix = "";
    String              mPostfix = "";
    CalculatorActivity  mCalculator;


    public InfixEvaluator(CalculatorActivity calculator) {
        mCalculator = calculator;
    }

    private void insertSpaces() {
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
            }
            else
                result += c;
        }

        mInfix = result;
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

    private void convert() {
        String postfix = "";
        Stack<Character> operator = new Stack<>();
        char popped;

        if (mInfix.isEmpty())
            return;

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

    public boolean evaluate(String infix) {
        mInfix = infix;
        if (mInfix.isEmpty())
            return false;

        insertSpaces();
        convert();

        if (mPostfix.isEmpty())
            return false;

        // script engine needs one line per operand/operator
        mPostfix = mPostfix.replaceAll("(\\s)+", "\n") + "\n";

        // display the postfix expression
        mCalculator.doDisplayMessage(mCalculator.getString(R.string.evaluating_label) + "\n" + mPostfix);

        // and run it
        mCalculator.runScript(mPostfix);

        return true;
    }
}
