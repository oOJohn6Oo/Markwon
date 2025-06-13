package io.noties.markwon.image.network;

import android.net.Uri;
import android.util.Log;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.ImageItem.WithDecodingNeeded;
import io.noties.markwon.image.ImageLoadedNotifier;
import io.noties.markwon.image.MediaDecoder;
import io.noties.markwon.image.SchemeHandler;

/**
 * A simple network scheme handler that is not dependent on any external libraries.
 *
 * @since 3.0.0
 */
public class NetworkSchemeHandler extends SchemeHandler {

    public static final String SCHEME_HTTP = "http";
    public static final String SCHEME_HTTPS = "https";

    private final ConcurrentHashMap<String, WithDecodingNeeded> cachedImageItems = new ConcurrentHashMap<>();

    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(12);

    public NetworkSchemeHandler() {
        this(false);
    }

    public NetworkSchemeHandler(boolean shouldHandleAsync) {
        this.shouldHandleAsync = shouldHandleAsync;
    }

    @Override
    public ImageItem prefetch(@NonNull String imgUrl, @NonNull Uri uri) {
        return cachedImageItems.get(imgUrl);
    }

    @NonNull
    @Override
    public ImageItem handle(@NonNull String raw, @NonNull Uri uri, @Nullable ImageLoadedNotifier notifier) {
        if (shouldHandleAsync) {
            return onAsyncRequestImage(raw, notifier);
        }
        return onSyncRequestImage(raw);
    }

    private WithDecodingNeeded onSyncRequestImage(@NonNull String imgUrl){

        final WithDecodingNeeded imageItem;
        try {
            final URL url = new URL(imgUrl);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                final String contentType = contentType(connection.getHeaderField("Content-Type"));
                final InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                imageItem = ImageItem.withDecodingNeeded(contentType, inputStream);
                cachedImageItems.put(imgUrl, imageItem);
            } else {
                throw new IOException("Bad response code: " + responseCode + ", url: " + imgUrl);
            }

        } catch (IOException e) {
            throw new IllegalStateException("Exception obtaining network resource: " + imgUrl, e);
        }

        return imageItem;
    }

    private WithDecodingNeeded onAsyncRequestImage(@NonNull String imgUrl, @Nullable ImageLoadedNotifier notifier){
        WithDecodingNeeded previousItem = cachedImageItems.get(imgUrl);
        if (previousItem != null) {
            if (previousItem.isProcessing()) return previousItem;
            if (previousItem.getCachedDrawable() != null) return previousItem;
        } else {
            previousItem = new WithDecodingNeeded();
            cachedImageItems.put(imgUrl, previousItem);
        }
        final WithDecodingNeeded tempItem = previousItem;
        requestExecutor.submit(() -> doAsyncRequest(imgUrl, tempItem, notifier));
        return previousItem;
    }

    private void doAsyncRequest(@NonNull String imgUrl, WithDecodingNeeded imageItem, @Nullable ImageLoadedNotifier notifier) {
        ImageItem.WithDecodingNeeded resItem = null;
        try {
            final URL url = new URL(imgUrl);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                final String contentType = contentType(connection.getHeaderField("Content-Type"));
                final InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                resItem = ImageItem.withDecodingNeeded(contentType, inputStream);
                cachedImageItems.put(imgUrl, resItem);
            } else {
                imageItem.setIsProcessing(false);
//                throw new IOException("Bad response code: " + responseCode + ", url: " + imgUrl);
            }

        } catch (Exception e) {
            imageItem.setIsProcessing(false);
//            throw new IllegalStateException("Exception obtaining network resource: " + imgUrl, e);
        } finally {
            if (notifier != null) {
                notifier.doNotifyUI(resItem, resItem != null);
            }
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
