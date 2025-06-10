package io.noties.markwon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import io.noties.markwon.core.CoreProps;
import io.noties.markwon.image.ImageProps;

public abstract class CacheableSpanFactory implements SpanFactory{
    final HashMap<String, Object> spanCacheMap = new HashMap<>();

    public abstract Object createSpan(MarkwonConfiguration configuration, RenderProps props);

    @Nullable
    @Override
    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
        Integer level =  CoreProps.HEADING_LEVEL.get(props);
        String destination = ImageProps.DESTINATION.get(props);

        String finalKey = destination + "_" + level + "_cached_span";
        Object span = spanCacheMap.get(finalKey);
        if(span == null){
            span = createSpan(configuration, props);
            spanCacheMap.put(finalKey, span);
        }
        return span;
    }
}