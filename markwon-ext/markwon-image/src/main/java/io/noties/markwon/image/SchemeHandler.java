package io.noties.markwon.image;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * @since 3.0.0
 */
public abstract class SchemeHandler {

    /**
     * Changes since 4.0.0:
     * <ul>
     * <li>Returns `non-null` image-item</li>
     * </ul>
     *
     * @see ImageItem#withResult(android.graphics.drawable.Drawable)
     * @see ImageItem#withDecodingNeeded(String, java.io.InputStream)
     */
    @NonNull
    public abstract ImageItem handle(@NonNull String raw, @NonNull Uri uri, @Nullable ImageLoadedNotifier notifier);

    /**
     * @since 4.0.0
     */
    @NonNull
    public abstract Collection<String> supportedSchemes();

    /**
     * Give loader a chance to fetch Loaded ImageItem from cache without creating a {@link ImageLoadedNotifier}
     */
    public ImageItem prefetch(@NonNull String imgUrl, @NonNull Uri uri){
        return null;
    }
}
