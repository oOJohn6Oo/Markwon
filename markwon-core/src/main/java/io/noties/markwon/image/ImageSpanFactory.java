package io.noties.markwon.image;

import io.noties.markwon.CacheableSpanFactory;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.RenderProps;

public class ImageSpanFactory extends CacheableSpanFactory {

    @Override
    public Object createSpan(MarkwonConfiguration configuration, RenderProps props) {
       return new AsyncDrawableSpan(
               configuration.theme(),
               new AsyncDrawable(
                       ImageProps.DESTINATION.require(props),
                       configuration.asyncDrawableLoader(),
                       configuration.imageSizeResolver(),
                       ImageProps.IMAGE_SIZE.get(props)
               ),
               AsyncDrawableSpan.ALIGN_BOTTOM,
               ImageProps.REPLACEMENT_TEXT_IS_LINK.get(props, false)
       );
    }

}
