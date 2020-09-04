package com.ouiplusplus.parser;
import com.ouiplusplus.error.*;
import com.ouiplusplus.error.Error;
import com.ouiplusplus.helper.Pair;
import com.ouiplusplus.lexer.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;


import java.util.List;

public class ASTExpression {
    /* DETAILS
    * PEMDAS WITH LEFT ON BOTTOM OF TREE
    * LPAREN AND INT/DOUBLE CAN BE NEGATIVE
    * LPAREN OPEN AT START THEN CLOSED LATER ON
    *
    * POSSIBLE ERRORS:
    * OVERFLOW
    * DIVIDE BY 0
    * EMPTY PARENTHESES*/
    private TreeNode root;
    private TGParser tgparser; //for functions and variables
    private int opened; //number of opened parentheses
    private Position start;
    private Position end;
    private int size;

    //############## CLASS METHODS #######################
    public ASTExpression(TGParser parser) {
        this.tgparser = parser;
        this.opened = 0;
    }
    public ASTExpression() {
        this.opened = 0;
    }


    private Error addVal(Token token) { //null for no errors
        Error err = new UnexpectedToken(token.getStart(), token.getEnd(), token.getValue());
        if (token.getType() == TokenType.VAR) { //converts variable to primitive type
            if (!this.tgparser.getVars().containsKey(token.getValue())) return err;
            Position s = token.getStart();

            // Making copy so i dont override the variable, just getting from map in TGParser
            token = this.tgparser.getVars().get(token.getValue()).copy();
            token.setStart(s);
        }
        TokenType tt = token.getType();
        this.size++;
        return switch (tt) {
            case STRING, DOUBLE, INT -> casePrimitiveType(token);
            case MULT, DIV, MODULO, CARROT -> caseMULTDIV(token);
            case PLUS, MINUS -> casePLUSMINUS(token);
            case LPAREN -> caseLPAREN(token);
            case RPAREN -> caseRPAREN(token);
            default -> err;
        };
    }

    public Pair<Token, Error> process(List<Token> tokens) {
        Error err;
        // checks if boolean
        if (isBoolTok(tokens)) {
            ASTBoolean astBool = new ASTBoolean(this, this.tgparser);
            return astBool.process(tokens);
        }
        if (!tokens.isEmpty()) {
            this.start = tokens.get(0).getStart();
            this.end = tokens.get(tokens.size() - 1).getEnd();
        }
        for(Token tok: tokens) {
            err = this.addVal(tok);
            if (err != null) {
                return new Pair<>(null, err);
            }
        }
        Pair<Token, Error> rtnPair = this.dfsResolveVal(this.root);
        this.clearTree();
        return rtnPair;
    }

    @Override
    public String toString() {
        if (this.root == null) return "";
        return this.dfsToString(this.root);
    }

    public void clearTree() {
        this.root = null;
        this.opened = 0;
    }



    //############## END CLASS METHODS ####################

