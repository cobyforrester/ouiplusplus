package com.ouiplusplus.lexer;

import com.ouiplusplus.error.Error;
import com.ouiplusplus.error.UnexpectedChar;
import com.ouiplusplus.helper.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Lexer {
    final private String text; //all text
    private Position pos;
    private char currChar;
    final private String fn; // file name
    final private BeforeAfterLsts ba; //lists for legal characters before and after token


    public Lexer(String fn, String text) {
        this.fn = fn;
        this.text = text;
        this.pos = new Position(-1, 0, -1, this.fn, this.text);
        this.currChar = 0; //initialized to null 0 is ascii for null
        this.ba = new BeforeAfterLsts();
        this.advance();
    }

    public void advance() {
        this.pos.advance(currChar);
        if (this.pos.getIndex() < this.text.length()) {
            this.currChar = this.text.charAt(pos.getIndex());
        } else {
            this.currChar = 0;
        }
    }

    public Pair<List<Token>, Error> make_tokens() {
        List<Token> tokens = new ArrayList<>();
        String alph = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String nums = "0123456789";
        while (this.currChar != 0) {
            if (nums.indexOf(this.currChar) > -1) {
                tokens.add(this.makeNumberToken());
            } else if(alph.indexOf(this.currChar) > -1) {
                tokens.add(this.makeAlphToken());
            }else {
                switch (this.currChar) {
                    case ' ':
                    case '\t':
                        break;

                    // OPERATIONS AND RELATED TO MATH
                    case '+':
                        tokens.add(new Token(TokenType.PLUS));
                        break;
                    case '-':
                        tokens.add(new Token(TokenType.MINUS));
                        break;
                    case '/':
                        tokens.add(new Token(TokenType.DIV));
                        break;
                    case '*':
                        tokens.add(new Token(TokenType.MULT));
                        break;
                    case '=':
                        tokens.add(new Token(TokenType.EQUALS));
                        break;

                    // (){}[]
                    case '(':
                        tokens.add(new Token(TokenType.LPAREN));
                        break;
                    case ')':
                        tokens.add(new Token(TokenType.RPAREN));
                        break;

                    //SPECIAL CHARACTERS
                    case ';':
                        tokens.add(new Token(TokenType.SEMICOLON));
                        break;
                    default:
                        String details = "'" + currChar + "'";
                        Position start = this.pos.copy();
                        this.advance();
                        UnexpectedChar err = new UnexpectedChar(start, this.pos, details);
                        List<Token> lst = new ArrayList<>();
                        return new Pair<>(lst, err);
                }
                this.advance();
            }
        }
        Error valParen = validateParentheses(tokens);
        if (valParen != null) return new Pair<> (null ,valParen);
        return this.validateTokens(tokens);
    }

    private Token makeNumberToken() {
        String num = "";
        int dotCount = 0;
        while (this.currChar != 0 && "0123456789.".indexOf(this.currChar) > -1) {
            if ('.' == currChar) {
                if (dotCount == 1) break; //we cant have more than one '.' in number
                dotCount++;
            }
            num += currChar; // creating number
            this.advance();
        }
        if (num.charAt(num.length() - 1) == '.') {
            num = num.substring(0, num.length() - 1);
            dotCount--;
        }

        if (dotCount == 0) {
            return new Token(TokenType.INT, num);
        }
        return new Token(TokenType.DOUBLE, num);
    }

    private Token makeAlphToken() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        String word = "";
        while (this.currChar != 0 && chars.indexOf(this.currChar) > -1) {
            word += currChar; // creating number
            this.advance();
        }
        return new Token(TokenType.WORD, word);
    }


    private Pair<List<Token>, Error> validateTokens(List<Token> tokens) {
        List<Token> lst = new ArrayList<>();
        Pair<List<Token>, Error> err = new Pair<>(null, new Error());
        for (int i = 0; i < tokens.size(); i++) {
            TokenType currTT = tokens.get(i).getType();
            // ALL FOR ADD AND SUBTRACT
            if(currTT == TokenType.INT || currTT == TokenType.DOUBLE) {
                /* THIS IS FOR WHEN A INT OR DOUBLE IS ENCOUNTERED */
                if (i == 0) {
                    if (i != tokens.size() - 1) {
                        TokenType next = tokens.get(i + 1).getType();
                        if(isInLst(next, this.ba.getAfterINTDOUBLTAdd())) lst.add(tokens.get(i));
                        else return err;
                    } else lst.add(tokens.get(i));
                }
                else if(i == tokens.size() - 1) {
                    TokenType prev = tokens.get(i - 1).getType();
                    if(isInLst(prev, this.ba.getBeforeINTDOUBLTAdd())) lst.add(tokens.get(i));
                    else return err;
                }
                else {
                    TokenType prev = tokens.get(i - 1).getType();
                    TokenType next = tokens.get(i + 1).getType();
                    if(isInLst(prev, this.ba.getBeforeINTDOUBLTAdd())
                            && isInLst(next, this.ba.getAfterINTDOUBLTAdd())) {
                        lst.add(tokens.get(i));
                    } else {
                        return err;
                    }
                }
            }
            else if(currTT == TokenType.MULT || currTT == TokenType.DIV) {
                if (i == 0 || i == tokens.size() - 1) return err;
                else {
                    TokenType prev = tokens.get(i - 1).getType();
                    TokenType next = tokens.get(i + 1).getType();
                    if(isInLst(prev, this.ba.getBeforeMULTDIVAdd())
                            && isInLst(next, this.ba.getAfterMULTDIVAdd())) {
                        lst.add(tokens.get(i));
                    } else {
                        return err;
                    }
                }
            }
            else if (currTT == TokenType.PLUS || currTT == TokenType.MINUS) {
                /* THIS IS FOR WHEN A PLUS OR MINUS IS ENCOUNTERED */
                int negCount = 0;
                if (i > 0) {
                    TokenType prevTT = tokens.get(i - 1).getType();
                    if(currTT == TokenType.PLUS) {
                        if (isInLst(prevTT, this.ba.getBeforePLUSAdd())) {
                            lst.add(tokens.get(i));
                        } else if (isInLst(prevTT, this.ba.getBeforePLUSErr())) {
                            return new Pair<>(null, new Error());
                        }
                    } else { // FOR MINUS
                        if (isInLst(prevTT, this.ba.getBeforeMINUSAdd())) {
                            lst.add(tokens.get(i));
                            negCount--;
                        } else if (isInLst(prevTT, this.ba.getBeforeMINUSErr())) {
                            return new Pair<>(null, new Error());
                        }
                    }
                }
                while (currTT == TokenType.PLUS || currTT == TokenType.MINUS) {
                    if(i+1 >= tokens.size()) return err;
                    TokenType nextTT = tokens.get(i + 1).getType();
                    if(currTT == TokenType.PLUS) {
                        if (isInLst(nextTT, this.ba.getAfterPLUSErr())) {
                            return err;
                        }
                    } else { // FOR MINUS
                        negCount++;
                        if (isInLst(nextTT, this.ba.getAfterMINUSErr())) {
                            return err;
                        }
                    }
                    if (nextTT == TokenType.PLUS || nextTT == TokenType.MINUS) i++; //keep going
                    currTT = nextTT;
                }
                if(negCount % 2 == 1) { //if negative then set next token to negative
                    tokens.get(i + 1).setNeg(true);
                }
            } else {
                lst.add(tokens.get(i));
            }
        }
        return new Pair<>(lst, null);
    }

    private static Error validateParentheses(List<Token> tLst) {
        Error err = new Error();
        Stack<TokenType> s = new Stack<>();
        int parenOpen = 0, cBraceOpen = 0, bracketOpen = 0; //implement later for extra cases
        for (Token t: tLst) {
            TokenType type = t.getType();
            switch(type) {
                case LPAREN:
                case LCBRACE:
                case LBRACKET:
                    s.push(type);
                    break;
                case RPAREN:
                    if(s.size() == 0 || s.pop() != TokenType.LPAREN) return err;
                    else break;
                case RBRACKET:
                    if(s.size() == 0 || s.pop() != TokenType.LBRACKET) return err;
                    else break;
                case RCBRACE:
                    if(s.size() == 0 || s.pop() != TokenType.LCBRACE) return err;
                    else break;
            }
        }
        if (s.size() != 0) return new Error();
        return null;
    }

    // ==================== HELPER FUNCTIONS ==========================
    public static boolean isInLst(TokenType tt, TokenType[] ttList) {
        for (TokenType elemTokenType : ttList) {
            if (tt == elemTokenType) return true;
        }
        return false;
    }
    // ==================== END HELPER FUNCTIONS ==========================
}
