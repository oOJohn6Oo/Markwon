package io.noties.markwon.app.samples.sse

import android.annotation.SuppressLint
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.ViewCompat
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.app.R
import io.noties.markwon.app.databinding.SampleSseCommonBinding
import io.noties.markwon.app.readme.GrammarLocatorDef
import io.noties.markwon.app.readme.ReadMeImageDestinationPlugin
import io.noties.markwon.app.readme.ReadMeLinkResolver
import io.noties.markwon.app.sample.ui.MarkwonTextViewSample
import io.noties.markwon.app.utils.loadSseLatex
import io.noties.markwon.app.utils.noOpDelegate
import io.noties.markwon.app.utils.safeDrawing
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.recycler.table.TableEntryPlugin
import io.noties.markwon.sample.annotations.MarkwonArtifact
import io.noties.markwon.sample.annotations.MarkwonSampleInfo
import io.noties.markwon.sample.annotations.Tag
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.node.FencedCodeBlock

@Suppress("unused")
@MarkwonSampleInfo(
    id = "20250609030068",
    title = "SSE Latex",
    description = "Display Latex + Markdown in SSE style",
    artifacts = [MarkwonArtifact.SSE],
    tags = [Tag.rendering]
)
class SseLatexSample : MarkwonTextViewSample() {
    private var _mBinding: SampleSseCommonBinding? = null
    val mBinding: SampleSseCommonBinding
        get() = _mBinding!!

    private var isSseFinished = false
    private var fullLatexStr: String = ""
    private var enableAutoScroll = true

    /**
     * Character / 100 Millis
     */
    @Volatile
    private var currentSpeed: Int = 20
    private val successMap = mutableMapOf<String, Boolean>()

    override val layoutResId: Int
        get() = R.layout.sample_sse_common

    override fun render() {
    }

    private lateinit var markwon: Markwon

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        _mBinding = SampleSseCommonBinding.bind(view)
        initMarkwon(view)
        setupEdge2Edge()
        loadSSEData()

        setupSpeedAndSeeker()
    }

    private fun setupSpeedAndSeeker() {
        mBinding.switcherFgSse.displayedChild = 0
        mBinding.btnCollapseFgSse.setOnClickListener {
            mBinding.switcherFgSse.apply {
                inAnimation = slideInFromLeft()
                outAnimation = slideOutToRight()
                displayedChild = 0
            }
            mBinding.switcherFgSse.displayedChild = 1
        }
        mBinding.btnExpandFgSse.setOnClickListener{
            mBinding.switcherFgSse.apply {
                inAnimation = slideInFromRight()
                outAnimation = slideOutToLeft()
                displayedChild = 0
            }
        }
        mBinding.seekBarFgSse.setOnSeekBarChangeListener(object : OnSeekBarChangeListener by noOpDelegate() {
            override fun onProgressChanged(seekbar: SeekBar, progress: Int, p2: Boolean) {
                val desireProgress = progress + 1
                currentSpeed = desireProgress
                mBinding.titleSpeedFgSse.text = "Speed: $desireProgress"
                mBinding.btnExpandFgSse.text = desireProgress.toString()
            }
        })
        mBinding.seekBarFgSse.progress = currentSpeed - 1
    }

    private fun initMarkwon(view: View) {
        markwon = Markwon.builder(view.context)
            .usePlugin(ImagesPlugin.create(true).apply {
                setOnImageRequestListener { imgUrl, success ->
                    if (!success) return@setOnImageRequestListener
                    if (successMap.contains(imgUrl)) return@setOnImageRequestListener
                    successMap[imgUrl] = true
                    if (isSseFinished) {
                        refreshMarkwon()
                    }
                }
            })
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TableEntryPlugin.create(view.context))
            .usePlugin(
                SyntaxHighlightPlugin.create(
                    Prism4j(GrammarLocatorDef()),
                    Prism4jThemeDefault.create(0)
                )
            )
            .usePlugin(TaskListPlugin.create(view.context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(mBinding.textView.textSize) { builder ->
                builder.inlinesEnabled(
                    true
                )
            })
            .usePlugin(ReadMeImageDestinationPlugin(null))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(FencedCodeBlock::class.java) { visitor, block ->
                        // we actually won't be applying code spans here, as our custom view will
                        // draw background and apply mono typeface
                        //
                        // NB the `trim` operation on literal (as code will have a new line at the end)
                        val code = visitor.configuration()
                            .syntaxHighlight()
                            .highlight(block.info, block.literal.trim())
                        visitor.builder().append(code)
                    }
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(ReadMeLinkResolver())
                }
            })
            .build()
    }

    private fun setupEdge2Edge() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val safeDrawing = insets.safeDrawing(false)
            mBinding.scrollView.updatePaddingRelative(start = safeDrawing.left, end = safeDrawing.right)
            mBinding.seekContainerFgSse.updatePaddingRelative(start = safeDrawing.left, end = safeDrawing.right)
            mBinding.speedBtnContainerFgSse.updatePaddingRelative(end = safeDrawing.right)
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadSSEData() {
        mBinding.scrollView.setOnTouchListener { _, _ ->
            enableAutoScroll = false
            false
        }

        fragment.lifecycleScope.launch(Dispatchers.Main) {
            fullLatexStr = loadSseLatex(fragment.requireContext())
            val totalLength = fullLatexStr.length
            var currentLength = 0
            flow<String> {
                isSseFinished = false
                while (this@launch.isActive && currentLength < totalLength) {
                    currentLength = minOf(currentLength + currentSpeed, totalLength)
                    emit(fullLatexStr.take(currentLength))
                    delay(100)
                    isSseFinished = false
                }
                isSseFinished = true
            }.map { markwon.render(markwon.parse(it)) }
                .flowOn(Dispatchers.IO)
                .catch { }
                .collect {
                    markwon.setParsedMarkdown(mBinding.textView, it)
                    if (enableAutoScroll) {
                        mBinding.scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
        }
    }


    private fun refreshMarkwon(){
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val node = withContext(Dispatchers.IO){
                markwon.render(markwon.parse(fullLatexStr))
            }
            markwon.setParsedMarkdown(textView, node)
        }
    }

    private fun slideInFromLeft(): Animation {
        val slide = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, -0.5f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        )
        val fade = AlphaAnimation(0f, 1f)

        return AnimationSet(true).apply {
            addAnimation(slide)
            addAnimation(fade)
            duration = 200
        }
    }

    private fun slideInFromRight(): Animation {
        val slide = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.5f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        )
        val fade = AlphaAnimation(0f, 1f)

        return AnimationSet(true).apply {
            addAnimation(slide)
            addAnimation(fade)
            duration = 200
        }
    }

    private fun slideOutToLeft(): Animation {
        val slide = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, -0.5f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        )
        val fade = AlphaAnimation(1f, 0f)

        return AnimationSet(true).apply {
            addAnimation(slide)
            addAnimation(fade)
            duration = 200
        }
    }

    private fun slideOutToRight(): Animation {
        val slide = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0.5f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        )
        val fade = AlphaAnimation(1f, 0f)

        return AnimationSet(true).apply {
            addAnimation(slide)
            addAnimation(fade)
            duration = 200
        }
    }
}