    // ############## LIST OF ALL CASES ###################
    private Error caseLPAREN(Token token) {
        if (this.root == null) { //case root null
            this.root = new TreeNode(token);
            this.opened++;
            return null;
        }

        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        //case LPAREN we are at has no leaves
        if (currNode.token.getType() == TokenType.LPAREN && currNode.left == null) {
            currNode.left = new TreeNode(token);
            this.opened++;
            return null;
        }

        //finding entry point and adding in value
        // Traverses down right side of tree until null right leaf found
        while (true) {
            if (currNode.right == null && isOp(currNode.token.getType())) {
                currNode.right = new TreeNode(token);
                this.opened++;
                return null;
            }
            if(currNode.left == null && !isOp(currNode.token.getType())) {
                currNode.left = new TreeNode(token);
                this.opened++;
                return null;
            }
            if (isOp(currNode.token.getType())) {
                currNode = currNode.right;
            } else{
                currNode = currNode.left;
            }

        }
    }
    private Error caseRPAREN(Token token) {
        Error err = new UnclosedParenthesis(token.getStart(), token.getEnd(), "()");
        if (this.root == null) return err;

        TreeNode currNode;
        if (this.opened != 0) {
            currNode = this.returnBottomOpenParen();
            Error unexpected = new UnclosedParenthesis(currNode.token.getStart(), token.getEnd(), "()");
            if (currNode.left == null) return unexpected; //if parenthesis pair with nothing inside
            Token tmp = currNode.token;
            currNode.token = new Token(TokenType.CLOSEDPAREN, "()", currNode.token.getStart(), token.getEnd());
            if (tmp.isNeg()) currNode.token.setNeg(true);
            this.opened--;
            return null;
        }
        return err;
    }
    private Error casePrimitiveType(Token token) {
        if (this.root == null) { //case root null
            this.root = new TreeNode(token);
            return null;
        }

        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        //case LPAREN we are at has no leaves
        if (currNode.token.getType() == TokenType.LPAREN && currNode.left == null) {
            currNode.left = new TreeNode(token);
            return null;
        }

        //finding entry point and adding in value
        // Traverses down right side of tree until null right leaf found
        while (true) {
            if (currNode.right == null && isOp(currNode.token.getType())) {
                currNode.right = new TreeNode(token);
                return null;
            }
            if (isOp(currNode.token.getType())) {
                currNode = currNode.right;
            } else {
                currNode = currNode.left;
            }

        }
    }
    private Error caseMULTDIV(Token token) {

        //setting currNode
        TreeNode currNode;
        if (this.opened != 0) currNode = this.returnBottomOpenParen();
        else currNode = this.root;

        // Loops through tree for place to add
        while (true) {
            if (currNode.right == null) {
                TreeNode left = null;
                if (currNode.left != null) left = currNode.left.copy();
                if (currNode.token.getType() == TokenType.LPAREN) {
                    if (left.token.getType() != TokenType.PLUS
                            && left.token.getType() != TokenType.MINUS) {
                        currNode.left = new TreeNode(token);
                        currNode.left.left = left;
                        return null;
                    } else currNode = currNode.left;

                } else {
                    Token tmp = currNode.token;
                    currNode.token = token;
                    currNode.left = new TreeNode(tmp);
                    currNode.left.left = left;
                    return null;
                }
            } else if (currNode.token.getType() == TokenType.MULT
                    || currNode.token.getType() == TokenType.DIV
                    || currNode.token.getType() == TokenType.MODULO
                    || currNode.token.getType() == TokenType.CARROT) {
                TreeNode tmp = currNode.copy();
                currNode = new TreeNode(token);
                currNode.left = tmp;
                this.root = currNode;
                return null;
            }else if (currNode.right.token.getType() == TokenType.MULT
                    || currNode.right.token.getType() == TokenType.DIV
                    || currNode.right.token.getType() == TokenType.MODULO
                    || currNode.right.token.getType() == TokenType.CARROT) {
                TreeNode right = currNode.right.copy();
                currNode.right = new TreeNode(token);
                currNode.right.left = right;
                return null;
            }
             else currNode = currNode.right;
        }
    }
    private Error casePLUSMINUS(Token token) {
        //setting currNode
        TreeNode currNode;
        if (this.opened != 0)  {
            currNode = this.returnBottomOpenParen();
            TreeNode tmpLeft = currNode.left;
            currNode.left = new TreeNode(token);
            currNode.left.left = tmpLeft;
        }
        else {
            TreeNode tmp = new TreeNode(token);
            tmp.left = this.root;
            this.root = tmp;
        }
        return null;
    }
    // ################# END OF CASES ###########################


    // ################## HELPER METHODS #########################

