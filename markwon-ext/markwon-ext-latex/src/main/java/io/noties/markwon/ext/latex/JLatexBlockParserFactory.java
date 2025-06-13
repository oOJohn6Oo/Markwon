package io.noties.markwon.ext.latex;

import static io.noties.markwon.ext.latex.JLatexMathBlockParser.consume;
import static io.noties.markwon.ext.latex.JLatexMathBlockParser.getSymbolByStyle;

import org.commonmark.internal.util.Parsing;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

public class JLatexBlockParserFactory extends AbstractBlockParserFactory {
    private static final char SPACE = ' ';
    @LatexParseStyle
    private final int parseStyle;

    public JLatexBlockParserFactory() {
        this.parseStyle = LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS;
    }

    public JLatexBlockParserFactory(int parseStyle) {
        this.parseStyle = parseStyle;
    }

    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {

        // let's define the spec:
        //  * 0-3 spaces before are allowed (Parsing.CODE_BLOCK_INDENT = 4)
        //  * 2+ subsequent `$` signs
        //  * any optional amount of spaces
        //  * new line
        //  * block is closed when the same amount of opening signs is met

        final int indent = state.getIndent();

        // check if it's an indented code block
        if (indent >= Parsing.CODE_BLOCK_INDENT) {
            return BlockStart.none();
        }

        final int nextNonSpaceIndex = state.getNextNonSpaceIndex();
        final CharSequence line = state.getLine();
        final int length = line.length();

        int signs;
        int signsMultiple;
        if (parseStyle == LatexParseStyle.STYLE_2_DOLLAR) {
            signsMultiple = 1;
            signs = consume('$', line, nextNonSpaceIndex, length);
            // 2 is minimum
            if (signs < 2) {
                return BlockStart.none();
            }
        }else{
            signsMultiple = 2;
            boolean canMatchMultiple = parseStyle == LatexParseStyle.STYLE_SLASH_DOLLAR;
            signs = consume(getSymbolByStyle(parseStyle, true), line, nextNonSpaceIndex, length, canMatchMultiple);
        }

        // consume spaces until the end of the line, if any other content is found -> NONE
        if (Parsing.skip(SPACE, line, nextNonSpaceIndex + (signs * signsMultiple), length) != length) {
            return BlockStart.none();
        }
        return BlockStart.of(new JLatexMathBlockParser(signs, parseStyle))
                .atIndex(length + 1);

    }

}