package io.noties.markwon.image;

import androidx.annotation.Nullable;

public interface ImageLoadedNotifier {
    void doNotifyUI(final @Nullable ImageItem imageItem, boolean success);
}