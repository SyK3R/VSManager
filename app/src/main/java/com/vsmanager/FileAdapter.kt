package com.vsmanager

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.Date

class FileAdapter(
    private val files: List<File>,
    private val isSelectionMode: Boolean,
    private val selectedFiles: Set<File>,
    private val onItemClick: (File) -> Unit,
    private val onIconClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val icon: ImageView = view.findViewById(R.id.folderIcon)
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
        val textContainer: LinearLayout = view.findViewById(R.id.textContainer)
        val name: TextView = view.findViewById(R.id.fileName)
        val subtitle: TextView = view.findViewById(R.id.fileSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.name
        
        holder.icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        if (file.isDirectory) {
            // Lógica para Ícone de App dentro de Android/data ou obb
            val isAndroidData = file.parentFile?.name == "data" || file.parentFile?.name == "obb"
            var appIconLoaded = false

            if (isAndroidData) {
                try {
                    val pm = holder.itemView.context.packageManager
                    val appInfo = pm.getApplicationInfo(file.name, 0)
                    Glide.with(holder.itemView.context)
                        .load(pm.getApplicationIcon(appInfo))
                        .into(holder.icon)
                    appIconLoaded = true
                } catch (e: Exception) {
                    // App não instalado, usa ícone de pasta normal
                }
            }

            if (!appIconLoaded) {
                holder.icon.setImageResource(R.drawable.ic_folder)
            }
            
            val count = file.listFiles()?.size ?: 0
            holder.subtitle.text = when {
                count == 0 -> "Vazio"
                count == 1 -> "1 item"
                else -> "$count itens"
            }
        } else if (file.name.endsWith(".apk", ignoreCase = true)) {
            try {
                val pm = holder.itemView.context.packageManager
                val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
                if (info != null) {
                    info.applicationInfo.sourceDir = file.absolutePath
                    info.applicationInfo.publicSourceDir = file.absolutePath
                    
                    holder.icon.setBackgroundColor(0xFF2C2C2C.toInt())
                    Glide.with(holder.itemView.context)
                        .load(info.applicationInfo.loadIcon(pm))
                        .into(holder.icon)
                } else {
                    holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
                }
            } catch (e: Exception) {
                holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
            }
            val size = Formatter.formatShortFileSize(holder.itemView.context, file.length())
            val date = android.text.format.DateFormat.format("dd/MM/yyyy", Date(file.lastModified()))
            holder.subtitle.text = "$size • $date"
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
            val size = Formatter.formatShortFileSize(holder.itemView.context, file.length())
            val date = android.text.format.DateFormat.format("dd/MM/yyyy", Date(file.lastModified()))
            holder.subtitle.text = "$size • $date"
        }

        if (isSelectionMode) {
            if (selectedFiles.contains(file)) {
                holder.checkIcon.visibility = View.VISIBLE
                holder.itemView.setBackgroundColor(0xFF2C3E50.toInt())
            } else {
                holder.checkIcon.visibility = View.GONE
                holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        } else {
            holder.checkIcon.visibility = View.GONE
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        holder.iconContainer.setOnClickListener { onIconClick(file) }
        holder.textContainer.setOnClickListener { 
            if (isSelectionMode) onIconClick(file) else onItemClick(file) 
        }
        holder.itemView.setOnLongClickListener { 
            onLongClick(file)
            true
        }
    }

    override fun getItemCount() = files.size
}
