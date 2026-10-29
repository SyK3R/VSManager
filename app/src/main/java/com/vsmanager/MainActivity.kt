package com.vsmanager

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var fabContainer: View
    private lateinit var fabIcon: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var adapter: FileAdapter
    private var isMenuOpen = false
    private var currentDir: File? = null
    private var showHiddenFiles = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvPath = findViewById(R.id.tvPath)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fabContainer = findViewById(R.id.fabContainer)
        fabIcon = findViewById(R.id.fabIcon)
        setupFabAnimation()

        tvPath.setOnClickListener {
            loadFiles(Environment.getExternalStorageDirectory())
        }

        checkPermissions()
    }

    private fun loadFiles(dir: File) {
        currentDir = dir
        tvPath.text = dir.absolutePath

        // Pega os arquivos reais
        val files = dir.listFiles()
        
        // Filtra e ordena (pastas primeiro, depois arquivos)
        val filteredFiles = files?.filter { 
            showHiddenFiles || !it.name.startsWith(".") 
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        adapter = FileAdapter(filteredFiles) { file ->
            if (file.isDirectory) {
                loadFiles(file) // Entra na pasta
            } else {
                Toast.makeText(this, "Arquivo: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = adapter
    }

    // Botão de voltar do celular
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentDir != null && currentDir!!.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
            loadFiles(currentDir!!.parentFile!!)
        } else {
            super.onBackPressed()
        }
    }

    // Menus de cima (Lupa e 3 pontinhos)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Lupa").setIcon(R.drawable.ic_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Opções").setIcon(R.drawable.ic_more_vert).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> Toast.makeText(this, "Lupa em breve!", Toast.LENGTH_SHORT).show()
            2 -> showOptionsMenu(findViewById(R.id.toolbar))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(this, view)
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
                else -> false
            }
        }
        popup.show()
    }

    // Animação do Botão +
    private fun setupFabAnimation() {
        fabContainer.setOnClickListener { view ->
            val rotation = if (isMenuOpen) 0f else 45f
            val animator = ObjectAnimator.ofFloat(fabIcon, "rotation", rotation)
            animator.duration = 1000
            animator.interpolator = LinearInterpolator()
            animator.start()

            isMenuOpen = !isMenuOpen
            showFabMenu(view)
        }
    }

    private fun showFabMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Nova pasta")
        popup.menu.add("Novo arquivo")
        popup.show()
    }

    // Permissões
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivity(intent)
            } else {
                loadFiles(Environment.getExternalStorageDirectory())
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
