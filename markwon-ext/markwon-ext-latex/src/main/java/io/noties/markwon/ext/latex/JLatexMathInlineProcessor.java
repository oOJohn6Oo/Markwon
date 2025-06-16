package io.noties.markwon.ext.latex;

import androidx.annotation.Nullable;

import org.commonmark.node.Node;

import java.util.regex.Pattern;

import io.noties.markwon.inlineparser.InlineProcessor;

/**
 * @since 4.3.0
 */
class JLatexMathInlineProcessor extends InlineProcessor {

    @LatexParseStyle
    private final int parseStyle;

    public JLatexMathInlineProcessor(@LatexParseStyle int parseStyle) {
        this.parseStyle = parseStyle;
    }
//    private static final Pattern RE4StyleDollar = Pattern.compile("(\\${2})(.+?)\\1");
    private static final Pattern RE4StyleDollar = Pattern.compile("(?<!\\\\)(\\$+)(.+?)(?<!\\\\)\\1");
    private static final Pattern RE4StyleBracket = Pattern.compile("\\\\\\(([\\s\\S]+?)\\\\\\)|\\\\\\[([\\s\\S]+?)\\\\\\]");

    @Override
    public char specialCharacter() {
        return mapStyle2SpecialChar();
    }

    @Nullable
    @Override
    protected Node parse() {

        final String latex = match(mapStyle2Pattern());
        if (latex == null) {
            return null;
        }

        return new JLatexMathNode(latex.substring(2, latex.length() - 2));
    }

    private Pattern mapStyle2Pattern(){
        if (parseStyle == LatexParseStyle.STYLE_DOLLAR) {
            return RE4StyleDollar;
        }
        return RE4StyleBracket;
    }

    private char mapStyle2SpecialChar(){
        if (parseStyle == LatexParseStyle.STYLE_BRACKETS) {
            return '\\';
        }
        return '$';
    }
}
