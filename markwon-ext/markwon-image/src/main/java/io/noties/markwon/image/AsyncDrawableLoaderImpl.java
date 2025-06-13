package io.noties.markwon.image;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class AsyncDrawableLoaderImpl extends AsyncDrawableLoader {

    private final ExecutorService executorService;
    private final Map<String, SchemeHandler> schemeHandlers;
    private final Map<String, MediaDecoder> mediaDecoders;
    private final MediaDecoder defaultMediaDecoder;
    private final ImagesPlugin.PlaceholderProvider placeholderProvider;
    private final ImagesPlugin.ErrorHandler errorHandler;

    private final Handler handler;

    // @since 4.0.0 use a hash-map with a AsyncDrawable as key for multiple requests
    //  for the same destination
    private final Map<AsyncDrawable, Future<?>> requests = new HashMap<>(2);

    AsyncDrawableLoaderImpl(@NonNull AsyncDrawableLoaderBuilder builder) {
        this(builder, new Handler(Looper.getMainLooper()));
    }

    // @since 4.0.0
    @VisibleForTesting
    AsyncDrawableLoaderImpl(@NonNull AsyncDrawableLoaderBuilder builder, @NonNull Handler handler) {
        this.executorService = builder.executorService;
        this.schemeHandlers = builder.schemeHandlers;
        this.mediaDecoders = builder.mediaDecoders;
        this.defaultMediaDecoder = builder.defaultMediaDecoder;
        this.placeholderProvider = builder.placeholderProvider;
        this.errorHandler = builder.errorHandler;
        this.handler = handler;
    }

    @Override
    public void load(@NonNull final AsyncDrawable drawable) {
        final Future<?> future = requests.get(drawable);
        if (future == null) {
            requests.put(drawable, execute(drawable));
        }
    }

    @Override
    public void cancel(@NonNull final AsyncDrawable drawable) {

        final Future<?> future = requests.remove(drawable);
        if (future != null) {
            future.cancel(true);
        }

        handler.removeCallbacksAndMessages(drawable);
    }

    @Nullable
    @Override
    public Drawable placeholder(@NonNull AsyncDrawable drawable) {
        return placeholderProvider != null
                ? placeholderProvider.providePlaceholder(drawable)
                : null;
    }

    @NonNull
    private Future<?> execute(@NonNull final AsyncDrawable asyncDrawable) {
        return executorService.submit(() -> doLoadDrawable(asyncDrawable));
    }

    @WorkerThread
    private void doLoadDrawable(@NonNull final AsyncDrawable asyncDrawable){
        final String destination = asyncDrawable.getDestination();

        final Uri uri = Uri.parse(destination);

        Drawable drawable = null;

        boolean shouldMarkRequesting = false;

        try {

            final String scheme = uri.getScheme();
            if (scheme == null
                    || scheme.isEmpty()) {
                throw new IllegalStateException("No scheme is found: " + destination);
            }

            // obtain scheme handler
            final SchemeHandler schemeHandler = schemeHandlers.get(scheme);
            if(schemeHandler == null){
                // throw no scheme handler is available
                throw new IllegalStateException("No scheme-handler is found: " + destination);
            }

            // handle scheme
            ImageItem imageItem = schemeHandler.prefetch(destination, uri);
            if(imageItem == null){
                shouldMarkRequesting = true;
                imageItem = schemeHandler.handle(destination, uri, (item,success) -> {
                    if(item == null) return;
                    if(!success) return;
                    if(!item.hasDecodingNeeded()){
                        doLoadDrawable(asyncDrawable);
                        return;
                    }

                    final Drawable resDrawable = proceedAndDecodeImage(item.getAsWithDecodingNeeded(), destination);
                    if (resDrawable == null){
                        handler.postAtTime(() -> requests.remove(asyncDrawable), asyncDrawable, SystemClock.uptimeMillis());
                        return;
                    }

                    DrawableUtils.ensureBounds(resDrawable);
                    handler.postAtTime(() -> {
                        final Future<?> future = requests.remove(asyncDrawable);
                        if (future != null && asyncDrawable.isAttached()) {
                            asyncDrawable.setResult(resDrawable);
                        }
                    }, asyncDrawable, SystemClock.uptimeMillis());
                });
            }

            // if resulting imageItem needs further decoding -> proceed
            if (!imageItem.hasDecodingNeeded()) {
                drawable = imageItem.getAsWithResult().result();
            } else {
                final ImageItem.WithDecodingNeeded withDecodingNeeded = imageItem.getAsWithDecodingNeeded();
                drawable = withDecodingNeeded.getCachedDrawable();
            }

        } catch (Throwable t) {
            if (errorHandler != null) {
                drawable = errorHandler.handleError(destination, t);
            } else {
                // else simply log the error
                Log.e("MARKWON-IMAGE", "Error loading image: " + destination, t);
            }
        }

        final Drawable out = drawable;

        if(out != null){
            DrawableUtils.ensureBounds(out);
        }

        if(shouldMarkRequesting) return;

        handler.postAtTime(() -> {
            // validate that
            // * request was not cancelled
            // * out-result is present
            // * async-drawable is attached
            final Future<?> future = requests.remove(asyncDrawable);
            if (future != null && out != null && asyncDrawable.isAttached()) {
                asyncDrawable.setResult(out);
            }
        }, asyncDrawable, SystemClock.uptimeMillis());

    }

    /**
     * @throws IllegalStateException If Failed to decode
     */
    private Drawable proceedAndDecodeImage(@NonNull ImageItem.WithDecodingNeeded withDecodingNeeded, String destination) {
        Drawable resDrawable = null;

        InputStream imgInputStream = withDecodingNeeded.inputStream();


        // @since 4.6.2 close input stream
        try (imgInputStream) {
            if (imgInputStream == null) {
                throw new IllegalStateException("image still in requesting: " + destination);
            }
            MediaDecoder mediaDecoder = mediaDecoders.get(withDecodingNeeded.contentType());

            if (mediaDecoder == null) {
                mediaDecoder = defaultMediaDecoder;
            }

            if (mediaDecoder != null) {
                resDrawable = mediaDecoder.decode(withDecodingNeeded.contentType(), imgInputStream);
                withDecodingNeeded.setCachedDrawable(resDrawable);
            } else {
                // throw that no media decoder is found
                throw new IllegalStateException("No media-decoder is found: " + destination);
            }
        } catch (IOException e) {
            Log.e("MARKWON-IMAGE", "Error closing inputStream", e);
        }

        return resDrawable;
    }
}
