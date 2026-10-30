package com.vsmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream

class TerminalActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var prompt: TextView
    private lateinit var sendBtn: ImageView

    private val out = SpannableStringBuilder()
    private val ui = Handler(Looper.getMainLooper())
    private var renderPending = false

    private var cwd = File("/sdcard")
    private val cmdHistory = mutableListOf<String>()
    private var histIdx = 0
    @Volatile private var process: Process? = null
    private var useShizuku = false

    private val cText = 0xFFD4D4D8.toInt()
    private val cErr = 0xFFFF6B6B.toInt()
    private val cCmd = 0xFF7FD67F.toInt()
    private val cDim = 0xFF8A8A93.toInt()

    private val ansi = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")
    private val danger = Regex("""\brm\s+-\w*[rf]|\bmkfs|\bdd\s+if=|\bformat\b|\buninstall\b|\bpm\s+clear\b|\bwipe\b""")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(pathIcon(Icons.BACK))
        toolbar.title = "Terminal"

        output = findViewById(R.id.outputText)
        scroll = findViewById(R.id.scrollView)
        input = findViewById(R.id.commandInput)
        prompt = findViewById(R.id.promptText)
        sendBtn = findViewById(R.id.sendButton)

        intent.getStringExtra("dir")?.let { cwd = File(it) }
        useShizuku = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) { false }

        append("VSManager Terminal\n", Color.WHITE)
        append(
            if (useShizuku) "Executando via Shizuku (acesso elevado)\n"
            else "Shizuku indisponivel: usando o shell do app\n", cDim
        )
        append("Dica: 'clear' limpa a tela, 'exit' fecha.\n\n", cDim)
        updatePrompt()
        updateSendIcon()

        sendBtn.setOnClickListener { if (process != null) stopProcess() else submit() }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
        }
        setupQuickKeys()
        input.requestFocus()
    }

    override fun onDestroy() {
        process?.destroy()
        super.onDestroy()
    }

    private fun append(text: CharSequence, color: Int) {
        ui.post {
            val start = out.length
            out.append(text)
            out.setSpan(ForegroundColorSpan(color), start, out.length, 0)
            if (out.length > 120_000) out.delete(0, 30_000)
            if (!renderPending) { renderPending = true; ui.postDelayed(render, 60) }
        }
    }

    private val render = Runnable {
        renderPending = false
        output.text = out
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updatePrompt() {
        prompt.text = (if (cwd.parent == null) "/" else cwd.name) + " $"
        toolbar.subtitle = cwd.absolutePath
    }

    private fun updateSendIcon() {
        sendBtn.setImageDrawable(pathIcon(if (process != null) Icons.STOP else Icons.SEND))
    }

    private fun submit() {
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        input.setText("")
        if (cmdHistory.lastOrNull() != cmd) cmdHistory.add(cmd)
        histIdx = cmdHistory.size

        if (danger.containsMatchIn(cmd)) {
            MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setTitle("Comando perigoso")
                .setMessage("$cmd\n\nTem certeza?")
                .setPositiveButton("Executar") { _, _ -> run(cmd) }
                .setNegativeButton("Cancelar", null).show()
        } else run(cmd)
    }

    private fun startProcess(cmd: String): Process {
        val full = "cd \"${cwd.absolutePath}\" 2>/dev/null; $cmd"
        val args = arrayOf("sh", "-c", full)
        return if (useShizuku) {
            val m = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            m.invoke(null, args, null, null) as Process
        } else {
            Runtime.getRuntime().exec(args)
        }
    }

    private fun pipe(stream: InputStream, color: Int) {
        val reader = stream.bufferedReader()
        val buf = CharArray(4096)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            append(ansi.replace(String(buf, 0, n), ""), color)
        }
    }

    private fun run(cmd: String) {
        append("${prompt.text} $cmd\n", cCmd)

        when {
            cmd == "clear" -> { out.clear(); output.text = ""; return }
            cmd == "exit" -> { finish(); return }
            cmd == "cd" || cmd.startsWith("cd ") -> { changeDir(cmd.removePrefix("cd").trim()); return }
        }

        Thread {
            try {
                val p = startProcess(cmd)
                process = p
                ui.post { updateSendIcon() }

                val tOut = Thread { pipe(p.inputStream, cText) }
                val tErr = Thread { pipe(p.errorStream, cErr) }
                tOut.start(); tErr.start()

                val code = p.waitFor()
                tOut.join(1500); tErr.join(1500)
                process = null
                if (code != 0) append("[codigo de saida $code]\n", cDim)
            } catch (e: Exception) {
                process = null
                append("Erro: ${e.message}\n", cErr)
            }
            ui.post { updateSendIcon() }
        }.start()
    }

    private fun stopProcess() {
        process?.destroy()
        append("[interrompido]\n", cDim)
    }

    private fun changeDir(arg: String) {
        val target = if (arg.isEmpty()) "/sdcard" else arg
        Thread {
            try {
                val p = startProcess("cd $target && pwd")
                val result = p.inputStream.bufferedReader().readText().trim()
                val err = p.errorStream.bufferedReader().readText().trim()
                val code = p.waitFor()
                if (code == 0 && result.isNotEmpty()) {
                    cwd = File(result.lines().last())
                    ui.post { updatePrompt() }
                } else {
                    append((if (err.isNotEmpty()) err else "cd: pasta nao encontrada") + "\n", cErr)
                }
            } catch (e: Exception) {
                append("Erro: ${e.message}\n", cErr)
            }
        }.start()
    }

    private fun histUp() {
        if (histIdx > 0) {
            histIdx--
            input.setText(cmdHistory[histIdx]); input.setSelection(input.length())
        }
    }

    private fun histDown() {
        if (histIdx < cmdHistory.size - 1) {
            histIdx++
            input.setText(cmdHistory[histIdx]); input.setSelection(input.length())
        } else {
            histIdx = cmdHistory.size
            input.setText("")
        }
    }

    private fun chipBackground() = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(0xFF2A2A33.toInt())
    }

    private fun addTextKey(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFE6E6E6.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            minWidth = dp(42)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = chipBackground()
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) })
    }

    private fun addIconKey(parent: LinearLayout, path: String, action: () -> Unit) {
        parent.addView(ImageView(this).apply {
            setImageDrawable(pathIcon(path))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = chipBackground()
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(dp(44), dp(38)).apply { marginEnd = dp(6) })
    }

    private fun insert(s: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        val end = input.selectionEnd.coerceAtLeast(0)
        input.text.replace(minOf(start, end), maxOf(start, end), s)
    }

    private fun setupQuickKeys() {
        val bar = findViewById<LinearLayout>(R.id.quickKeys)
        addIconKey(bar, Icons.UP) { histUp() }
        addIconKey(bar, Icons.DOWN) { histDown() }
        addTextKey(bar, "Ctrl+C") { if (process != null) stopProcess() }
        listOf("/", "-", "~", "|", ">", "*", "$", "\"", ".").forEach { k ->
            addTextKey(bar, k) { insert(k) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Copiar tudo").setIcon(pathIcon(Icons.COPY)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Limpar").setIcon(pathIcon(Icons.DELETE)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            1 -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("terminal", out.toString()))
                Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
            }
            2 -> { out.clear(); output.text = "" }
        }
        return true
    }
}
