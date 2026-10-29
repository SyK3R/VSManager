package com.vsmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(private val files: List<File>) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.folderIcon)
        val name: TextView = view.findViewById(R.id.fileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.name
        
        // Se for pasta, usa ícone de pasta. Se for arquivo, usa outro ícone futuramente.
        if (file.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
        } else {
            // Usa o mesmo ícone por enquanto para não quebrar o código
            holder.icon.setImageResource(R.drawable.ic_folder) 
        }
    }

    override fun getItemCount() = files.size
}