    public TreeNode returnBottomOpenParen() {
        if (this.opened == 0) return null;
        Queue<TreeNode> q = new LinkedList<>();
        int leftToDiscover = this.opened;
        q.add(this.root);
        while (q.size() != 0) {
            TreeNode curr = q.remove();
            //if LPAREN
            if (curr.token.getType() == TokenType.LPAREN) leftToDiscover--;
            if (leftToDiscover == 0) return curr;

            //adding left and right nodes
            if (curr.left != null) q.add(curr.left);
            if (curr.right != null) q.add(curr.right);
        }
        return null;
    }

    private static boolean isOp(TokenType tt) {
        return tt == TokenType.MULT
                || tt == TokenType.DIV
                || tt == TokenType.MINUS
                || tt == TokenType.PLUS
                || tt == TokenType.MODULO
                || tt == TokenType.CARROT;
    }


    private Pair<Token, Error> dfsResolveVal(TreeNode node) {
        if (node == null) return new Pair<>(null, new Error("Empty Tree"));
        if (node.token.isNeg() && (node.token.getType() == TokenType.BOOLEAN
                || node.token.getType() == TokenType.STRING
                || node.token.getType() == TokenType.NULL))
            return new Pair<>(null, new InvalidOperation(node.token.getStart(), node.token.getEnd(), "-"));
        Pair<Token, Error> tmp;
        Error err;
        if (node.right != null && node.left != null) {
            // Left tree traversal
            Pair<Token, Error> left = this.dfsResolveVal(node.left);
            err = left.getP2();
            if (err != null) return new Pair<>(null, err);

            //Right Tree Traversal
            Pair<Token, Error> right = this.dfsResolveVal(node.right);
            err = right.getP2();
            if (err != null) return new Pair<>(null, err);

            //Combine Tokens
            tmp = ASTCombineTokens.combine(left.getP1(), node.token, right.getP1(), this.start, this.end);
        } else if (node.left != null) { // case of ()
            tmp = this.dfsResolveVal(node.left);
            err = tmp.getP2();
            if(err != null) return new Pair<>(null, err);
            if(node.token.isNeg()) tmp.getP1().setNeg(!tmp.getP1().isNeg());
        }
        else return new Pair<>(node.token, null);
        return tmp;
    }

    private String dfsToString(TreeNode node) {
        String tmp = "";
        if (node.right != null && node.left != null) {
            tmp += "("  + this.dfsToString(node.left);
            tmp += node.token + this.dfsToString(node.right) + ")";
        } else if (node.left != null) { // case of ()
            tmp += "(" + this.dfsToString(node.left) + ")";
        }
        else return "[" + node.token + "]";
        return tmp;
    }

    public boolean isBoolTok(List<Token> lst) {
        List<TokenType> boolOps = Arrays.asList(TokenType.BOOLEAN,
                TokenType.DOUBLE_EQUALS, TokenType.GREATER_THAN,
                TokenType.GREATER_THAN_OR_EQUALS,
                TokenType.LESS_THAN, TokenType.LESS_THAN_OR_EQUALS,
                TokenType.NOT_EQUAL, TokenType.NOT,
                TokenType.AND, TokenType.OR);
        for(Token t : lst) {
            // if variable convert to type
            Token tmp = null;
            if (t.getType() == TokenType.VAR) { //converts variable to primitive type

                tmp = this.tgparser.getVars().get(t.getValue()).copy();
            }
            if(boolOps.contains(t.getType())
                    || (tmp!= null && boolOps.contains(tmp.getType()))) {
                return true;
            }
        }
        return false;
    }

    // ################### END HELPER METHODS #####################


    // ################### TREE CLASS #############################
    class TreeNode {
        public Token token;
        public TreeNode right, left;

        public TreeNode(Token token) {
            this.token = token;
        }

        public TreeNode(Token token, TreeNode right, TreeNode left) {
            this.token = token;
            this.right = right;
            this.left = left;
        }

        public TreeNode copy() {
            return new TreeNode(token, right, left);
        }
    }
    // #################### END TREE CLASS ###########################
}
