package com.vsmanager

import android.Manifest
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class FtpActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private val rows = mutableMapOf<String, View>()
    private val storageRoot: String = Environment.getExternalStorageDirectory().absolutePath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ftp)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(pathIcon(Icons.BACK))
        toolbar.title = "Servidor FTP"
        toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences("ftp", Context.MODE_PRIVATE)
        if (!prefs.contains("pass")) {
            prefs.edit().putString("pass", (100000..999999).random().toString()).apply()
        }

        list = findViewById(R.id.settingsList)
        build()
    }

    override fun onResume() {
        super.onResume()
        FtpService.onChange = { refresh() }
        refresh()
    }

    override fun onPause() {
        FtpService.onChange = null
        super.onPause()
    }

    private fun build() {
        row("state", "Estado", switch = true) { toggleServer() }
        row("url", "URL") {
            FtpService.localIp()?.let {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ftp", "ftp://$it:${prefs.getInt("port", 2121)}/"))
                Toast.makeText(this, "Endereco copiado", Toast.LENGTH_SHORT).show()
            }
        }
        row("tile", "Adicionar as Configuracoes Rapidas") { addTile() }

        list.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            })
        list.addView(TextView(this).apply {
            text = "Configuracao"; textSize = 14f
            setTextColor(0xFF8FA8FF.toInt())
            setPadding(dp(20), dp(12), dp(20), dp(8))
        })

        row("anon", "Login anonimo", switch = true) { toggleAnon() }
        row("user", "Nome do usuario") {
            if (!prefs.getBoolean("anon", false)) {
                editText("Nome do usuario", "Usuario", prefs.getString("user", "admin")!!, false) { v ->
                    if (v.isEmpty() || v.contains(' ')) "Use um nome sem espacos"
                    else { prefs.edit().putString("user", v).apply(); changed(); null }
                }
            }
        }
        row("pass", "Senha") {
            if (!prefs.getBoolean("anon", false)) {
                editText("Senha", "Senha", prefs.getString("pass", "")!!, false) { v ->
                    if (v.length < 4) "Minimo de 4 caracteres"
                    else { prefs.edit().putString("pass", v).apply(); changed(); null }
                }
            }
        }
        row("port", "Porta") {
            editText("Porta", "1024 a 65535", prefs.getInt("port", 2121).toString(), true) { v ->
                val n = v.toIntOrNull()
                if (n == null || n !in 1024..65535) "Informe um numero entre 1024 e 65535"
                else { prefs.edit().putInt("port", n).apply(); changed(); null }
            }
        }
        row("root", "Pasta raiz") { pickRoot() }
        row("write", "Permitir gravacao", switch = true) {
            prefs.edit().putBoolean("write", !prefs.getBoolean("write", true)).apply(); changed()
        }
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

    private fun summary(key: String, text: String?) {
        val s = rows[key]!!.findViewById<TextView>(R.id.setSummary)
        s.text = text
        s.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun sw(key: String) = rows[key]!!.findViewById<SwitchCompat>(R.id.setSwitch)

    private fun refresh() {
        val on = FtpService.running
        sw("state").isChecked = on
        summary("state", if (on) "Em execucao" else "Nao iniciado")

        val ip = FtpService.localIp()
        summary("url", if (ip != null) "ftp://$ip:${prefs.getInt("port", 2121)}/" else "Sem conexao Wi-Fi")

        val anon = prefs.getBoolean("anon", false)
        sw("anon").isChecked = anon
        summary("user", prefs.getString("user", "admin"))
        summary("pass", if (prefs.getString("pass", "").isNullOrEmpty()) "Nao definido" else "Definida")
        rows["user"]!!.alpha = if (anon) 0.4f else 1f
        rows["pass"]!!.alpha = if (anon) 0.4f else 1f

        summary("port", prefs.getInt("port", 2121).toString())
        val root = prefs.getString("root", storageRoot)!!
        summary("root", if (root == storageRoot) "Divisao interna de armazenamento" else root)
        sw("write").isChecked = prefs.getBoolean("write", true)

        FtpService.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            FtpService.error = null
        }
    }

    private fun changed() {
        refresh()
        if (FtpService.running) {
            stopService(Intent(this, FtpService::class.java))
            Handler(Looper.getMainLooper()).postDelayed({
                ContextCompat.startForegroundService(this, Intent(this, FtpService::class.java))
            }, 600)
        }
    }

    private fun toggleServer() {
        if (FtpService.running) {
            stopService(Intent(this, FtpService::class.java))
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
            ContextCompat.startForegroundService(this, Intent(this, FtpService::class.java))
        }
    }

    private fun toggleAnon() {
        if (prefs.getBoolean("anon", false)) {
            prefs.edit().putBoolean("anon", false).apply(); changed()
        } else {
            MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setTitle("Ativar login anonimo?")
                .setMessage("Qualquer pessoa conectada a mesma rede podera acessar os arquivos da pasta raiz sem senha.")
                .setPositiveButton("Ativar") { _, _ -> prefs.edit().putBoolean("anon", true).apply(); changed() }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val sbm = getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(this, FtpTileService::class.java),
                "Servidor FTP",
                Icon.createWithResource(this, R.drawable.ic_storage),
                mainExecutor
            ) { }
        } else {
            MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
                .setTitle("Adicionar o icone")
                .setMessage("Deslize a barra de notificacoes para baixo, toque em editar e arraste \"Servidor FTP\" para o painel.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun editText(title: String, hint: String, current: String, number: Boolean, onOk: (String) -> String?) {
        val view = layoutInflater.inflate(R.layout.dialog_create, null)
        val layout = view.findViewById<TextInputLayout>(R.id.inputLayout)
        val input = view.findViewById<TextInputEditText>(R.id.inputName)
        layout.hint = hint
        input.setText(current)
        if (number) input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setSelection(input.length())

        val d = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle(title).setView(view)
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val err = onOk(input.text.toString().trim())
                if (err == null) d.dismiss() else layout.error = err
            }
        }
        d.show()
    }

    private fun pickRoot(dir: File = File(prefs.getString("root", storageRoot)!!)) {
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
                    i == 0 -> { prefs.edit().putString("root", dir.absolutePath).apply(); changed() }
                    hasUp && i == 1 -> pickRoot(dir.parentFile!!)
                    else -> pickRoot(subs[i - offset])
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
