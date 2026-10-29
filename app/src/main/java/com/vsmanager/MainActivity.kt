package com.vsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var fabContainer: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var adapter: FileAdapter
    private var currentDir: File? = null
    private var showHiddenFiles = false
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvPath = findViewById(R.id.tvPath)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fabContainer = findViewById(R.id.fabContainer)

        tvPath.setOnClickListener { loadFiles(Environment.getExternalStorageDirectory()) }
        fabContainer.setOnClickListener { showFabMenu(it) }

        // Intercepta o botão de voltar do celular
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearching) {
                    isSearching = false
                    supportActionBar?.setDisplayShowCustomEnabled(false)
                    supportActionBar?.setDisplayShowTitleEnabled(true)
                    invalidateOptionsMenu()
                    loadFiles(currentDir!!)
                } else if (currentDir != null && currentDir!!.absolutePath != "/") {
                    val parent = currentDir!!.parentFile
                    if (parent != null) loadFiles(parent) else finish()
                } else {
                    finish() // Fecha o app se estiver na raiz
                }
            }
        })

        checkPermissions()
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        tvPath.text = dir.absolutePath

        // Se for pasta restrita do Android, usa o Shizuku
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

        setupAdapter(files)
    }

    private fun loadFilesWithShizuku(dir: File) {
        try {
            // Comando ls para listar os arquivos (Shizuku)
            val process = rikka.shizuku.Shizuku.newProcess(arrayOf("ls", "-a", dir.absolutePath), null, null)
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
            
            // Ordena e adapta
            val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            setupAdapter(sortedFiles)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao ler com Shizuku: ${e.message}", Toast.LENGTH_SHORT).show()
            loadFilesNormally(dir) // Fallback
        }
    }

    private fun setupAdapter(files: List<File>) {
        adapter = FileAdapter(files) { file ->
            if (file.isDirectory) {
                loadFiles(file)
            } else {
                Toast.makeText(this, "Arquivo: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Lupa").setIcon(R.drawable.ic_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> Toast.makeText(this, "Pesquisa em breve", Toast.LENGTH_SHORT).show()
            2 -> showOptionsMenu(findViewById(R.id.toolbar))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Mostrar ocultos")
        popup.menu.add("Multiseleção")
        popup.menu.add("Terminal")
        popup.setOnMenuItemClickListener {
            if (it.title == "Mostrar ocultos") {
                showHiddenFiles = !showHiddenFiles
                currentDir?.let { loadFiles(it) }
                true
            } else false
        }
        popup.show()
    }

    private fun showFabMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Nova pasta")
        popup.menu.add("Novo arquivo")
        popup.setOnMenuItemClickListener {
            if (it.title == "Nova pasta") {
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
        val input = android.widget.EditText(this)
        val title = if (isFolder) "Nova Pasta" else "Novo Arquivo"
        AlertDialog.Builder(this)
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
