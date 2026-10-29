package com.vsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.transition.Fade
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabContainer = findViewById(R.id.fabContainer)

        fabContainer.setOnClickListener { showFabMenu(it) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearching) {
                    closeSearch()
                } else if (isSelectionMode) {
                    clearSelection()
                } else if (currentDir != null && currentDir!!.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                    val parent = currentDir!!.parentFile
                    if (parent != null) loadFiles(parent) else finish()
                } else {
                    finish()
                }
            }
        })

        checkPermissions()
    }

    private fun updateToolbar() {
        if (isSearching) return

        if (isSelectionMode) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel)
            toolbar.title = "${selectedFiles.size} selecionados"
            toolbar.subtitle = ""
        } else {
            if (currentDir?.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            } else {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }
            
            val folderName = if (currentDir?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                "Armazenamento Interno"
            } else {
                currentDir?.name ?: "Armazenamento"
            }
            
            toolbar.title = folderName
            toolbar.subtitle = currentDir?.absolutePath ?: ""
        }
        invalidateOptionsMenu()
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
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null,
                arrayOf("ls", "-a", dir.absolutePath),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess

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
                if (file.isDirectory) {
                    loadFiles(file)
                } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                    showApkOptions(file)
                } else {
                    Toast.makeText(this, "Abrir: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onIconClick = { file ->
                toggleSelection(file)
            },
            onLongClick = { file ->
                if (!isSelectionMode) {
                    showContextMenu(file)
                } else {
                    toggleSelection(file)
                }
            }
        )
        recyclerView.adapter = adapter
    }

    // --- POPUP CUSTOMIZADO DO CLAUDE ---
    private data class PopupItem(val title: String, val iconRes: Int, val onClick: () -> Unit)

    private fun showStyledPopup(anchor: View, items: List<PopupItem>, above: Boolean = false) {
        val root = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val container = root.findViewById<LinearLayout>(R.id.popupItems)

        val popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f * resources.displayMetrics.density
            enterTransition = Fade()
            exitTransition = Fade()
        }

        items.forEach { item ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_popup, container, false)
            row.findViewById<ImageView>(R.id.itemIcon).setImageResource(item.iconRes)
            row.findViewById<TextView>(R.id.itemTitle).text = item.title
            row.setOnClickListener { popup.dismiss(); item.onClick() }
            container.addView(row)
        }

        if (above) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            popup.showAsDropDown(anchor, 0, -(anchor.height + root.measuredHeight), Gravity.END)
        } else {
            popup.showAsDropDown(anchor, 0, 0, Gravity.END)
        }
    }

    private fun showFabMenu(anchorView: View) {
        showStyledPopup(
            anchorView,
            listOf(
                PopupItem("Nova pasta", R.drawable.ic_create_new_folder) { showCreateDialog(true) },
                PopupItem("Novo arquivo", R.drawable.ic_note_add) { showCreateDialog(false) }
            ),
            above = true
        )
    }

    private fun showOptionsMenu(anchorView: View) {
        showStyledPopup(
            anchorView,
            listOf(
                PopupItem(
                    if (showHiddenFiles) "Ocultar arquivos ocultos" else "Mostrar ocultos",
                    R.drawable.ic_visibility
                ) {
                    showHiddenFiles = !showHiddenFiles
                    currentDir?.let { loadFiles(it) }
                },
                PopupItem("Terminal", R.drawable.ic_terminal) {
                    Toast.makeText(this, "Em breve", Toast.LENGTH_SHORT).show()
                }
            )
        )
    }

    private fun showCreateDialog(isFolder: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_create, null)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.inputLayout)
        val input = view.findViewById<TextInputEditText>(R.id.inputName)

        inputLayout.hint = if (isFolder) "Nome da pasta" else "Nome do arquivo"
        inputLayout.setStartIconDrawable(
            if (isFolder) R.drawable.ic_create_new_folder else R.drawable.ic_note_add
        )

        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setTitle(if (isFolder) "Nova pasta" else "Novo arquivo")
            .setView(view)
            .setPositiveButton("Criar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                val newFile = File(currentDir, name)
                when {
                    name.isEmpty() -> inputLayout.error = "Digite um nome"
                    newFile.exists() -> inputLayout.error = "Já existe com esse nome"
                    else -> try {
                        if (isFolder) newFile.mkdirs() else newFile.createNewFile()
                        loadFiles(currentDir!!)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        inputLayout.error = "Erro: ${e.message}"
                    }
                }
            }
        }
        dialog.show()
    }

    // --- Lógica de Contexto ---
    private fun showContextMenu(file: File) {
        val options = arrayOf("Copiar", "Mover", "Renomear", "Excluir", "Informações")
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Copiar em breve", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Mover em breve", Toast.LENGTH_SHORT).show()
                    2 -> renameFile(file)
                    3 -> deleteFile(file)
                    4 -> showFileInfo(file)
                }
            }
            .show()
    }

    private fun showApkOptions(file: File) {
        val options = arrayOf("Instalar", "Visualizar Conteúdo", "Informações")
        MaterialAlertDialogBuilder(this)
            .setTitle("Este arquivo é um pacote do Android.\nDeseja instalá-lo ou visualizar seu conteúdo?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> installApk(file)
                    1 -> Toast.makeText(this, "Visualizador em breve", Toast.LENGTH_SHORT).show()
                    2 -> showFileInfo(file)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun installApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                FileProvider.getUriForFile(this, "$packageName.provider", file),
                "application/vnd.android.package-archive"
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao instalar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSelection(file: File) {
        if (!isSelectionMode) isSelectionMode = true
        
        if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)

        if (selectedFiles.isEmpty()) clearSelection() else {
            updateToolbar()
            setupAdapter(allFiles)
        }
    }

    private fun clearSelection() {
        isSelectionMode = false
        selectedFiles.clear()
        if (currentDir != null) setupAdapter(allFiles)
        updateToolbar()
    }

    // --- Busca ---
    private fun activateSearch() {
        if (isSearching) { closeSearch(); return }

        isSearching = true
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        val searchView = EditText(this).apply {
            hint = "Pesquisar..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setSingleLine()
            setPadding(0, 0, 0, 0)
        }
        
        val layoutParams = Toolbar.LayoutParams(
            Toolbar.LayoutParams.MATCH_PARENT,
            Toolbar.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL
        )
        layoutParams.rightMargin = 100
        searchView.layoutParams = layoutParams

        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.customView = searchView
        invalidateOptionsMenu()

        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = allFiles.filter { it.name.lowercase().contains(query) }
                setupAdapter(filtered)
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
        invalidateOptionsMenu()
        if (reload && currentDir != null) loadFilesNormally(currentDir!!)
        updateToolbar()
    }

    private fun renameFile(file: File) {
        val input = EditText(this)
        input.setText(file.name)
        MaterialAlertDialogBuilder(this)
            .setTitle("Renomear")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = input.text.toString()
                val newFile = File(file.parentFile, newName)
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "Renomeado!", Toast.LENGTH_SHORT).show()
                    loadFiles(currentDir!!)
                } else Toast.makeText(this, "Erro ao renomear", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteFile(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir")
            .setMessage("Tem certeza que deseja excluir '${file.name}'?")
            .setPositiveButton("Excluir") { _, _ ->
                if (file.deleteRecursively()) {
                    Toast.makeText(this, "Excluído!", Toast.LENGTH_SHORT).show()
                    loadFiles(currentDir!!)
                } else Toast.makeText(this, "Erro ao excluir", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFileInfo(file: File) {
        val size = android.text.format.Formatter.formatShortFileSize(this, file.length())
        val date = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", java.util.Date(file.lastModified()))
        MaterialAlertDialogBuilder(this)
            .setTitle("Informações")
            .setMessage("Nome: ${file.name}\nTamanho: $size\nData: $date\nCaminho: ${file.absolutePath}")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (isSelectionMode) {
            menu.add(0, 10, 0, "Copiar").setIcon(android.R.drawable.ic_menu_crop).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(0, 11, 0, "Excluir").setIcon(android.R.drawable.ic_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
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
            R.id.menu_more -> {
                val anchor = findViewById<View>(R.id.menu_more) ?: toolbar
                showOptionsMenu(anchor)
            }
            10 -> { Toast.makeText(this, "Copiar ${selectedFiles.size} itens", Toast.LENGTH_SHORT).show(); true }
            11 -> { deleteSelectedFiles(); true }
            android.R.id.home -> {
                if (isSelectionMode) clearSelection()
                else currentDir?.parentFile?.let { loadFiles(it) }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun deleteSelectedFiles() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir")
            .setMessage("Excluir ${selectedFiles.size} itens?")
            .setPositiveButton("Excluir") { _, _ ->
                var sucesso = 0
                selectedFiles.forEach { if (it.deleteRecursively()) sucesso++ }
                Toast.makeText(this, "$sucesso itens excluídos", Toast.LENGTH_SHORT).show()
                clearSelection()
                loadFiles(currentDir!!)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
                if (rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    rikka.shizuku.Shizuku.requestPermission(1)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager() && currentDir == null) {
                loadFiles(Environment.getExternalStorageDirectory())
            }
        }
    }
}
