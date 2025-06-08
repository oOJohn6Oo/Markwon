package io.noties.markwon.core.factory;

import io.noties.markwon.CacheableSpanFactory;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.spans.HeadingSpan;

public class HeadingSpanFactory extends CacheableSpanFactory {
    @Override
    public Object createSpan(MarkwonConfiguration configuration, RenderProps props) {
        return new HeadingSpan(
                configuration.theme(),
                CoreProps.HEADING_LEVEL.require(props)
        );
    }

}
