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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

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
    
    // Controles dos diálogos
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }

    fun loadFiles() {
        val list = currentDir.listFiles()?.filter { 
            showHidden || !it.name.startsWith(".") 
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        files = list
    }

    LaunchedEffect(currentDir, showHidden) {
        loadFiles()
    }

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
                    IconButton(onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null) currentDir = parent
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Lupa */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = Color.White)
                    }
                    
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
                    containerColor = Color(0xFF4F4F4F),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
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
                .background(Color(0xFF0F0F0F))
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(files, key = { it.absolutePath }) { file -> // key para evitar lag
                FileItem(file = file) {
                    if (file.isDirectory) currentDir = file
                }
            }
        }
    }

    // Dialogo Nova Pasta
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

    // Dialogo Novo Arquivo
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
    val context = LocalContext.current
    val icon: ImageVector
    val iconColor: Color
    var subtitle = ""

    if (file.isDirectory) {
        icon = Icons.Outlined.Folder
        iconColor = Color(0xFF64B5F6) // Azul pastel bonito
        subtitle = "Pasta"
    } else {
        icon = Icons.Default.Description
        iconColor = Color(0xFFAAAAAA)
        val size = Formatter.formatShortFileSize(context, file.length())
        val date = android.text.format.DateFormat.format("dd/MM/yyyy", Date(file.lastModified()))
        subtitle = "$size • $date"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun VSManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1E1E1E),
            onSurface = Color.White
        ),
        content = content
    )
}
