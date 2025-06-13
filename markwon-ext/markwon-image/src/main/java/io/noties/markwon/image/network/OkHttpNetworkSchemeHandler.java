package io.noties.markwon.image.network;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.ImageLoadedNotifier;
import io.noties.markwon.image.SchemeHandler;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @since 4.0.0
 */
public class OkHttpNetworkSchemeHandler extends SchemeHandler {

    private final HashMap<String, ImageItem.WithDecodingNeeded> imgPathMap = new HashMap<>();

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    // @since 4.0.0, previously just OkHttpClient
    private final Call.Factory factory;

    public OkHttpNetworkSchemeHandler(){
        this(false, new OkHttpClient());
    }

    public OkHttpNetworkSchemeHandler(boolean shouldHandleAsync){
        this(shouldHandleAsync, new OkHttpClient());
    }


    public <T extends Call.Factory> OkHttpNetworkSchemeHandler(@NonNull T factory){
        this(false, factory);
    }

    public <T extends Call.Factory> OkHttpNetworkSchemeHandler(boolean shouldHandleAsync, @NonNull T factory) {
        this.shouldHandleAsync = shouldHandleAsync;
        this.factory = factory;
    }

    @Override
    public ImageItem prefetch(@NonNull String imgUrl, @NonNull Uri uri) {
        return imgPathMap.get(imgUrl);
    }

    @NonNull
    @Override
    public ImageItem handle(@NonNull String raw, @NonNull Uri uri,@Nullable ImageLoadedNotifier notifier) {
        if(shouldHandleAsync){
            return doAsyncRequest(raw, notifier);
        }else{
            return doSyncRequest(raw);
        }
    }

    private ImageItem.WithDecodingNeeded doAsyncRequest(String imgUrl,@Nullable ImageLoadedNotifier notifier){
        ImageItem.WithDecodingNeeded previousItem = imgPathMap.get(imgUrl);
        if (previousItem != null) {
            if (previousItem.isProcessing()) return previousItem;
            if (previousItem.getCachedDrawable() != null) return previousItem;
        } else {
            previousItem = new ImageItem.WithDecodingNeeded();
            imgPathMap.put(imgUrl, previousItem);
        }

        final ImageItem.WithDecodingNeeded tempItem = previousItem;

        final Request request = new Request.Builder()
                .url(imgUrl)
                .tag(imgUrl)
                .build();
        factory.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                tempItem.setIsProcessing(false);
                if (notifier != null) {
                    notifier.doNotifyUI(tempItem, false);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final ResponseBody body = response.body();
                final InputStream inputStream = body != null
                        ? body.byteStream()
                        : null;

                if(inputStream != null){
                    // important to process content-type as it can have encoding specified (which we should remove)
                    final String contentType =
                            NetworkSchemeHandler.contentType(response.header(HEADER_CONTENT_TYPE));
                    imgPathMap.put(imgUrl, ImageItem.withDecodingNeeded(contentType, inputStream));
                }else{
                    tempItem.setIsProcessing(false);
                }
                if (notifier != null) {
                    notifier.doNotifyUI(tempItem, tempItem.inputStream() != null);
                }
            }
        });
        return previousItem;
    }

    private ImageItem.WithDecodingNeeded doSyncRequest(String imgUrl){
        final Request request = new Request.Builder()
                .url(imgUrl)
                .tag(imgUrl)
                .build();

        try(Response response = factory.newCall(request).execute()) {
            final ResponseBody body = response.body();
            final InputStream inputStream = body != null
                    ? body.byteStream()
                    : null;

            if (inputStream == null) {
                throw new IllegalStateException("Response does not contain body: " + imgUrl);
            }

            // important to process content-type as it can have encoding specified (which we should remove)
            final String contentType =
                    NetworkSchemeHandler.contentType(response.header(HEADER_CONTENT_TYPE));

            return ImageItem.withDecodingNeeded(contentType, inputStream);
        } catch (Throwable t) {
            throw new IllegalStateException("Exception obtaining network resource: " + imgUrl, t);
        }

    }

    @NonNull
    @Override
    public Collection<String> supportedSchemes() {
        return Arrays.asList(
                NetworkSchemeHandler.SCHEME_HTTP,
                NetworkSchemeHandler.SCHEME_HTTPS);
    }
}
