package io.noties.markwon.ext.latex;

import static io.noties.markwon.ext.latex.LatexParseStyle.STYLE_SLASH_DOLLAR;
import static io.noties.markwon.ext.latex.LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS;

import androidx.annotation.IntDef;

/**
 * Styles for parsing latex blocks and inlines.
 * default style is {@value STYLE_SLASH_SQUARE_BRACKETS}
 */
@IntDef({LatexParseStyle.STYLE_2_DOLLAR, STYLE_SLASH_DOLLAR, STYLE_SLASH_SQUARE_BRACKETS})
public @interface LatexParseStyle {
    /**
     * $$
     */
    int STYLE_2_DOLLAR = 0;
    /**
     * \$
     */
    int STYLE_SLASH_DOLLAR = 1;
    /**
     * \[
     * this is the standard way
     */
    int STYLE_SLASH_SQUARE_BRACKETS = 2;
}
