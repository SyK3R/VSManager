package com.vsmanager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private val rows = mutableMapOf<String, View>()
    private val switches = mutableMapOf<String, Pair<String, Boolean>>()
    private val storageRoot: String = Environment.getExternalStorageDirectory().absolutePath

    private val sortNames = linkedMapOf("name" to "Nome", "size" to "Tamanho", "date" to "Data de modificação")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ftp)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(pathIcon(Icons.BACK))
        toolbar.title = "Configurações"
        toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        list = findViewById(R.id.settingsList)
        build()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun build() {
        header("Exibição", first = true)
        toggle("hidden", "Mostrar arquivos ocultos", "hidden", false)
        toggle("thumbs", "Miniaturas de imagens e vídeos", "thumbs", true)
        toggle("ff", "Pastas primeiro", "foldersFirst", true)

        header("Ordenação")
        row("sortBy", "Ordenar por") { chooseSort() }
        toggle("asc", "Ordem crescente", "sortAsc", true)

        header("Navegação")
        row("start", "Pasta inicial") { chooseStart() }

        header("Segurança")
        toggle("confirm", "Confirmar antes de excluir", "confirmDelete", true)

        header("Permissões")
        row("allfiles", "Acesso a todos os arquivos") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .apply { data = Uri.parse("package:$packageName") })
            }
        }
        row("shizuku", "Shizuku") {
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1)
                }
            } catch (_: Throwable) {}
        }

        header("Geral")
        row("reset", "Restaurar padrões") {
            MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setTitle("Restaurar padrões?")
                .setMessage("Todas as configurações voltam ao valor original.")
                .setPositiveButton("Restaurar") { _, _ -> prefs.edit().clear().apply(); refresh() }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun header(text: String, first: Boolean = false) {
        if (!first) {
            list.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(8); bottomMargin = dp(8)
                })
        }
        list.addView(TextView(this).apply {
            this.text = text; textSize = 14f
            setTextColor(0xFF8FA8FF.toInt())
            setPadding(dp(20), dp(12), dp(20), dp(8))
        })
    }

    private fun row(key: String, title: String, switch: Boolean = false, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.item_setting, list, false)
        v.findViewById<TextView>(R.id.setTitle).text = title
        if (switch) v.findViewById<View>(R.id.setSwitch).visibility = View.VISIBLE
        v.setOnClickListener { onClick() }
        list.addView(v)
        rows[key] = v
        return v
    }

    private fun toggle(key: String, title: String, prefKey: String, default: Boolean) {
        switches[key] = prefKey to default
        row(key, title, switch = true) {
            prefs.edit().putBoolean(prefKey, !prefs.getBoolean(prefKey, default)).apply()
            refresh()
        }
    }

    private fun summary(key: String, text: String?) {
        val s = rows[key]!!.findViewById<TextView>(R.id.setSummary)
        s.text = text
        s.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun refresh() {
        for ((key, pd) in switches) {
            rows[key]!!.findViewById<SwitchCompat>(R.id.setSwitch).isChecked = prefs.getBoolean(pd.first, pd.second)
        }
        summary("sortBy", sortNames[prefs.getString("sortBy", "name")])

        val start = prefs.getString("startDir", "") ?: ""
        summary("start", if (start.isEmpty() || start == storageRoot) "Armazenamento interno" else start)

        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        summary("allfiles", if (granted) "Concedido" else "Não concedido (toque para conceder)")

        summary("shizuku", try {
            when {
                !Shizuku.pingBinder() -> "Não está em execução"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Conectado"
                else -> "Sem permissão (toque para solicitar)"
            }
        } catch (_: Throwable) { "Indisponível" })
    }

    private fun chooseSort() {
        val keys = sortNames.keys.toList()
        val cur = keys.indexOf(prefs.getString("sortBy", "name")).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("Ordenar por")
            .setSingleChoiceItems(sortNames.values.toTypedArray(), cur) { d, i ->
                prefs.edit().putString("sortBy", keys[i]).apply()
                refresh(); d.dismiss()
            }.show()
    }

    private fun chooseStart() {
        val opts = arrayOf("Armazenamento interno", "Download", "Escolher outra pasta")
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("Pasta inicial")
            .setItems(opts) { _, i ->
                when (i) {
                    0 -> save("startDir", "")
                    1 -> save("startDir", File(storageRoot, "Download").absolutePath)
                    2 -> pickFolder(File(storageRoot)) { save("startDir", it.absolutePath) }
                }
            }.show()
    }

    private fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply(); refresh()
    }

    private fun pickFolder(dir: File, onPick: (File) -> Unit) {
        val subs = (dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList())
            .sortedBy { it.name.lowercase() }
        val hasUp = dir.parentFile != null && dir.absolutePath != "/"
        val items = mutableListOf("Usar esta pasta")
        if (hasUp) items.add("..")
        items.addAll(subs.map { it.name })
        val offset = if (hasUp) 2 else 1

        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle(dir.absolutePath)
            .setItems(items.toTypedArray()) { _, i ->
                when {
                    i == 0 -> onPick(dir)
                    hasUp && i == 1 -> pickFolder(dir.parentFile!!, onPick)
                    else -> pickFolder(subs[i - offset], onPick)
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
