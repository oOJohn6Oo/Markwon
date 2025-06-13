package io.noties.markwon.ext.latex;

import org.commonmark.node.CustomNode;

/**
 * @since 4.3.0
 */
public class JLatexMathNode extends CustomNode {

    private final String latex;

    public JLatexMathNode(String latex) {
        this.latex = latex;
    }

    public String latex() {
        return latex;
    }

}
