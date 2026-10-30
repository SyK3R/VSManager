package com.vsmanager

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.nio.charset.Charset

object Syntax {
    private class Rule(val re: Regex, val color: Int)

    private const val KW = 0xFFFF9E64.toInt()
    private const val STR = 0xFF9ECE6A.toInt()
    private const val NUM = 0xFF7DCFFF.toInt()
    private const val COM = 0xFF6B7280.toInt()
    private const val TAG = 0xFFF7768E.toInt()

    private fun kw(vararg w: String) = Regex("\\b(" + w.joinToString("|") + ")\\b")
    private val num = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val str = Regex("\"(\\\\.|[^\"\\\\\n])*\"|'(\\\\.|[^'\\\\\n])*'")

    private val langs: Map<String, List<Rule>> = mapOf(
        "Kotlin/Java" to listOf(
            Rule(kw("fun","val","var","class","object","interface","if","else","when","for","while","return",
                "import","package","private","public","protected","override","null","true","false","this",
                "new","static","final","void","int","String","try","catch","throw","is","in","as"), KW),
            Rule(num, NUM), Rule(str, STR), Rule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), COM)),
        "JSON" to listOf(
            Rule(kw("true","false","null"), KW), Rule(num, NUM), Rule(str, STR)),
        "XML/HTML" to listOf(
            Rule(Regex("</?[\\w:.-]+|/?>"), TAG), Rule(str, STR), Rule(Regex("<!--[\\s\\S]*?-->"), COM)),
        "Python" to listOf(
            Rule(kw("def","class","if","elif","else","for","while","return","import","from","as","try",
                "except","finally","with","lambda","None","True","False","and","or","not","in","is","pass"), KW),
            Rule(num, NUM), Rule(str, STR), Rule(Regex("#[^\\n]*"), COM)),
        "Shell" to listOf(
            Rule(kw("if","then","else","elif","fi","for","do","done","while","case","esac","function",
                "echo","export","cd","exit","return"), KW),
            Rule(Regex("\\$\\{?\\w+\\}?"), NUM), Rule(str, STR), Rule(Regex("#[^\\n]*"), COM))
    )

    val names = listOf("Nenhuma") + langs.keys

    fun detect(f: File) = when (f.extension.lowercase()) {
        "kt", "java", "js", "gradle", "kts" -> "Kotlin/Java"
        "json" -> "JSON"
        "xml", "html", "htm", "svg" -> "XML/HTML"
        "py" -> "Python"
        "sh", "bash" -> "Shell"
        else -> "Nenhuma"
    }

    fun apply(e: Editable, lang: String) {
        for (s in e.getSpans(0, e.length, ForegroundColorSpan::class.java)) e.removeSpan(s)
        val rules = langs[lang] ?: return
        for (r in rules) for (m in r.re.findAll(e))
            e.setSpan(ForegroundColorSpan(r.color), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

class EditorActivity : AppCompatActivity() {

    private lateinit var file: File
    private lateinit var edit: EditText
    private lateinit var toolbar: Toolbar
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText

    private var charset: Charset = Charsets.UTF_8
    private var dirty = false
    private var loading = false
    private var wrap = true
    private var lang = "Nenhuma"
    private val handler = Handler(Looper.getMainLooper())
    private val highlight = Runnable {
        if (edit.length() < 200_000) Syntax.apply(edit.text, lang)
    }

    private val fonts = linkedMapOf(
        "Monoespaçada" to Typeface.MONOSPACE, "Sans-serif" to Typeface.SANS_SERIF, "Serif" to Typeface.SERIF)
    private val sizes = listOf(10, 12, 14, 16, 18, 20, 24)
    private val charsets = listOf("UTF-8", "ISO-8859-1", "windows-1252", "UTF-16")

    private val prefs by lazy { getSharedPreferences("editor", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        file = File(intent.getStringExtra("path") ?: run { finish(); return })
        lang = Syntax.detect(file)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(pathIcon(Icons.BACK))
        updateTitle()

        edit = findViewById(R.id.editor)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)

        edit.typeface = fonts[prefs.getString("font", "Monoespaçada")] ?: Typeface.MONOSPACE
        edit.textSize = prefs.getInt("size", 14).toFloat()

        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                if (!dirty) { dirty = true; updateTitle() }
                handler.removeCallbacks(highlight); handler.postDelayed(highlight, 400)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<View>(R.id.searchNext).setOnClickListener { find(true) }
        findViewById<View>(R.id.searchPrev).setOnClickListener { find(false) }
        searchInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { find(true); true } else false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchBar.visibility == View.VISIBLE) toggleSearch()
                else if (dirty) confirmExit()
                else finish()
            }
        })

        load()
    }

    private fun updateTitle() { toolbar.title = (if (dirty) "● " else "") + file.name }

    private fun load() {
        loading = true
        Thread {
            val text = try { String(file.readBytes(), charset) } catch (e: Exception) { null }
            runOnUiThread {
                if (text == null) {
                    Toast.makeText(this, "Não foi possível abrir o arquivo", Toast.LENGTH_LONG).show()
                    finish(); return@runOnUiThread
                }
                edit.setText(text)
                loading = false; dirty = false; updateTitle()
                highlight.run()
            }
        }.start()
    }

    private fun save(then: (() -> Unit)? = null) {
        val text = edit.text.toString()
        Thread {
            try {
                file.writeBytes(text.toByteArray(charset))
                runOnUiThread {
                    dirty = false; updateTitle()
                    Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
                    then?.invoke()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("Salvar alterações?")
            .setMessage(file.name)
            .setPositiveButton("Salvar") { _, _ -> save { finish() } }
            .setNegativeButton("Descartar") { _, _ -> finish() }
            .setNeutralButton("Cancelar", null).show()
    }

    private fun toggleSearch() {
        val show = searchBar.visibility != View.VISIBLE
        searchBar.visibility = if (show) View.VISIBLE else View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            searchInput.requestFocus()
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        }
    }

    private fun find(forward: Boolean) {
        val q = searchInput.text.toString()
        if (q.isEmpty()) return
        val text = edit.text
        val from = if (forward) edit.selectionEnd else edit.selectionStart - 1
        var i = if (forward) text.indexOf(q, from.coerceAtLeast(0), true)
                else text.lastIndexOf(q, from.coerceAtLeast(0), true)
        if (i < 0) i = if (forward) text.indexOf(q, 0, true) else text.lastIndexOf(q, ignoreCase = true)
        if (i < 0) { Toast.makeText(this, "Não encontrado", Toast.LENGTH_SHORT).show(); return }
        edit.requestFocus()
        edit.setSelection(i, i + q.length)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Buscar").setIcon(R.drawable.ic_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Salvar").setIcon(pathIcon(Icons.SAVE)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 3, 2, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            1 -> toggleSearch()
            2 -> save()
            3 -> showMenuSheet()
        }
        return true
    }

    private fun showMenuSheet() {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_actions, null)
        v.findViewById<ImageView>(R.id.sheetIcon).visibility = View.GONE
        v.findViewById<TextView>(R.id.sheetTitle).text = file.name
        v.findViewById<TextView>(R.id.sheetSubtitle).text = "${charset.name()} • $lang"
        val list = v.findViewById<LinearLayout>(R.id.sheetList)

        fun row(title: String, run: () -> Unit) {
            val r = layoutInflater.inflate(R.layout.item_popup, list, false)
            r.findViewById<ImageView>(R.id.itemIcon).visibility = View.GONE
            r.findViewById<TextView>(R.id.itemTitle).apply { text = title; setPadding(dp(8), 0, 0, 0) }
            r.setOnClickListener { dialog.dismiss(); run() }
            list.addView(r)
        }
        row("Visualizar") { dialogView() }
        row("Fonte") { dialogFont() }
        row("Tamanho da fonte") { dialogSize() }
        row("Codificação") { dialogCharset() }
        row("Sintaxe") { dialogSyntax() }
        row("Sair") { onBackPressedDispatcher.onBackPressed() }

        dialog.setContentView(v)
        (v.parent as View).setBackgroundColor(Color.TRANSPARENT)
        dialog.show()
    }

    private fun dialogView() {
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog).setTitle("Visualizar")
            .setMultiChoiceItems(arrayOf("Quebra de linha"), booleanArrayOf(wrap)) { _, _, on ->
                wrap = on; edit.setHorizontallyScrolling(!on)
            }.setPositiveButton("OK", null).show()
    }

    private fun dialogFont() {
        val names = fonts.keys.toList()
        val cur = names.indexOf(prefs.getString("font", "Monoespaçada")).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog).setTitle("Fonte")
            .setSingleChoiceItems(names.toTypedArray(), cur) { d, i ->
                edit.typeface = fonts[names[i]]
                prefs.edit().putString("font", names[i]).apply(); d.dismiss()
            }.show()
    }

    private fun dialogSize() {
        val cur = sizes.indexOf(prefs.getInt("size", 14)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog).setTitle("Tamanho da fonte")
            .setSingleChoiceItems(sizes.map { "$it sp" }.toTypedArray(), cur) { d, i ->
                edit.textSize = sizes[i].toFloat()
                prefs.edit().putInt("size", sizes[i]).apply(); d.dismiss()
            }.show()
    }

    private fun dialogCharset() {
        val cur = charsets.indexOf(charset.name()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog).setTitle("Codificação")
            .setSingleChoiceItems(charsets.toTypedArray(), cur) { d, i ->
                d.dismiss()
                val newCs = Charset.forName(charsets[i])
                if (dirty) {
                    MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                        .setTitle("Recarregar arquivo?")
                        .setMessage("As alterações não salvas serão perdidas.")
                        .setPositiveButton("Recarregar") { _, _ -> charset = newCs; load() }
                        .setNegativeButton("Cancelar", null).show()
                } else { charset = newCs; load() }
            }.show()
    }

    private fun dialogSyntax() {
        val cur = Syntax.names.indexOf(lang).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog).setTitle("Sintaxe")
            .setSingleChoiceItems(Syntax.names.toTypedArray(), cur) { d, i ->
                lang = Syntax.names[i]; highlight.run(); d.dismiss()
            }.show()
    }
}
