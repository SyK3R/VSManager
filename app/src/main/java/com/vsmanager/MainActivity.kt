package com.vsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var searchEditText: EditText? = null

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
        if (isSearching) return // Se estiver pesquisando, não mexe no cabeçalho normal

        if (currentDir?.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        } else {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
        toolbar.title = "VSManager"
        toolbar.subtitle = currentDir?.absolutePath ?: ""
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        closeSearch(false) // Fecha a busca se estiver aberta
        updateToolbar()
        isSearching = false

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
        adapter = FileAdapter(files, 
            onClick = { file ->
                if (file.isDirectory) {
                    loadFiles(file)
                } else {
                    Toast.makeText(this, "Arquivo: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onLongClick = { file ->
                showContextMenu(file)
            }
        )
        recyclerView.adapter = adapter
    }

    // --- Lógica de Pesquisa ---
    private fun activateSearch() {
        if (isSearching) {
            closeSearch()
            return
        }

        isSearching = true
        // Esconde o título e subtítulo
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        val searchView = EditText(this).apply {
            hint = "Pesquisar..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setSingleLine()
        }
        searchEditText = searchView
        
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
        
        if (reload && currentDir != null) {
            loadFilesNormally(currentDir!!) // Recarrega a lista original
        }
        updateToolbar()
    }

    // --- Menu de Contexto ---
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
                } else {
                    Toast.makeText(this, "Erro ao renomear", Toast.LENGTH_SHORT).show()
                }
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
                } else {
                    Toast.makeText(this, "Erro ao excluir", Toast.LENGTH_SHORT).show()
                }
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

    // --- Menus ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        // Muda o ícone da lupa para X se estiver pesquisando
        val searchIcon = if (isSearching) android.R.drawable.ic_menu_close_clear_cancel else R.drawable.ic_search
        menu.add(0, 1, 0, "Lupa").setIcon(searchIcon).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, R.id.menu_more, 1, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> activateSearch()
            R.id.menu_more -> {
                val anchor = findViewById<View>(R.id.menu_more) ?: toolbar
                showOptionsMenu(anchor)
            }
            android.R.id.home -> {
                currentDir?.parentFile?.let { loadFiles(it) }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showOptionsMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView, Gravity.END)
        popup.menu.add("Mostrar ocultos")
        popup.menu.add("Multiseleção")
        popup.menu.add("Terminal")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Mostrar ocultos" -> {
                    showHiddenFiles = !showHiddenFiles
                    currentDir?.let { loadFiles(it) }
                    true
                }
                "Multiseleção" -> { Toast.makeText(this, "Em breve", Toast.LENGTH_SHORT).show(); true }
                "Terminal" -> { Toast.makeText(this, "Em breve", Toast.LENGTH_SHORT).show(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showFabMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menu.add("Nova pasta")
        popup.menu.add("Novo arquivo")
        
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Nova pasta") {
                showCreateDialog(true)
                true
            } else {
                showCreateDialog(false)
                true
            }
        }
        popup.show()
    }

    private fun showCreateDialog(isFolder: Boolean) {
        val input = EditText(this)
        val title = if (isFolder) "Nova Pasta" else "Novo Arquivo"
        
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val name = input.text.toString()
                val newFile = File(currentDir, name)
                try {
                    if (isFolder) newFile.mkdirs() else newFile.createNewFile()
                    loadFiles(currentDir!!)
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro", Toast.LENGTH_SHORT).show()
                }
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
