package io.noties.markwon.image.data;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.ImageLoadedNotifier;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.image.SchemeHandler;

/**
 * @since 2.0.0
 */
public class DataUriSchemeHandler extends SchemeHandler {

    public static final String SCHEME = "data";

    private static final String START = "data:";

    private final DataUriParser uriParser;
    private final DataUriDecoder uriDecoder;

    public DataUriSchemeHandler() {
        this(DataUriParser.create(), DataUriDecoder.create(), false);
    }

    public DataUriSchemeHandler(boolean shouldHandleAsync) {
        this(DataUriParser.create(), DataUriDecoder.create(), shouldHandleAsync);
    }

    private DataUriSchemeHandler(@NonNull DataUriParser uriParser, @NonNull DataUriDecoder uriDecoder, boolean shouldHandleAsync) {
        this.uriParser = uriParser;
        this.uriDecoder = uriDecoder;
        this.shouldHandleAsync = shouldHandleAsync;
    }

    @NonNull
    @Override
    public ImageItem handle(@NonNull String raw, @NonNull Uri uri, @Nullable ImageLoadedNotifier notifier) {

        if (!raw.startsWith(START)) {
            throw new IllegalStateException("Invalid data-uri: " + raw);
        }

        final String part = raw.substring(START.length());

        final DataUri dataUri = uriParser.parse(part);
        if (dataUri == null) {
            throw new IllegalStateException("Invalid data-uri: " + raw);
        }

        final byte[] bytes;
        try {
            bytes = uriDecoder.decode(dataUri);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot decode data-uri: " + raw, t);
        }

        if (bytes == null) {
            throw new IllegalStateException("Decoding data-uri failed: " + raw);
        }

        return ImageItem.withDecodingNeeded(
                dataUri.contentType(),
                new ByteArrayInputStream(bytes));
    }

    @NonNull
    @Override
    public Collection<String> supportedSchemes() {
        return Collections.singleton(SCHEME);
    }
}
