package io.noties.markwon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import io.noties.markwon.core.CoreProps;

public abstract class CacheableSpanFactory implements SpanFactory{
    final HashMap<Integer, Object> spanCacheMap = new HashMap<>();

    public abstract Object createSpan(MarkwonConfiguration configuration, RenderProps props);

    @Nullable
    @Override
    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
        Integer key =  CoreProps.HEADING_LEVEL.get(props);
        Object span = spanCacheMap.get(key);
        if(span == null){
            span = createSpan(configuration, props);
            spanCacheMap.put(key, span);
        }
        return span;
    }
}