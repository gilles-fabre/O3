package com.gfabre.android.o3;

/* JFlex Script Lexical Analyzer definition file : yylex() will return script tokens  */
import java_cup.runtime.*;
import java.lang.String;
import java.lang.Double;

/**
 * This class defines the Script Lexical Analyzer
 */
%%

%public
%class ScriptLexer
%unicode
%cup
%line
%column

%{
  enum sym {
                WHILE,
                END_WHILE,
                IF,
                ELSE,
                END_IF,
                FUNDEF,
                FUNDEL,
                END_FUNDEF,
                DEBUG_BREAK,
                EXIT,
                FUNCALL,
                JAVA_MATH_CALL,
                NEG,
                DUP,
                DUPN,
                DROP,
                DROPN,
                SWAP,
                SWAPN,
                ROLLN,
                CLEAR,
                UPDATE,
                STACK_SIZE,
                RANGE,
                COLOR,
                ERASE,
                PLOT,
                DOT_SIZE,
                PUSH_IDENTIFIER,
                POP_IDENTIFIER,
                PUSH_ARRAY_VALUE,
                POP_ARRAY_VALUE,
                DOUBLE_LITERAL,
                DISPLAY_MESSAGE,
                PROMPT_MESSAGE,
                SUB,
                ADD,
                DIV,
                MUL,
                MOD,
                NEQ,
                EQ,
                LT,
                LTE,
                GT,
                GTE,
                EOF,
                SYNTAX_ERROR
  };

  final static int FUNDEF_LEN = new String("fundef").length();
  final static int FUNDEL_LEN = new String("fundel").length();
  final static int FUNCALL_LEN = new String("funcall").length();
  final static int MATH_CALL_LEN = new String("math_call").length();
  final static int PUSH_VAR_LEN = new String("!").length();
  final static int POP_VAR_LEN = new String("?").length();
  final static int PUSH_ARRAY_LEN = new String("![]").length();
  final static int POP_ARRAY_LEN = new String("?[]").length();
  final static int DISPLAY_MESSAGE_LEN = new String("!\"").length();
  final static int PROMPT_MESSAGE_LEN = new String("?\"").length();

  String identifier = null;
  Double doubleValue = null;

  public int yyline() {
      return yyline;
  }

  public int yycolumn() {
      return yycolumn;
  }

  public int yychar() {
      return yychar;
  }

  private Symbol symbol(sym type) {
    return new Symbol(type.ordinal(), yyline, yycolumn);
  }
  private Symbol symbol(sym type, Object value) {
    return new Symbol(type.ordinal(), yyline, yycolumn, value);
  }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]

/* comments */

