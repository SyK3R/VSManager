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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var fabContainer: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var adapter: FileAdapter
    private var currentDir: File? = null
    private var showHiddenFiles = false
    
    // Para a lupa
    private var allFiles: List<File> = emptyList()
    private var isSearching = false
    private var searchEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvPath = findViewById(R.id.tvPath)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabContainer = findViewById(R.id.fabContainer)

        // Cliques
        tvPath.setOnClickListener { loadFiles(Environment.getExternalStorageDirectory()) }
        fabContainer.setOnClickListener { showFabMenu(it) }

        checkPermissions()
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        tvPath.text = dir.absolutePath
        isSearching = false

        val files = dir.listFiles()
        allFiles = files?.filter { 
            showHiddenFiles || !it.name.startsWith(".") 
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        updateRecyclerView(allFiles)
    }

    private fun updateRecyclerView(filesToShow: List<File>) {
        adapter = FileAdapter(filesToShow) { file ->
            if (file.isDirectory) {
                loadFiles(file)
            } else {
                Toast.makeText(this, "Arquivo selecionado: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = adapter
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSearching) {
            isSearching = false
            supportActionBar?.setDisplayShowCustomEnabled(false)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            invalidateOptionsMenu()
            updateRecyclerView(allFiles)
        } else if (currentDir != null && currentDir!!.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
            loadFiles(currentDir!!.parentFile!!)
        } else {
            super.onBackPressed()
        }
    }

    // --- Configuração da Barra Superior (Lupa e 3 Pontinhos) ---
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
                // Abre o menu ancorado no botão de 3 pontinhos
                showOptionsMenu(findViewById(R.id.toolbar))
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
                updateRecyclerView(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        searchView.requestFocus()
    }

    // --- Menus ---
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
                "Multiseleção" -> { Toast.makeText(this, "Multiseleção em breve", Toast.LENGTH_SHORT).show(); true }
                "Terminal" -> { Toast.makeText(this, "Terminal em breve", Toast.LENGTH_SHORT).show(); true }
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
            when (item.title) {
                "Nova pasta" -> createFolder()
                "Novo arquivo" -> createFile()
            }
            true
        }
        popup.show()
    }

    // --- Criar Pastas e Arquivos ---
    private fun createFolder() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Nova Pasta")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val folderName = input.text.toString()
                val newDir = File(currentDir, folderName)
                if (newDir.mkdirs()) {
                    loadFiles(currentDir!!)
                    Toast.makeText(this, "Pasta criada!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Falha ao criar pasta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createFile() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Novo Arquivo")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val fileName = input.text.toString()
                val newFile = File(currentDir, fileName)
                try {
                    if (newFile.createNewFile()) {
                        loadFiles(currentDir!!)
                        Toast.makeText(this, "Arquivo criado!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao criar arquivo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- Permissões e Shizuku ---
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivity(intent)
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
                requestShizukuPermission()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
                requestShizukuPermission()
            }
        }
    }

    private fun requestShizukuPermission() {
        // Código real do Shizuku para ele aparecer na lista
        try {
            if (rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                rikka.shizuku.Shizuku.requestPermission(1337)
            }
        } catch (e: Exception) {
            // Shizuku não está rodando, ignora.
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
