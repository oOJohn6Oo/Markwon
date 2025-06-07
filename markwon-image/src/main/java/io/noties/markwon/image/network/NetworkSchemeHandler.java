package io.noties.markwon.image.network;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.SchemeHandler;

/**
 * A simple network scheme handler that is not dependent on any external libraries.
 *
 * @see #create()
 * @since 3.0.0
 */
public class NetworkSchemeHandler extends SchemeHandler {

    public static final String SCHEME_HTTP = "http";
    public static final String SCHEME_HTTPS = "https";

    private static final HashMap<String, ImageItem.WithDecodingNeeded> imgPathMap = new HashMap<>();

    private static ExecutorService requestExecutor = Executors.newFixedThreadPool(12);

    @NonNull
    public static NetworkSchemeHandler create() {
        return new NetworkSchemeHandler();
    }

    @SuppressWarnings("WeakerAccess")
    NetworkSchemeHandler() {

    }

    @NonNull
    @Override
    public ImageItem handle(@NonNull String raw, @NonNull Uri uri) {
        ImageItem.WithDecodingNeeded previousItem = imgPathMap.get(raw);
        if (previousItem != null) {
            if (previousItem.isProcessing()) return previousItem;
            if (previousItem.getCachedDrawable() != null) return previousItem;
        } else {
            previousItem = new ImageItem.WithDecodingNeeded();
            imgPathMap.put(raw, previousItem);
        }
        requestExecutor.submit(() -> doRequestImage(raw));
        return previousItem;
    }

    private static void doRequestImage(@NonNull String raw) {
        final ImageItem.WithDecodingNeeded imageItem;
        try {

            final URL url = new URL(raw);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                final String contentType = contentType(connection.getHeaderField("Content-Type"));
                final InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                imageItem = ImageItem.withDecodingNeeded(contentType, inputStream);
                imgPathMap.put(raw, imageItem);
            } else {
//                throw new IOException("Bad response code: " + responseCode + ", url: " + raw);
            }

        } catch (IOException e) {
            imgPathMap.remove(raw);
//            throw new IllegalStateException("Exception obtaining network resource: " + raw, e);
        }
    }

    @NonNull
    @Override
    public Collection<String> supportedSchemes() {
        return Arrays.asList(SCHEME_HTTP, SCHEME_HTTPS);
    }

    @Nullable
    static String contentType(@Nullable String contentType) {

        if (contentType == null) {
            return null;
        }

        final int index = contentType.indexOf(';');
        if (index > -1) {
            return contentType.substring(0, index);
        }

        return contentType;
    }
}