// Comment can be the last line of the file, without line terminator.
EndOfLineComment     = "//" {InputCharacter}* {LineTerminator}?
DocumentationComment = "/**" {CommentContent} "*"+ "/"
CommentContent       = ( [^*] | \*+ [^/*] )*

TraditionalComment   = "/*" [^*] ~"*/" | "/*" "*"+ "/"
Comment = {TraditionalComment} | {EndOfLineComment} | {DocumentationComment}

/* doubles */
DoubleLiteral = (-)?({FLit1}|{FLit2}|{FLit3}) {Exponent}?

FLit1    = [0-9]+ \. [0-9]*
FLit2    = \. [0-9]+
FLit3    = [0-9]+
Exponent = [eE] [+-]? [0-9]+

/* variables to and from the stack */
Identifier = [:jletter:][:jletterdigit:]*
ArrayValue = "[]"{Identifier}
PopIdentifier = "?"{Identifier}
PushIdentifier = "!"{Identifier}
PopArrayValue = "?"{ArrayValue}
PushArrayValue = "!"{ArrayValue}

/* display a message */
DisplayMessage = "!"\"{InputCharacter}+

/* prompt a message */
PromptMessage = "?"\"{InputCharacter}+

/* call */
FunDef = "fundef"(" "|\t)+{Identifier}
FunDel = "fundel"(" "|\t)+{Identifier}
FunCall = "funcall"(" "|\t)+{Identifier}
JavaMathCall = "math_call"(" "|\t)+{Identifier}

%%

/* keywords */
"end_fundef"                   { return symbol(sym.END_FUNDEF); }
"neg"                          { return symbol(sym.NEG); }
"while"                        { return symbol(sym.WHILE); }
"end_while"                    { return symbol(sym.END_WHILE); }
"if"                           { return symbol(sym.IF); }
"else"                         { return symbol(sym.ELSE); }
"end_if"                       { return symbol(sym.END_IF); }
"debug_break"                  { return symbol(sym.DEBUG_BREAK); }
"exit"                         { return symbol(sym.EXIT); }

// stack stuff
"dup"                          { return symbol(sym.DUP); }
"dupn"                         { return symbol(sym.DUPN); }
"drop"                         { return symbol(sym.DROP); }
"dropn"                        { return symbol(sym.DROPN); }
"swap"                         { return symbol(sym.SWAP); }
"swapn"                        { return symbol(sym.SWAPN); }
"rolln"                        { return symbol(sym.ROLLN); }
"clear"                        { return symbol(sym.CLEAR); }
"update"                       { return symbol(sym.UPDATE); }
"stack_size"                   { return symbol(sym.STACK_SIZE); }

// graphics
"range"                        { return symbol(sym.RANGE); }
"color"                        { return symbol(sym.COLOR); }
"erase"                        { return symbol(sym.ERASE); }
"plot"                         { return symbol(sym.PLOT); }
"dot_size"                     { return symbol(sym.DOT_SIZE); }

/* literals */
{FunDef}                       { identifier = yytext().substring(FUNDEF_LEN).trim(); return symbol(sym.FUNDEF); }
{FunDel}                       { identifier = yytext().substring(FUNDEL_LEN).trim(); return symbol(sym.FUNDEL); }
{FunCall}                      { identifier = yytext().substring(FUNCALL_LEN).trim(); return symbol(sym.FUNCALL); }
{JavaMathCall}                 { identifier = yytext().substring(MATH_CALL_LEN).trim(); return symbol(sym.JAVA_MATH_CALL);  }

{DoubleLiteral}                { try {doubleValue = new Double(yytext());} catch (Exception e) {doubleValue = Double.NaN;} return symbol(sym.DOUBLE_LITERAL); }
{PushIdentifier}               { identifier = yytext().substring(PUSH_VAR_LEN).trim(); return symbol(sym.PUSH_IDENTIFIER); }
{PopIdentifier}                { identifier = yytext().substring(POP_VAR_LEN).trim(); return symbol(sym.POP_IDENTIFIER); }
{PushArrayValue}               { identifier = yytext().substring(PUSH_ARRAY_LEN).trim(); return symbol(sym.PUSH_ARRAY_VALUE); }
{PopArrayValue}                { identifier = yytext().substring(POP_ARRAY_LEN).trim(); return symbol(sym.POP_ARRAY_VALUE); }

{DisplayMessage}               { identifier = yytext().substring(DISPLAY_MESSAGE_LEN).trim(); return symbol(sym.DISPLAY_MESSAGE); }
{PromptMessage}                { identifier = yytext().substring(PROMPT_MESSAGE_LEN).trim(); return symbol(sym.PROMPT_MESSAGE); }

/* operators */
"-"{WhiteSpace}                { return symbol(sym.SUB); }
"+"{WhiteSpace}                 { return symbol(sym.ADD); }
"/"{WhiteSpace}                 { return symbol(sym.DIV); }
"*"{WhiteSpace}                 { return symbol(sym.MUL); }
"%"{WhiteSpace}                 { return symbol(sym.MOD); }
"<>"{WhiteSpace}                { return symbol(sym.NEQ); }
"=="{WhiteSpace}                { return symbol(sym.EQ); }
"<"{WhiteSpace}                 { return symbol(sym.LT); }
"<="{WhiteSpace}                { return symbol(sym.LTE); }
">"{WhiteSpace}                 { return symbol(sym.GT); }
">="{WhiteSpace}                { return symbol(sym.GTE); }

/* comments */
{Comment}                      { /* ignore */ }

/* whitespace */
{WhiteSpace}                   { /* ignore */ }

/* error fallback */
[^]                            { return symbol(sym.SYNTAX_ERROR); }

<<EOF>>                        { return symbol(sym.EOF); }