package io.noties.markwon.ext.latex;

import org.commonmark.node.CustomBlock;

public class JLatexMathBlock extends CustomBlock {

    private String latex;
    private boolean isComplete;

    public String latex() {
        return latex;
    }

    public void latex(String latex, boolean isComplete) {
        this.latex = latex;
        this.isComplete = isComplete;
    }

    public boolean isComplete() {
        return isComplete;
    }
}
