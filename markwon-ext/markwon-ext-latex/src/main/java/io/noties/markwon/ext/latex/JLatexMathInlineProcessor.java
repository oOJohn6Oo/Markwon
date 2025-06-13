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

    private static final Pattern RE4Style2Dollar = Pattern.compile("(\\${2})([\\s\\S]+?)\\1");
    private static final Pattern RE4StyleSlashDollar = Pattern.compile("\\\\\\$([\\s\\S]+?)\\\\\\$");
    private static final Pattern RE4StyleSlashSBracket = Pattern.compile("\\\\\\(([\\s\\S]+?)\\\\\\)");

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
        switch (parseStyle){
            case LatexParseStyle.STYLE_2_DOLLAR:
                return RE4Style2Dollar;

            case LatexParseStyle.STYLE_SLASH_DOLLAR:
                return RE4StyleSlashDollar;

            default:
                return RE4StyleSlashSBracket;
        }
    }

    private char mapStyle2SpecialChar(){
        switch (parseStyle){
            case LatexParseStyle.STYLE_SLASH_DOLLAR:
            case LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS:
                return '\\';

            default:
                return '$';
        }
    }
}
