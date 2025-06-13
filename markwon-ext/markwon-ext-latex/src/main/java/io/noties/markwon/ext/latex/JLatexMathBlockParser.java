package io.noties.markwon.ext.latex;

import androidx.annotation.NonNull;

import org.commonmark.internal.util.Parsing;
import org.commonmark.node.Block;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.ParserState;

/**
 * @since 4.3.0 (although there was a class with the same name,
 * which is renamed now to {@link JLatexMathBlockParserLegacy})
 */
class JLatexMathBlockParser extends AbstractBlockParser {

    /**
     *  0 => $$;<br/>
     *  1 => \$; <br/>
     *  2 => \[;<br/>
     * @see JLatexMathBlockParser#getSymbolByStyle
     */
    @LatexParseStyle
    private final int parseStyle;

    private static final char SPACE = ' ';

    private final JLatexMathBlock block = new JLatexMathBlock();

    private final StringBuilder builder = new StringBuilder();

    private final int startSignCount;

    private boolean isParseEnded = false;

    JLatexMathBlockParser(int startSignCount) {
        this.startSignCount = startSignCount;
        this.parseStyle = LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS;
    }

    JLatexMathBlockParser(int startSignCount, @LatexParseStyle int parseStyle) {
        this.startSignCount = startSignCount;
        this.parseStyle = parseStyle;
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState parserState) {
        final int nextNonSpaceIndex = parserState.getNextNonSpaceIndex();
        final CharSequence line = parserState.getLine();
        final int length = line.length();
        isParseEnded = false;

        // check for closing
        if (parserState.getIndent() < Parsing.CODE_BLOCK_INDENT) {
            int consumed;
            int signsMultiple;
            if(this.parseStyle == LatexParseStyle.STYLE_2_DOLLAR){
                signsMultiple = 1;
                consumed = consume('$', line, nextNonSpaceIndex, length);
            }else{
                signsMultiple = 2;
                boolean canMatchMultiple = this.parseStyle == LatexParseStyle.STYLE_SLASH_DOLLAR;
                consumed = consume(getSymbolByStyle(parseStyle, false), line, nextNonSpaceIndex, length, canMatchMultiple);
            }
            if (consumed == startSignCount) {
                // okay, we have our number of signs
                // let's consume spaces until the end
                if (Parsing.skip(SPACE, line, nextNonSpaceIndex + startSignCount * signsMultiple, length) == length) {
                    isParseEnded = true;
                    return BlockContinue.finished();
                }
            }
        }

        return BlockContinue.atIndex(parserState.getIndex());
    }

    @Override
    public void addLine(CharSequence line) {
        builder.append(line);
        builder.append('\n');
    }

    @Override
    public void closeBlock() {
        block.latex(builder.toString(), isParseEnded);
    }

    /**
     * This is for style 1 and 2
     * For style 1, we could have multiple s
     * For style 2, we can only have 1 s
     */
    static int consume(String s, @NonNull CharSequence line, int start, int end, boolean canMatchMultiple) {
        final int lengthS = s.length();
        if(canMatchMultiple){
            int matchedCount = 0;
            for (int i = start; i < end; i++) {
                for (int j = 0; j < lengthS && i < end; j++) {
                    if (s.charAt(j) != line.charAt(i)) {
                        return matchedCount;
                    }
                    i++;
                }
                matchedCount++;
            }
            return matchedCount;
        }

        if(line.length() >= s.length()){
            for (int i = 0; i < lengthS; i++){
                if (s.charAt(i) != line.charAt(i)){
                    return 0;
                }
            }
            return 1;
        }

        return 0;
    }

    static int consume(char c, @NonNull CharSequence line, int start, int end) {
        for (int i = start; i < end; i++) {
            if (c != line.charAt(i)) {
                return i - start;
            }
        }
        // all consumed
        return end - start;
    }

    static String getSymbolByStyle(int style, boolean start) {
        switch (style) {
            case LatexParseStyle.STYLE_2_DOLLAR:
                return "$$";
            case LatexParseStyle.STYLE_SLASH_DOLLAR:
                return "\\$";
            default:
                if(start) return "\\[";
                else return "\\]";
        }
    }

}
