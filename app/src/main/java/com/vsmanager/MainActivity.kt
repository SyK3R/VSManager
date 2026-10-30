package com.vsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var fabContainer: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var adapter: FileAdapter
    private var currentDir: File? = null
    private var showHiddenFiles = false
    private var isSearching = false
    private var allFiles: List<File> = emptyList()
    
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    
    private var clipboard: List<File> = emptyList()
    private var clipboardCut = false
    private val textExt = setOf("txt","md","json","xml","html","css","js","kt","java","py","sh","log","ini","cfg","conf","properties","gradle","yml","yaml","csv","toml","sql","kts")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // --- CONFIGURAÇÃO DO MENU LATERAL ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Atualiza a barra de armazenamento no fundo do menu
        val statRoot = Environment.getExternalStorageDirectory()
        val stat = android.os.StatFs(statRoot.path)
        val used = stat.totalBytes - stat.availableBytes
        findViewById<ProgressBar>(R.id.storageBar).progress = (used * 100 / stat.totalBytes).toInt()
        findViewById<TextView>(R.id.storageText).text =
            "${Formatter.formatShortFileSize(this, used)} usados de ${Formatter.formatShortFileSize(this, stat.totalBytes)}"

        // Ações do Menu Lateral
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_downloads -> {
                    val downloadDir = File(Environment.getExternalStorageDirectory(), "Download")
                    if (downloadDir.exists()) loadFiles(downloadDir)
                    else Toast.makeText(this, "Pasta Download não encontrada", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_ftp -> {
                    Toast.makeText(this, "Servidor FTP em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Configurações em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_about -> {
                    Toast.makeText(this, "VSManager v1.0", Toast.LENGTH_LONG).show()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabContainer = findViewById(R.id.fabContainer)

        fabContainer.setOnClickListener { showFabMenu(it) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearching) closeSearch()
                else if (isSelectionMode) clearSelection()
                else if (currentDir != null && currentDir!!.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                    val parent = currentDir!!.parentFile
                    if (parent != null) loadFiles(parent) else finish()
                } else finish()
            }
        })

        checkPermissions()
    }

    private fun updateToolbar() {
        invalidateOptionsMenu()
        val header = findViewById<View>(R.id.headerPath)
        val root = Environment.getExternalStorageDirectory()
        val dir = currentDir ?: root
        val atRoot = dir.absolutePath == root.absolutePath

        if (isSelectionMode) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel)
            val n = selectedFiles.size
            toolbar.title = "$n ${if (n == 1) "selecionado" else "selecionados"}"
            toolbar.subtitle = null
            header.visibility = View.GONE
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(!atRoot)
        if (atRoot) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material)
        }
        if (atRoot) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material)
        }
        supportActionBar?.setHomeAsUpIndicator(pathIcon(Icons.BACK))
        header.visibility = View.VISIBLE
        toolbar.title = if (atRoot) "Armazenamento" else dir.name
        toolbar.subtitle = null

        val crumbs = findViewById<LinearLayout>(R.id.crumbs)
        crumbs.removeAllViews()
        val chain = generateSequence(dir) { it.parentFile }
            .takeWhile { it.absolutePath.startsWith(root.absolutePath) }.toList().reversed()
        chain.forEachIndexed { i, f ->
            if (i > 0) crumbs.addView(TextView(this).apply { text = "›"; setTextColor(0xFF777783.toInt()); setPadding(dp(6), 0, dp(6), 0) })
            val last = i == chain.lastIndex
            crumbs.addView(TextView(this).apply {
                text = if (f.absolutePath == root.absolutePath) "Interno" else f.name
                setTextColor(if (last) Color.WHITE else 0xFFB8C4FF.toInt())
                textSize = 13f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (last) 0xFF3D4B8F.toInt() else 0xFF2A2A33.toInt())
                }
                setOnClickListener { if (!last) loadFiles(f) }
            })
        }
        val sv = findViewById<HorizontalScrollView>(R.id.crumbScroll)
        sv.post { sv.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        clearSelection()
        closeSearch(false)
        updateToolbar()

        val isRestricted = dir.absolutePath.contains("/Android/data") || dir.absolutePath.contains("/Android/obb")
        
        if (isRestricted && rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            loadFilesWithShizuku(dir)
        } else {
            loadFilesNormally(dir)
        }
    }

    private fun loadFilesNormally(dir: File) {
        val files = dir.listFiles()?.filter { 
            showHiddenFiles || !it.name.startsWith(".") 
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        allFiles = files
        setupAdapter(files)
    }

    private fun loadFilesWithShizuku(dir: File) {
        try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(null, arrayOf("ls", "-a", dir.absolutePath), null, null) as rikka.shizuku.ShizukuRemoteProcess

            val reader = process.inputStream.bufferedReader()
            val files = mutableListOf<File>()
            var line = reader.readLine()
            while (line != null) {
                if (line != "." && line != ".." && (showHiddenFiles || !line.startsWith("."))) {
                    files.add(File(dir, line))
                }
                line = reader.readLine()
            }
            process.waitFor()

            val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            allFiles = sortedFiles
            setupAdapter(sortedFiles)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao ler com Shizuku: ${e.message}", Toast.LENGTH_SHORT).show()
            loadFilesNormally(dir)
        }
    }

    private fun setupAdapter(files: List<File>) {
        adapter = FileAdapter(
            files = files,
            isSelectionMode = isSelectionMode,
            selectedFiles = selectedFiles,
            onItemClick = { file ->
                if (file.isDirectory) loadFiles(file)
                else if (file.name.endsWith(".apk", ignoreCase = true)) showApkSheet(file)
                else if (file.extension.lowercase() in textExt) openEditor(file)
                else Toast.makeText(this, "Abrir: ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onIconClick = { file -> toggleSelection(file) },
            onLongClick = { file ->
                if (!isSelectionMode) showContextMenu(file) else toggleSelection(file)
            }
        )
        recyclerView.adapter = adapter
    }

    private data class PopupItem(val title: String, val iconRes: Int, val onClick: () -> Unit)
    private data class SheetAction(val title: String, val icon: String, val danger: Boolean = false, val run: () -> Unit)

    private fun showStyledPopup(anchor: View, items: List<PopupItem>, above: Boolean = false) {
        val root = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val container = root.findViewById<LinearLayout>(R.id.popupItems)

        val popup = PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f * resources.displayMetrics.density
        }

        items.forEach { item ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_popup, container, false)
            val iv = row.findViewById<ImageView>(R.id.itemIcon)
            if (item.iconRes == 0) iv.setImageDrawable(pathIcon(Icons.PASTE)) else iv.setImageResource(item.iconRes)
            row.findViewById<TextView>(R.id.itemTitle).text = item.title
            row.setOnClickListener { popup.dismiss(); item.onClick() }
            container.addView(row)
        }

        if (above) {
            root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            popup.showAsDropDown(anchor, 0, -(anchor.height + root.measuredHeight), Gravity.END)
        } else {
            popup.showAsDropDown(anchor, 0, 0, Gravity.END)
        }
    }

    private fun showSheet(title: String, subtitle: String, icon: Drawable, actions: List<SheetAction>) {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_actions, null)
        v.findViewById<ImageView>(R.id.sheetIcon).setImageDrawable(icon)
        v.findViewById<TextView>(R.id.sheetTitle).text = title
        v.findViewById<TextView>(R.id.sheetSubtitle).text = subtitle
        val list = v.findViewById<LinearLayout>(R.id.sheetList)
        actions.forEach { a ->
            val row = layoutInflater.inflate(R.layout.item_popup, list, false)
            val ic = row.findViewById<ImageView>(R.id.itemIcon)
            val tx = row.findViewById<TextView>(R.id.itemTitle)
            ic.setImageDrawable(pathIcon(a.icon)); tx.text = a.title
            if (a.danger) {
                ic.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFF6B6B.toInt()); tx.setTextColor(0xFFFF6B6B.toInt())
            }
            row.setOnClickListener { dialog.dismiss(); a.run() }
            list.addView(row)
        }
        dialog.setContentView(v)
        (v.parent as View).setBackgroundColor(Color.TRANSPARENT)
        dialog.show()
    }

    private fun showStorageSheet() {
        val st = android.os.StatFs(Environment.getExternalStorageDirectory().path)
        val total = st.totalBytes; val free = st.availableBytes; val used = total - free
        val v = layoutInflater.inflate(R.layout.sheet_storage, null)
        v.findViewById<ProgressBar>(R.id.storageBar).progress = (used * 100 / total).toInt()
        v.findViewById<TextView>(R.id.storageUsed).text =
            "${Formatter.formatShortFileSize(this, used)} usados de ${Formatter.formatShortFileSize(this, total)}"
        v.findViewById<TextView>(R.id.storageFree).text = "${Formatter.formatShortFileSize(this, free)} livres"
        BottomSheetDialog(this).apply {
            setContentView(v)
            (v.parent as View).setBackgroundColor(Color.TRANSPARENT)
            show()
        }
    }

    private fun showFabMenu(anchorView: View) {
        val items = mutableListOf(
            PopupItem("Nova pasta", R.drawable.ic_create_new_folder) { showCreateDialog(true) },
            PopupItem("Novo arquivo", R.drawable.ic_note_add) { showCreateDialog(false) }
        )
        if (clipboard.isNotEmpty()) items.add(0, PopupItem("Colar aqui", 0) { paste() })
        showStyledPopup(anchorView, items, above = true)
    }

    private fun showOptionsMenu(anchorView: View) {
        showStyledPopup(
            anchorView,
            listOf(
                PopupItem("Mostrar ocultos",
                    if (showHiddenFiles) R.drawable.ic_visibility else R.drawable.ic_visibility_off) {
                    showHiddenFiles = !showHiddenFiles
                    currentDir?.let { loadFiles(it) }
                },
                PopupItem("Armazenamento", R.drawable.ic_storage) { showStorageSheet() },
                PopupItem("Terminal", R.drawable.ic_terminal) { startActivity(android.content.Intent(this, TerminalActivity::class.java)) }
            )
        )
    }

    private fun showCreateDialog(isFolder: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_create, null)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.inputLayout)
        val input = view.findViewById<TextInputEditText>(R.id.inputName)

        inputLayout.hint = if (isFolder) "Nome da pasta" else "Nome do arquivo"
        inputLayout.setStartIconDrawable(if (isFolder) R.drawable.ic_create_new_folder else R.drawable.ic_note_add)

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle(if (isFolder) "Nova pasta" else "Novo arquivo")
            .setView(view).setPositiveButton("Criar", null).setNegativeButton("Cancelar", null).create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                val newFile = File(currentDir, name)
                when {
                    name.isEmpty() -> inputLayout.error = "Digite um nome"
                    newFile.exists() -> inputLayout.error = "Já existe"
                    else -> try {
                        if (isFolder) newFile.mkdirs() else newFile.createNewFile()
                        loadFiles(currentDir!!); dialog.dismiss()
                    } catch (e: Exception) { inputLayout.error = "Erro: ${e.message}" }
                }
            }
        }
        dialog.show()
    }

    private fun showApkSheet(file: File) {
        val pm = packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
        val app = info?.applicationInfo
        var icon: Drawable = ColorDrawable(Color.TRANSPARENT)
        var title = file.name
        var sub = Formatter.formatShortFileSize(this, file.length())
        if (app != null) {
            app.sourceDir = file.absolutePath; app.publicSourceDir = file.absolutePath
            icon = app.loadIcon(pm); title = app.loadLabel(pm).toString()
            sub = "${info.packageName}\nVersão ${info.versionName} • $sub"
        }
        showSheet(title, sub, icon, listOf(
            SheetAction("Instalar", Icons.CHECK) { installApk(file) },
            SheetAction("Visualizar conteúdo", Icons.ZIP) { showZipContent(file) },
            SheetAction("Informações", Icons.INFO) { showInfoDialog(file) }
        ))
    }

    private fun installApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(FileProvider.getUriForFile(this, "$packageName.provider", file), "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) { Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun showZipContent(file: File) {
        val names = try { java.util.zip.ZipFile(file).use { z -> z.entries().toList().map { it.name } } }
                    catch (e: Exception) { listOf("Erro: ${e.message}") }
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("${names.size} itens").setItems(names.take(500).toTypedArray(), null)
            .setPositiveButton("Fechar", null).show()
    }

    private fun showContextMenu(file: File) {
        val actions = mutableListOf(
            SheetAction("Selecionar", Icons.CHECK) { toggleSelection(file) },
            SheetAction("Copiar", Icons.COPY) { clipboard = listOf(file); clipboardCut = false; toastPaste() },
            SheetAction("Mover", Icons.MOVE) { clipboard = listOf(file); clipboardCut = true; toastPaste() },
            SheetAction("Renomear", Icons.EDIT) { showRenameDialog(file) },
            SheetAction("Compactar (.zip)", Icons.ZIP) { zipFiles(listOf(file)) }
        )
        if (!file.isDirectory) actions.add(SheetAction("Editar como texto", Icons.EDIT) { openEditor(file) })
        if (file.extension.equals("zip", true) || file.extension.equals("apk", true))
            actions.add(SheetAction("Descompactar aqui", Icons.UNZIP) { unzip(file) })
        actions.add(SheetAction("Informações", Icons.INFO) { showInfoDialog(file) })
        actions.add(SheetAction("Excluir", Icons.DELETE, danger = true) { confirmDelete(listOf(file)) })

        val sub = if (file.isDirectory) "Pasta" else Formatter.formatShortFileSize(this, file.length())
        showSheet(file.name, sub, pathIcon(if (file.isDirectory) Icons.MOVE else Icons.INFO).apply {
            setTint(0xFFB8C4FF.toInt()) }, actions)
    }

    private fun toastPaste() = Toast.makeText(this, "Abra a pasta destino e toque em + > Colar aqui", Toast.LENGTH_LONG).show()

    private fun runBg(work: () -> Unit) {
        Thread {
            try { work(); runOnUiThread { currentDir?.let { loadFiles(it) } } }
            catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show() } }
        }.start()
    }

    private fun paste() {
        val dest = currentDir ?: return
        runBg {
            clipboard.forEach { src ->
                var target = File(dest, src.name)
                if (target.exists()) {
                    val ext = if (src.extension.isEmpty()) "" else ".${src.extension}"
                    target = File(dest, "${src.nameWithoutExtension}_copia$ext")
                }
                if (clipboardCut) {
                    if (!src.renameTo(target)) { src.copyRecursively(target); src.deleteRecursively() }
                } else src.copyRecursively(target)
            }
            if (clipboardCut) clipboard = emptyList()
        }
    }

    private fun confirmDelete(files: List<File>) {
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("Excluir ${files.size} item(ns)?")
            .setMessage(files.joinToString("\n") { it.name }.take(300))
            .setPositiveButton("Excluir") { _, _ -> runBg { files.forEach { it.deleteRecursively() } } }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showRenameDialog(file: File) {
        val view = layoutInflater.inflate(R.layout.dialog_create, null)
        val layout = view.findViewById<TextInputLayout>(R.id.inputLayout)
        val input = view.findViewById<TextInputEditText>(R.id.inputName)
        layout.hint = "Novo nome"; input.setText(file.name)
        val d = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle("Renomear").setView(view).setPositiveButton("Renomear", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = input.text.toString().trim()
                val target = File(file.parentFile, n)
                when {
                    n.isEmpty() -> layout.error = "Digite um nome"
                    target.exists() -> layout.error = "Já existe"
                    file.renameTo(target) -> { d.dismiss(); currentDir?.let { loadFiles(it) } }
                    else -> layout.error = "Erro ao renomear"
                }
            }
        }
        d.show()
    }

    private fun zipFiles(files: List<File>) {
        val dir = currentDir ?: return
        val out = File(dir, (if (files.size == 1) files[0].nameWithoutExtension else "arquivos") + ".zip")
        runBg {
            java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zos ->
                fun add(f: File, base: String) {
                    if (f == out) return
                    if (f.isDirectory) f.listFiles()?.forEach { add(it, "$base${f.name}/") }
                    else {
                        zos.putNextEntry(java.util.zip.ZipEntry(base + f.name))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                files.forEach { add(it, "") }
            }
        }
    }

    private fun unzip(zip: File) {
        val dest = File(zip.parentFile, zip.nameWithoutExtension)
        runBg {
            dest.mkdirs()
            java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val out = File(dest, e.name)
                    if (!out.canonicalPath.startsWith(dest.canonicalPath)) throw SecurityException("Entrada inválida")
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs(); out.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
        }
    }

    private fun showInfoDialog(file: File) {
        val size = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(file.lastModified())
        MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle(file.name)
            .setMessage("Caminho:\n${file.absolutePath}\n\nTamanho: ${Formatter.formatShortFileSize(this, size)}\nModificado: $date")
            .setPositiveButton("Fechar", null).show()
    }

    private fun toggleSelection(file: File) {
        if (!isSelectionMode) isSelectionMode = true
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
        if (selectedFiles.isEmpty()) clearSelection() else { updateToolbar(); setupAdapter(allFiles) }
    }

    private fun clearSelection() {
        isSelectionMode = false; selectedFiles.clear()
        if (currentDir != null) setupAdapter(allFiles)
        updateToolbar()
    }

    private fun activateSearch() {
        if (isSearching) { closeSearch(); return }
        isSearching = true
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // --- CONFIGURAÇÃO DO MENU LATERAL ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Atualiza a barra de armazenamento no fundo do menu
        val statRoot = Environment.getExternalStorageDirectory()
        val stat = android.os.StatFs(statRoot.path)
        val used = stat.totalBytes - stat.availableBytes
        findViewById<ProgressBar>(R.id.storageBar).progress = (used * 100 / stat.totalBytes).toInt()
        findViewById<TextView>(R.id.storageText).text =
            "${Formatter.formatShortFileSize(this, used)} usados de ${Formatter.formatShortFileSize(this, stat.totalBytes)}"

        // Ações do Menu Lateral
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_downloads -> {
                    val downloadDir = File(Environment.getExternalStorageDirectory(), "Download")
                    if (downloadDir.exists()) loadFiles(downloadDir)
                    else Toast.makeText(this, "Pasta Download não encontrada", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_ftp -> {
                    Toast.makeText(this, "Servidor FTP em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Configurações em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_about -> {
                    Toast.makeText(this, "VSManager v1.0", Toast.LENGTH_LONG).show()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        val searchView = EditText(this).apply {
            hint = "Pesquisar..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setBackgroundColor(Color.TRANSPARENT); setSingleLine()
        }
        val layoutParams = Toolbar.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, Toolbar.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL)
        layoutParams.rightMargin = 100; searchView.layoutParams = layoutParams

        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.customView = searchView
        invalidateOptionsMenu()

        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                setupAdapter(allFiles.filter { it.name.lowercase().contains(query) })
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        searchView.requestFocus()
    }

    private fun closeSearch(reload: Boolean = true) {
        isSearching = false
        supportActionBar?.setDisplayShowCustomEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // --- CONFIGURAÇÃO DO MENU LATERAL ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Atualiza a barra de armazenamento no fundo do menu
        val statRoot = Environment.getExternalStorageDirectory()
        val stat = android.os.StatFs(statRoot.path)
        val used = stat.totalBytes - stat.availableBytes
        findViewById<ProgressBar>(R.id.storageBar).progress = (used * 100 / stat.totalBytes).toInt()
        findViewById<TextView>(R.id.storageText).text =
            "${Formatter.formatShortFileSize(this, used)} usados de ${Formatter.formatShortFileSize(this, stat.totalBytes)}"

        // Ações do Menu Lateral
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_downloads -> {
                    val downloadDir = File(Environment.getExternalStorageDirectory(), "Download")
                    if (downloadDir.exists()) loadFiles(downloadDir)
                    else Toast.makeText(this, "Pasta Download não encontrada", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_ftp -> {
                    Toast.makeText(this, "Servidor FTP em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Configurações em breve", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_about -> {
                    Toast.makeText(this, "VSManager v1.0", Toast.LENGTH_LONG).show()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
        invalidateOptionsMenu()
        if (reload && currentDir != null) loadFilesNormally(currentDir!!)
        updateToolbar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (isSelectionMode) {
            menu.add(0, 10, 0, "Copiar").setIcon(pathIcon(Icons.COPY)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(0, 11, 0, "Excluir").setIcon(pathIcon(Icons.DELETE)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        } else {
            val searchIcon = if (isSearching) android.R.drawable.ic_menu_close_clear_cancel else R.drawable.ic_search
            menu.add(0, 1, 0, "Lupa").setIcon(searchIcon).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(0, R.id.menu_more, 1, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> activateSearch()
            R.id.menu_more -> showOptionsMenu(findViewById(R.id.menu_more) ?: toolbar)
            10 -> { clipboard = selectedFiles.toList(); clipboardCut = false; clearSelection(); toastPaste() }
            11 -> { val l = selectedFiles.toList(); clearSelection(); confirmDelete(l) }
            android.R.id.home -> {
                if (isSelectionMode) clearSelection() else currentDir?.parentFile?.let { loadFiles(it) }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") })
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
                if (rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    rikka.shizuku.Shizuku.requestPermission(1)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            } else loadFiles(Environment.getExternalStorageDirectory())
        }
    }

    private fun openEditor(file: File) {
        if (file.length() > 5L * 1024 * 1024) {
            Toast.makeText(this, "Arquivo grande demais para o editor (máx. 5 MB)", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, EditorActivity::class.java).putExtra("path", file.absolutePath))
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() && currentDir == null) {
            loadFiles(Environment.getExternalStorageDirectory())
        }
    }
}
