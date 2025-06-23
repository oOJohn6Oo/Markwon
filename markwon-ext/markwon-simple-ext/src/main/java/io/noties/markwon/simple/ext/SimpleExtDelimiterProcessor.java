package io.noties.markwon.simple.ext;

import androidx.annotation.NonNull;

import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;

import io.noties.markwon.SpanFactory;

// @since 4.0.0
class SimpleExtDelimiterProcessor implements DelimiterProcessor {

    private final char open;
    private final char close;
    private final int length;
    private final SpanFactory spanFactory;

    SimpleExtDelimiterProcessor(
            char open,
            char close,
            int length,
            @NonNull SpanFactory spanFactory) {
        this.open = open;
        this.close = close;
        this.length = length;
        this.spanFactory = spanFactory;
    }

    @Override
    public char getOpeningCharacter() {
        return open;
    }

    @Override
    public char getClosingCharacter() {
        return close;
    }

    @Override
    public int getMinLength() {
        return length;
    }

//    @Override
//    public int getDelimiterUse(DelimiterRun opener, DelimiterRun closer) {
//        if (opener.length() >= length && closer.length() >= length) {
//            return length;
//        }
//        return 0;
//    }

    @Override
    public int process(DelimiterRun openingRun, DelimiterRun closingRun) {

        if (openingRun.length() < length || closingRun.length() < length) {
            return 0;
        }
        final Node node = new SimpleExtNode(spanFactory);
        final Text opener = openingRun.getOpener();
        final Text closer = openingRun.getCloser();

        int count = 0;

        Node tmp = opener.getNext();
        Node next;

        while (tmp != null && tmp != closer) {
            next = tmp.getNext();
            node.appendChild(tmp);
            tmp = next;
            count++;
        }

        opener.insertAfter(node);
        return count;
    }

//    @Override
//    public void process(Text opener, Text closer, int delimiterUse) {
//
//        final Node node = new SimpleExtNode(spanFactory);
//
//        Node tmp = opener.getNext();
//        Node next;
//
//        while (tmp != null && tmp != closer) {
//            next = tmp.getNext();
//            node.appendChild(tmp);
//            tmp = next;
//        }
//
//        opener.insertAfter(node);
//    }
}
