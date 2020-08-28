package com.ouiplusplus.parser;
import com.ouiplusplus.error.Error;
import com.ouiplusplus.helper.Pair;
import com.ouiplusplus.lexer.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private List<Token> allTokens = new ArrayList<>();
    ASTExpression ast = new ASTExpression();

    public Parser() {
    }

    public Pair<String, Error> toStringParse() {

        //add list
        Error addErr = ast.addList(this.allTokens);
        if (addErr != null) return new Pair<>(null, addErr);

        Pair<Token, Error> treeVal = ast.resolveTreeVal();
        if (treeVal.getP2() != null) return new Pair<>(null, treeVal.getP2());
        String val;
        if (treeVal.getP1().isNeg() && !treeVal.getP1().getValue().equals("0")) {
            val = "-" + treeVal.getP1().getValue();
        }
        else val = treeVal.getP1().getValue();
        ast.clearTree();
        return new Pair<>(val, null);
    }

    public void setAllTokens(List<Token> allTokens) {
        this.allTokens = allTokens;
    }
}
