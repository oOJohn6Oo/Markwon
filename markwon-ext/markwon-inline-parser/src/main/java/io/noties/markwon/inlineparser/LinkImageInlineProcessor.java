package io.noties.markwon.inlineparser;
import static io.noties.markwon.inlineparser.InlineParserUtils.mergeChildTextNodes;

import io.noties.markwon.inlineparser.InlineProcessor;

import org.commonmark.internal.Bracket;
import org.commonmark.internal.util.Escaping;
import org.commonmark.node.*;

public class LinkImageProcessor extends InlineProcessor {

    public static final String RE4Image = "](\s\S)*(";

    @Override
    public char specialCharacter() {
        return '['; // 触发左中括号
    }

    @Override
    protected Node parse() {
        final int startIndex = index;

        // 支持图片：![alt](url)
        boolean isImage = false;
        index++;
        char lastChar = peek();
        index--;
        if (startIndex > 0 && lastChar == '!') {
            isImage = true;
        }

        // Check to see if we have a link/image

        String dest = null;
        String title = null;
        boolean isLinkOrImage = false;

        // Maybe a inline link like `[foo](/uri "title")`
        if (peek() == '(') {
            index++;
            spnl();
            if ((dest = parseLinkDestination()) != null) {
                spnl();
                // title needs a whitespace before
                if (WHITESPACE.matcher(input.substring(index - 1, index)).matches()) {
                    title = parseLinkTitle();
                    spnl();
                }
                if (peek() == ')') {
                    index++;
                    isLinkOrImage = true;
                } else {
                    index = startIndex;
                }
            }
        }

        // Maybe a reference link like `[foo][bar]`, `[foo][]` or `[foo]`
        if (!isLinkOrImage) {

            // See if there's a link label like `[bar]` or `[]`
            int beforeLabel = index;
            parseLinkLabel();
            int labelLength = index - beforeLabel;
            String ref = null;
            if (labelLength > 2) {
                ref = input.substring(beforeLabel, beforeLabel + labelLength);
            } else if (!opener.bracketAfter) {
                // If the second label is empty `[foo][]` or missing `[foo]`, then the first label is the reference.
                // But it can only be a reference when there's no (unescaped) bracket in it.
                // If there is, we don't even need to try to look up the reference. This is an optimization.
                ref = input.substring(opener.index, startIndex);
            }

            if (ref != null) {
                String label = Escaping.normalizeReference(ref);
                LinkReferenceDefinition definition = context.getLinkReferenceDefinition(label);
                if (definition != null) {
                    dest = definition.getDestination();
                    title = definition.getTitle();
                    isLinkOrImage = true;
                }
            }
        }

        if (isLinkOrImage) {
            // If we got here, open is a potential opener
            Node linkOrImage = opener.image ? new Image(dest, title) : new Link(dest, title);

            Node node = opener.node.getNext();
            while (node != null) {
                Node next = node.getNext();
                linkOrImage.appendChild(node);
                node = next;
            }

            // Process delimiters such as emphasis inside link/image
            processDelimiters(opener.previousDelimiter);
            mergeChildTextNodes(linkOrImage);
            // We don't need the corresponding text node anymore, we turned it into a link/image node
            opener.node.unlink();
            removeLastBracket();

            // Links within links are not allowed. We found this link, so there can be no other link around it.
            if (!opener.image) {
                Bracket bracket = lastBracket();
                while (bracket != null) {
                    if (!bracket.image) {
                        // Disallow link opener. It will still get matched, but will not result in a link.
                        bracket.allowed = false;
                    }
                    bracket = bracket.previous;
                }
            }

            return linkOrImage;

        } else { // no link or image
            index = startIndex;
            removeLastBracket();

            return text("]");
        }

    }

//    private boolean match(String s) {
//        if (input().regionMatches(index(), s, 0, s.length())) {
//            setIndex(index() + s.length());
//            return true;
//        }
//        return false;
//    }

    private String readUntil(char end) {
        StringBuilder sb = new StringBuilder();
        index++;
        char currentChar = peek();
        while (currentChar != end && currentChar != '\0') {
            sb.append(peek());
            index++;
            currentChar = peek();
        }
        if (currentChar == '\0' || peek() != end) {
            return null;
        }
        index++;
        return sb.toString();
    }
}
