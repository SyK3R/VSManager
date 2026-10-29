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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
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
        
        // Remove o título padrão para usarmos o nosso
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabContainer = findViewById(R.id.fabContainer)

        fabContainer.setOnClickListener { showFabMenu(it) }

        // Lógica do botão de voltar do celular
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearching) {
                    isSearching = false
                    supportActionBar?.setDisplayShowCustomEnabled(false)
                    supportActionBar?.setDisplayShowTitleEnabled(false)
                    invalidateOptionsMenu()
                    loadFiles(currentDir!!)
                } else if (currentDir != null && currentDir!!.absolutePath != "/") {
                    val parent = currentDir!!.parentFile
                    if (parent != null) loadFiles(parent) else finish()
                } else {
                    finish()
                }
            }
        })

        checkPermissions()
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        toolbar.subtitle = dir.absolutePath
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

    // --- Lógica do Menu de Contexto (Segurar o dedo) ---
    private fun showContextMenu(file: File) {
        val options = arrayOf("Copiar", "Mover", "Renomear", "Excluir", "Informações")
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Copiar em breve", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Mover em breve", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Renomear em breve", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "Excluir em breve", Toast.LENGTH_SHORT).show()
                    4 -> Toast.makeText(this, "Informações em breve", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // --- Menus ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        menu.add(0, 1, 0, "Lupa").setIcon(R.drawable.ic_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> activateSearch()
            2 -> {
                // Ancora no botão de 3 pontinhos (o próprio toolbar)
                showOptionsMenu(toolbar)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun activateSearch() {
        isSearching = true
        val searchView = EditText(this).apply {
            hint = "Pesquisar..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        searchEditText = searchView
        
        supportActionBar?.setDisplayShowTitleEnabled(false)
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

    private fun showOptionsMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
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
