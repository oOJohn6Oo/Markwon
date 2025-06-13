package io.noties.markwon.ext.latex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.ImageSize;
import io.noties.markwon.image.ImageSizeResolver;

/**
 * @since 4.3.0
 */
class JLatextAsyncDrawable extends AsyncDrawable {

    private final boolean isBlock;
    private final boolean isComplete;

    JLatextAsyncDrawable(
            @NonNull String destination,
            @NonNull AsyncDrawableLoader loader,
            @NonNull ImageSizeResolver imageSizeResolver,
            @Nullable ImageSize imageSize,
            boolean isBlock,
            boolean isComplete
    ) {
        super(destination, loader, imageSizeResolver, imageSize);
        this.isBlock = isBlock;
        this.isComplete = isComplete;
    }

    public boolean isBlock() {
        return isBlock;
    }


    public boolean isComplete() {
        return isComplete;
    }
}
