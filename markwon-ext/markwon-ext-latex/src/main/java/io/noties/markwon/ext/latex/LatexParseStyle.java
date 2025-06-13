package io.noties.markwon.ext.latex;

import static io.noties.markwon.ext.latex.LatexParseStyle.STYLE_DOLLAR;
import static io.noties.markwon.ext.latex.LatexParseStyle.STYLE_BRACKETS;

import androidx.annotation.IntDef;

/**
 * Styles for parsing latex blocks and inlines.
 * default style is {@value STYLE_BRACKETS}
 */
@IntDef({STYLE_DOLLAR, STYLE_BRACKETS})
public @interface LatexParseStyle {
    /**
     * $$
     */
    int STYLE_DOLLAR = 1;
    /**
     * \[
     * this is the standard way
     */
    int STYLE_BRACKETS = 2;
}
