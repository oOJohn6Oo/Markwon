package io.noties.markwon.ext.latex;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spanned;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;

import org.commonmark.parser.Parser;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.AsyncDrawableScheduler;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.image.DrawableUtils;
import io.noties.markwon.image.ImageSizeResolver;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import kotlin.jvm.Volatile;
import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * @since 3.0.0
 */
public class JLatexMathPlugin extends AbstractMarkwonPlugin {

    /**
     * @since 4.3.0
     */
    public interface ErrorHandler {

        /**
         * @param latex that caused the error
         * @param error occurred
         * @return (optional) error drawable that will be used instead (if drawable will have bounds
         * it will be used, if not intrinsic bounds will be set)
         */
        @Nullable
        Drawable handleError(@NonNull String latex, @NonNull Throwable error);
    }

    public interface BuilderConfigure {
        void configureBuilder(@NonNull Builder builder);
    }

    @NonNull
    public static JLatexMathPlugin create(float textSize) {
        return new JLatexMathPlugin(builder(textSize).build());
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin create(@Px float inlineTextSize, @Px float blockTextSize) {
        return new JLatexMathPlugin(builder(inlineTextSize, blockTextSize).build());
    }

    @NonNull
    public static JLatexMathPlugin create(@NonNull Config config) {
        return new JLatexMathPlugin(config);
    }

    @NonNull
    public static JLatexMathPlugin create(@Px float textSize, @NonNull BuilderConfigure builderConfigure) {
        final Builder builder = builder(textSize);
        builderConfigure.configureBuilder(builder);
        return new JLatexMathPlugin(builder.build());
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin create(
            @Px float inlineTextSize,
            @Px float blockTextSize,
            @NonNull BuilderConfigure builderConfigure) {
        final Builder builder = builder(inlineTextSize, blockTextSize);
        builderConfigure.configureBuilder(builder);
        return new JLatexMathPlugin(builder.build());
    }

    @NonNull
    public static JLatexMathPlugin.Builder builder(@Px float textSize) {
        return new Builder(JLatexMathTheme.builder(textSize));
    }

    /**
     * @since 4.3.0
     */
    @NonNull
    public static JLatexMathPlugin.Builder builder(@Px float inlineTextSize, @Px float blockTextSize) {
        return new Builder(JLatexMathTheme.builder(inlineTextSize, blockTextSize));
    }

    @VisibleForTesting
    static class Config {

        // @since 4.3.0
        final JLatexMathTheme theme;

        // @since 4.3.0
        final boolean blocksEnabled;
        final boolean blocksLegacy;
        final boolean inlinesEnabled;

        // @since 4.3.0
        final ErrorHandler errorHandler;

        final ExecutorService executorService;

        @LatexParseStyle
        final int inlineStyle;

        @LatexParseStyle
        final int blockStyle;

        Config(@NonNull Builder builder) {
            this.theme = builder.theme.build();
            this.blocksEnabled = builder.blocksEnabled;
            this.blocksLegacy = builder.blocksLegacy;
            this.inlinesEnabled = builder.inlinesEnabled;
            this.errorHandler = builder.errorHandler;

            this.inlineStyle = builder.inlineStyle;
            this.blockStyle = builder.blockStyle;

            // @since 4.0.0
            ExecutorService executorService = builder.executorService;
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            this.executorService = executorService;
        }
    }

    @VisibleForTesting
    final Config config;

    private final JLatexAsyncDrawableLoader jLatexAsyncDrawableLoader;
    private final JLatexBlockImageSizeResolver jLatexBlockImageSizeResolver;
    private final ImageSizeResolver inlineImageSizeResolver;

    private final HashMap<String, JLatextAsyncDrawable> cachedInlineMap = new HashMap<>();
    private final HashMap<String, JLatextAsyncDrawable> cachedBlockMap = new HashMap<>();


    @SuppressWarnings("WeakerAccess")
    JLatexMathPlugin(@NonNull Config config) {
        this.config = config;
        this.jLatexAsyncDrawableLoader = new JLatexAsyncDrawableLoader(config);
        this.jLatexBlockImageSizeResolver = new JLatexBlockImageSizeResolver(config.theme.blockFitCanvas());
        this.inlineImageSizeResolver = new InlineImageSizeResolver();
    }

    @Override
    public void configure(@NonNull Registry registry) {
        if (config.inlinesEnabled) {
            registry.require(MarkwonInlineParserPlugin.class)
                    .factoryBuilder()
                    .addInlineProcessor(new JLatexMathInlineProcessor(config.inlineStyle));
        }
    }

    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        // @since 4.3.0
        if (config.blocksEnabled) {
            if (config.blocksLegacy) {
                builder.customBlockParserFactory(new JLatexMathBlockParserLegacy.Factory());
            } else {
                builder.customBlockParserFactory(new JLatexBlockParserFactory(config.blockStyle));
            }
        }
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        addBlockVisitor(builder);
        addInlineVisitor(builder);
    }

    private void addBlockVisitor(@NonNull MarkwonVisitor.Builder builder) {
        if (!config.blocksEnabled) {
            return;
        }

        builder.on(JLatexMathBlock.class, new MarkwonVisitor.NodeVisitor<JLatexMathBlock>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull JLatexMathBlock jLatexMathBlock) {

                visitor.blockStart(jLatexMathBlock);

                final String latex = jLatexMathBlock.latex();

                final int length = visitor.length();

                // @since 4.0.2 we cannot append _raw_ latex as a placeholder-text,
                // because Android will draw formula for each line of text, thus
                // leading to formula duplicated (drawn on each line of text)
                visitor.builder().append(prepareLatexTextPlaceholder(latex));

                final MarkwonConfiguration configuration = visitor.configuration();

                JLatextAsyncDrawable drawable = null;
                boolean isBlockComplete = jLatexMathBlock.isComplete();

                if (isBlockComplete && cachedBlockMap.containsKey(latex)){
                    drawable = cachedBlockMap.get(latex);
                }
                if (drawable == null) {
                    drawable = new JLatextAsyncDrawable(
                            latex,
                            jLatexAsyncDrawableLoader,
                            jLatexBlockImageSizeResolver,
                            null,
                            true,
                            isBlockComplete
                            );
                    if(isBlockComplete){
                        cachedBlockMap.put(latex, drawable);
                    }
                }


                final AsyncDrawableSpan span = new JLatexAsyncDrawableSpan(
                        configuration.theme(),
                        drawable,
                        config.theme.blockTextColor()
                );

                visitor.setSpans(length, span);

                visitor.blockEnd(jLatexMathBlock);
            }
        });
    }

    private void addInlineVisitor(@NonNull MarkwonVisitor.Builder builder) {

        if (!config.inlinesEnabled) {
            return;
        }

        builder.on(JLatexMathNode.class, new MarkwonVisitor.NodeVisitor<JLatexMathNode>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull JLatexMathNode jLatexMathNode) {
                final String latex = jLatexMathNode.latex();

                final int length = visitor.length();

                // @since 4.0.2 we cannot append _raw_ latex as a placeholder-text,
                // because Android will draw formula for each line of text, thus
                // leading to formula duplicated (drawn on each line of text)
                visitor.builder().append(prepareLatexTextPlaceholder(latex));

                final MarkwonConfiguration configuration = visitor.configuration();
                JLatextAsyncDrawable drawable = null;
                if(cachedInlineMap.containsKey(latex)){
                    drawable = cachedInlineMap.get(latex);
                }
                if(drawable == null){
                    drawable = new JLatextAsyncDrawable(
                            latex,
                            jLatexAsyncDrawableLoader,
                            inlineImageSizeResolver,
                            null,
                            false,
                            true);
                    cachedInlineMap.put(latex, drawable);
                }
                final AsyncDrawableSpan span = new JLatexInlineAsyncDrawableSpan(
                        configuration.theme(),
                        drawable,
                        config.theme.inlineTextColor()
                );

                visitor.setSpans(length, span);
            }
        });
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        AsyncDrawableScheduler.unschedule(textView);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        AsyncDrawableScheduler.schedule(textView);
    }

    // @since 4.0.2
    @VisibleForTesting
    @NonNull
    static String prepareLatexTextPlaceholder(@NonNull String latex) {
        return latex.replace('\n', ' ').trim();
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static class Builder {

        // @since 4.3.0
        private final JLatexMathTheme.Builder theme;

        // @since 4.3.0
        private boolean blocksEnabled = true;
        private boolean blocksLegacy;
        private boolean inlinesEnabled;

        // @since 4.3.0
        private ErrorHandler errorHandler;

        // @since 4.0.0
        private ExecutorService executorService;

        @LatexParseStyle
        private int inlineStyle = LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS;
        @LatexParseStyle
        private int blockStyle = LatexParseStyle.STYLE_SLASH_SQUARE_BRACKETS;

        Builder(@NonNull JLatexMathTheme.Builder builder) {
            this.theme = builder;
        }

        @NonNull
        public JLatexMathTheme.Builder theme() {
            return theme;
        }

        /**
         * @since 4.3.0
         */
        @NonNull
        public Builder blocksEnabled(boolean blocksEnabled) {
            this.blocksEnabled = blocksEnabled;
            return this;
        }

        /**
         * @param blocksLegacy indicates if blocks should be handled in legacy mode ({@code pre 4.3.0})
         * @since 4.3.0
         */
        @NonNull
        public Builder blocksLegacy(boolean blocksLegacy) {
            this.blocksLegacy = blocksLegacy;
            return this;
        }

        /**
         * @param inlinesEnabled indicates if inline parsing should be enabled.
         *                       NB, this requires `MarkwonInlineParserPlugin` to be used when creating `MarkwonInstance`
         * @since 4.3.0
         */
        @NonNull
        public Builder inlinesEnabled(boolean inlinesEnabled) {
            this.inlinesEnabled = inlinesEnabled;
            return this;
        }

        public Builder inlineStyle(@LatexParseStyle int style) {
            this.inlineStyle = style;
            return this;
        }

        public Builder blockStyle(@LatexParseStyle int style) {
            this.blockStyle = style;
            return this;
        }

        @NonNull
        public Builder errorHandler(@Nullable ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * @since 4.0.0
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        public Builder executorService(@NonNull ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        @NonNull
        public Config build() {
            return new Config(this);
        }
    }

    // @since 4.0.0
    static class JLatexAsyncDrawableLoader extends AsyncDrawableLoader {
        private final Config config;
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Volatile
        private final Map<AsyncDrawable, Future<?>> cache = new HashMap<>(3);

        JLatexAsyncDrawableLoader(@NonNull Config config) {
            this.config = config;
        }

        @Override
        public void load(@NonNull final AsyncDrawable drawable) {

            // this method must be called from main-thread only (thus synchronization can be skipped)

            // check for currently running tasks associated with provided drawable
            final Future<?> future = cache.get(drawable);

            boolean isLatex = drawable instanceof JLatextAsyncDrawable;
            if(!isLatex) return;

            if (!((JLatextAsyncDrawable) drawable).isComplete()) {
                return;
            }

            final JLatextAsyncDrawable tempDrawable = (JLatextAsyncDrawable) drawable;

            // if it's present -> proceed with new execution
            // as asyncDrawable is immutable, it won't have destination changed (so there is no need
            // to cancel any started tasks)
            if (future == null) {
                cache.put(tempDrawable, config.executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        // @since 4.0.1 wrap in try-catch block and add error logging
                        try {
                            final JLatexMathDrawable jLatexMathDrawable;

                            if (tempDrawable.isBlock()) {
                                jLatexMathDrawable = createBlockDrawable(tempDrawable);
                            } else {
                                jLatexMathDrawable = createInlineDrawable(tempDrawable);
                            }

                            setResult(tempDrawable, jLatexMathDrawable);
                        } catch (Throwable t) {
                            // @since 4.3.0 add error handling
                            final ErrorHandler errorHandler = config.errorHandler;
                            if (errorHandler == null) {
                                // as before
                                Log.e(
                                        "JLatexMathPlugin",
                                        "Error displaying latex: `" + tempDrawable.getDestination() + "`",
                                        t);
                            } else {
                                // just call `getDestination` without casts and checks
                                final Drawable errorDrawable = errorHandler.handleError(
                                        tempDrawable.getDestination(),
                                        t
                                );
                                if (errorDrawable != null) {
                                    DrawableUtils.applyIntrinsicBoundsIfEmpty(errorDrawable);
                                    setResult(tempDrawable, errorDrawable);
                                }
                            }
                        }
                    }

                }));
            }
        }

        @Override
        public void cancel(@NonNull AsyncDrawable drawable) {

            // this method also must be called from main thread only

            final Future<?> future = cache.remove(drawable);
            if (future != null) {
                future.cancel(true);
            }

            // remove all callbacks (via runnable) and messages posted for this drawable
            handler.removeCallbacksAndMessages(drawable);
        }

        @Nullable
        @Override
        public Drawable placeholder(@NonNull AsyncDrawable drawable) {
            return null;
        }

        // @since 4.3.0
        @NonNull
        private JLatexMathDrawable createBlockDrawable(@NonNull JLatextAsyncDrawable drawable) {

            final String latex = drawable.getDestination();

            final JLatexMathTheme theme = config.theme;

            final JLatexMathTheme.BackgroundProvider backgroundProvider = theme.blockBackgroundProvider();
            final JLatexMathTheme.Padding padding = theme.blockPadding();
            final int color = theme.blockTextColor();

            final JLatexMathDrawable.Builder builder = JLatexMathDrawable.builder(latex)
                    .textSize(theme.blockTextSize())
                    .align(theme.blockHorizontalAlignment());

            if (backgroundProvider != null) {
                builder.background(backgroundProvider.provide());
            }

            if (padding != null) {
                builder.padding(padding.left, padding.top, padding.right, padding.bottom);
            }

            if (color != 0) {
                builder.color(color);
            }

            return builder.build();
        }

        // @since 4.3.0
        @NonNull
        private JLatexMathDrawable createInlineDrawable(@NonNull JLatextAsyncDrawable drawable) {

            final String latex = drawable.getDestination();

            final JLatexMathTheme theme = config.theme;

            final JLatexMathTheme.BackgroundProvider backgroundProvider = theme.inlineBackgroundProvider();
            final JLatexMathTheme.Padding padding = theme.inlinePadding();
            final int color = theme.inlineTextColor();

            final JLatexMathDrawable.Builder builder = JLatexMathDrawable.builder(latex)
                    .textSize(theme.inlineTextSize());

            if (backgroundProvider != null) {
                builder.background(backgroundProvider.provide());
            }

            if (padding != null) {
                builder.padding(padding.left, padding.top, padding.right, padding.bottom);
            }

            if (color != 0) {
                builder.color(color);
            }

            return builder.build();
        }

        // @since 4.3.0
        private void setResult(@NonNull final AsyncDrawable drawable, @NonNull final Drawable result) {
            // we must post to handler, but also have a way to identify the drawable
            // for which we are posting (in case of cancellation)
            handler.postAtTime(new Runnable() {
                @Override
                public void run() {
                    // remove entry from cache (it will be present if task is not cancelled)
                    if (cache.remove(drawable) != null
                            && drawable.isAttached()) {
                        drawable.setResult(result);
                    }

                }
            }, drawable, SystemClock.uptimeMillis());
        }
    }

    private static class InlineImageSizeResolver extends ImageSizeResolver {

        @NonNull
        @Override
        public Rect resolveImageSize(@NonNull AsyncDrawable drawable) {

            // @since 4.4.0 resolve inline size (scale down if exceed available width)
            final Rect imageBounds = drawable.getResult().getBounds();
            final int canvasWidth = drawable.getLastKnownCanvasWidth();
            final int w = imageBounds.width();

            if (w > canvasWidth) {
                // here we must scale it down (keeping the ratio)
                final float ratio = (float) w / imageBounds.height();
                final int h = (int) (canvasWidth / ratio + .5F);
                return new Rect(0, 0, canvasWidth, h);
            }

            return imageBounds;
        }
    }
}
