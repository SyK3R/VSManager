package com.vsmanager

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Date

class FileAdapter(
    private val files: List<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.folderIcon)
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

        if (file.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            holder.subtitle.text = "Pasta"
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
            val size = Formatter.formatShortFileSize(holder.itemView.context, file.length())
            val date = android.text.format.DateFormat.format("dd/MM/yyyy", Date(file.lastModified()))
            holder.subtitle.text = "$size • $date"
        }

        // Ações
        holder.itemView.setOnClickListener { onClick(file) }
        holder.itemView.setOnLongClickListener { 
            onLongClick(file)
            true
        }
    }

    override fun getItemCount() = files.size
}
