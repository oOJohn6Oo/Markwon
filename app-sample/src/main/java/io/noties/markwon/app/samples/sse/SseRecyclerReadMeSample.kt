package io.noties.markwon.app.samples.sse

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.noties.markwon.*
import io.noties.markwon.app.R
import io.noties.markwon.app.databinding.SampleSseCommonBinding
import io.noties.markwon.app.readme.*
import io.noties.markwon.app.sample.ui.MarkwonSample
import io.noties.markwon.app.utils.*
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.SimpleEntry
import io.noties.markwon.recycler.table.TableEntry
import io.noties.markwon.recycler.table.TableEntryPlugin
import io.noties.markwon.sample.annotations.*
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.FencedCodeBlock

@Suppress("unused")
@MarkwonSampleInfo(
    id = "20250612030070",
    title = "SSE Recycler README",
    description = "Display README in SSE style Using RecyclerView",
    artifacts = [MarkwonArtifact.SSE, MarkwonArtifact.RECYCLER],
    tags = [Tag.rendering]
)
class SseRecyclerReadMeSample : MarkwonSample() {
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

    private lateinit var markwon: Markwon

    override fun onViewCreated(view: View) {
        _mBinding = SampleSseCommonBinding.bind(view)
        mBinding.scrollView.isVisible = false
        mBinding.recyclerView.isVisible = true
        mBinding.setupEdge2Edge()
        mBinding.setupSpeedAndSeeker(currentSpeed){
            currentSpeed = it
        }
        initMarkwon(view)
        loadSSEData()

    }

    private fun initMarkwon(view: View) {
        markwon = Markwon.builder(view.context)
            .usePlugin(ImagesPlugin.create(true))
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
                builder.inlinesEnabled(true)
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


        val adapter = MarkwonAdapter.builderTextViewIsRoot(R.layout.adapter_node)
            .include(
                FencedCodeBlock::class.java,
                SimpleEntry.create(R.layout.adapter_node_code_block, R.id.text_view)
            )
            .include(
                TableBlock::class.java,
                TableEntry.create { builder: TableEntry.Builder ->
                    builder
                        .tableLayout(R.layout.adapter_node_table_block, R.id.table_layout)
                        .textLayoutIsRoot(R.layout.view_table_entry_cell)
                }
            )
            .build()
        mBinding.recyclerView.itemAnimator = null
        mBinding.recyclerView.setAdapter(adapter)
    }

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    private fun loadSSEData() {
        mBinding.recyclerView.setOnTouchListener { _, _ ->
            enableAutoScroll = false
            false
        }

        fragment.lifecycleScope.launch(Dispatchers.Main) {
            fullLatexStr = loadReadMe(fragment.requireContext())
            val totalLength = fullLatexStr.length
            var currentLength = 0
            flow {
                isSseFinished = false
                while (this@launch.isActive && currentLength < totalLength) {
                    currentLength = minOf(currentLength + currentSpeed, totalLength)
                    emit(fullLatexStr.take(currentLength))
                    delay(100)
                    isSseFinished = false
                }
                isSseFinished = true
            }.map { markwon.parse(it) }
                .flowOn(Dispatchers.IO)
                .catch { }
                .collect {
                    (mBinding.recyclerView.adapter as MarkwonAdapter).apply {
                        setParsedMarkdown(markwon, it)
                        notifyDataSetChanged()
                        if (enableAutoScroll) {
                            mBinding.recyclerView.smoothScrollToPosition(this.itemCount)
                        }
                    }
                }
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun refreshMarkwon(){
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val node = withContext(Dispatchers.IO){
                markwon.parse(fullLatexStr)
            }

            (mBinding.recyclerView.adapter as MarkwonAdapter).apply {
                setParsedMarkdown(markwon, node)
                notifyDataSetChanged()
            }
        }
    }

}