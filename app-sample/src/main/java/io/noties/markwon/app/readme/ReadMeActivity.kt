package io.noties.markwon.app.readme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import io.noties.debug.Debug
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.app.R
import io.noties.markwon.app.databinding.ActivityReadMeBinding
import io.noties.markwon.app.utils.ReadMeUtils
import io.noties.markwon.app.utils.loadReadMe
import io.noties.markwon.app.utils.noOpDelegate
import io.noties.markwon.app.utils.safeDrawing
import io.noties.markwon.app.utils.textOrHide
import io.noties.markwon.app.utils.vdp
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin.BuilderConfigure
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.SimpleEntry
import io.noties.markwon.recycler.table.TableEntry
import io.noties.markwon.recycler.table.TableEntryPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.FencedCodeBlock
import java.io.IOException

@PrismBundle(includeAll = true)
class ReadMeActivity : FragmentActivity() {

    private lateinit var mBinding: ActivityReadMeBinding
    private lateinit var mAdapter:MarkwonAdapter

    private val renderScope = MainScope() + SupervisorJob()

    /**
     * Character / 100 Millis
     */
    @Volatile
    private var currentSpeed: Int = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mBinding = ActivityReadMeBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        val data = intent.data

        Debug.i(data)

        initAppBar(data)

        if (intent.getBooleanExtra("sseStyle", false)){
            loadSSEData()
        }else{
            initRecyclerView(data)
        }

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { _, insets ->
            val safeDrawing = insets.safeDrawing(false)
            mBinding.appBar.updatePaddingRelative(
                top = safeDrawing.top,
                start = safeDrawing.left,
                end = safeDrawing.right,
            )
            mBinding.recyclerView.updatePaddingRelative(
                bottom = safeDrawing.bottom,
                start = safeDrawing.left,
                end = safeDrawing.right,
            )
            mBinding.sseMarkwon.updatePaddingRelative(
                bottom = safeDrawing.bottom,
                start = safeDrawing.left + 16.vdp,
                end = safeDrawing.right + 16.vdp,
            )
            insets
        }
    }

    private val markwon: Markwon
        get() = Markwon.builder(this)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TableEntryPlugin.create(this))
                .usePlugin(SyntaxHighlightPlugin.create(Prism4j(GrammarLocatorDef()), Prism4jThemeDefault.create(0)))
                .usePlugin(TaskListPlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(JLatexMathPlugin.create(mBinding.sseMarkwon.textSize) { builder -> builder.inlinesEnabled(true) })
                .usePlugin(ReadMeImageDestinationPlugin(intent.data))
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

    private fun initAppBar(data: Uri?) {
        val appBar = findViewById<View>(R.id.app_bar)
        appBar.findViewById<View>(R.id.app_bar_icon).setOnClickListener {
            finish()
        }

        val (title: String, subtitle: String?) = if (data == null) {
            Pair("README.md", null)
        } else {
            Pair(data.lastPathSegment ?: "", data.toString())
        }

        appBar.findViewById<TextView>(R.id.title).text = title
        appBar.findViewById<TextView>(R.id.subtitle).textOrHide(subtitle)
    }

    private fun initRecyclerView(data: Uri?) {

        mAdapter = MarkwonAdapter.builder(R.layout.adapter_node, R.id.text_view)
                .include(FencedCodeBlock::class.java, SimpleEntry.create(R.layout.adapter_node_code_block, R.id.text_view))
                .include(TableBlock::class.java, TableEntry.create {
                    it
                            .tableLayout(R.layout.adapter_node_table_block, R.id.table_layout)
                            .textLayoutIsRoot(R.layout.view_table_entry_cell)
                })
                .build()

        mBinding.recyclerView.apply {
            setHasFixedSize(true)
            itemAnimator = DefaultItemAnimator()
            adapter = mAdapter
        }

        loadData(data)
    }

    private fun loadSSEData() {
        mBinding.seekBarContainer.isVisible = true
        mBinding.scrollView.isVisible = true
        mBinding.recyclerView.isVisible = false
        mBinding.progressBar.isVisible = false

        mBinding.seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener by noOpDelegate(){
            override fun onProgressChanged(seekbar: SeekBar, progress: Int, p2: Boolean) {
                val desireProgress = progress + 1
                currentSpeed = desireProgress
                mBinding.seekSpeed.text = "Speed: $desireProgress"
            }
        })
        mBinding.seekBar.progress = currentSpeed - 1

        renderScope.launch(Dispatchers.Main) {
            val readmeStr = loadReadMe(this@ReadMeActivity)
            val totalLength = readmeStr.length
            var currentLength = 0
            flow<String> {
                while (this@launch.isActive &&  currentLength < totalLength){
                    currentLength = minOf(currentLength + currentSpeed, totalLength)
                    emit(readmeStr.take(currentLength))
                    delay(100)
                }
            }.map { markwon.render(markwon.parse(it)) }
                .flowOn(Dispatchers.IO)
                .catch {  }
                .collect{
                markwon.setParsedMarkdown(mBinding.sseMarkwon, it)
            }
        }
    }

    private fun loadData(data: Uri?) {
        mBinding.seekBarContainer.isVisible = false
        mBinding.scrollView.isVisible = false
        mBinding.recyclerView.isVisible = true
        load(applicationContext, data) { result ->
            when (result) {
                is Result.Failure -> Debug.e(result.throwable)
                is Result.Success -> {
                    val markwon = markwon
                    val node = markwon.parse(result.markdown)
                    if (window != null) {
                        mBinding.recyclerView.post {
                            mAdapter.setParsedMarkdown(markwon, node)
                            mAdapter.notifyDataSetChanged()
                            mBinding.progressBar.isVisible = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderScope.cancel()
    }

    private sealed class Result {
        data class Success(val markdown: String) : Result()
        data class Failure(val throwable: Throwable) : Result()
    }

    companion object {
        fun makeIntent(context: Context, sseStyle: Boolean): Intent {
            return Intent(context, ReadMeActivity::class.java).apply {
                putExtra("sseStyle", sseStyle)
            }
        }

        private fun load(context: Context, data: Uri?, callback: (Result) -> Unit) = try {

            if (data == null) {
                callback.invoke(Result.Success(loadReadMe(context)))
            } else {
                val request = Request.Builder()
                        .get()
                        .url(ReadMeUtils.buildRawGithubUrl(data))
                        .build()
                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.invoke(Result.Failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val md = response.body?.string() ?: ""
                        callback.invoke(Result.Success(md))
                    }
                })
            }

        } catch (t: Throwable) {
            callback.invoke(Result.Failure(t))
        }
    }
}