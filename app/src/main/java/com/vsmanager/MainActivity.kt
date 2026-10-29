package com.vsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pedir permissão de arquivos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        // Pedir permissão do Shizuku
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1)
        }

        setContent {
            VSManagerTheme {
                FileManagerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen() {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf(listOf<File>()) }
    var showHidden by remember { mutableStateOf(false) }
    var fabOpen by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }

    // Atualiza a lista de arquivos
    fun loadFiles() {
        val list = currentDir.listFiles()?.filter { 
            showHidden || !it.name.startsWith(".") 
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        files = list
    }

    LaunchedEffect(currentDir, showHidden) {
        loadFiles()
    }

    // Animação do FAB
    val rotation by animateFloatAsState(
        targetValue = if (fabOpen) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "fabRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VSManager", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = currentDir.absolutePath,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    // Seta de voltar
                    IconButton(onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && currentDir.absolutePath != "/") {
                            currentDir = parent
                        } else {
                            (context as? ComponentActivity)?.finish()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Lupa */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = Color.White)
                    }
                    
                    // Menu de 3 pontinhos
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opções", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Mostrar ocultos") }, onClick = {
                                showHidden = !showHidden
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text("Multiseleção") }, onClick = { expanded = false })
                            DropdownMenuItem(text = { Text("Terminal") }, onClick = { expanded = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabOpen = !fabOpen },
                    containerColor = Color(0xFFD3D3D3),
                    contentColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Adicionar",
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
                DropdownMenu(
                    expanded = fabOpen,
                    onDismissRequest = { fabOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nova pasta") },
                        onClick = { showNewFolderDialog = true; fabOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Novo arquivo") },
                        onClick = { showNewFileDialog = true; fabOpen = false }
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues)
        ) {
            items(files) { file ->
                FileItem(file = file) {
                    if (file.isDirectory) {
                        currentDir = file
                    }
                }
            }
        }
    }

    // Diálogo Nova Pasta
    if (showNewFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Nova Pasta") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }) },
            confirmButton = {
                TextButton(onClick = {
                    File(currentDir, name).mkdirs()
                    loadFiles()
                    showNewFolderDialog = false
                }) { Text("Criar") }
            }
        )
    }

    // Diálogo Novo Arquivo
    if (showNewFileDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Novo Arquivo") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }) },
            confirmButton = {
                TextButton(onClick = {
                    File(currentDir, name).createNewFile()
                    loadFiles()
                    showNewFileDialog = false
                }) { Text("Criar") }
            }
        )
    }
}

@Composable
fun FileItem(file: File, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = Color(0xFFD3D3D3),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = file.name, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun VSManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White
        ),
        content = content
    )
}
