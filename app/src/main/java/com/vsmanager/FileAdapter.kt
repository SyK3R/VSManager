package com.vsmanager

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
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
        val check: ImageView = view.findViewById(R.id.checkIcon)
        val textContainer: LinearLayout = view.findViewById(R.id.textContainer)
        val name: TextView = view.findViewById(R.id.fileName)
        val subtitle: TextView = view.findViewById(R.id.fileSubtitle)
    }

    private val apkCache = android.util.LruCache<String, Drawable>(80)
    private val imgExt = setOf("jpg","jpeg","png","webp","gif","bmp","mp4","mkv","3gp","webm")

    private fun tint(h: ViewHolder, res: Int, color: Int) {
        h.icon.setImageResource(res)
        h.icon.imageTintList = ColorStateList.valueOf(color)
    }

    private fun loadApkIcon(ctx: Context, f: File): Drawable? = try {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(f.absolutePath, 0)
        info?.applicationInfo?.let {
            it.sourceDir = f.absolutePath; it.publicSourceDir = f.absolutePath
            it.loadIcon(pm)
        }
    } catch (e: Exception) { null }

    private fun bindIcon(h: ViewHolder, file: File) {
        val ctx = h.itemView.context
        Glide.with(ctx).clear(h.icon)
        h.icon.imageTintList = null
        val ext = file.extension.lowercase()
        when {
            file.isDirectory -> tint(h, R.drawable.ic_folder, 0xFFD3D3D3.toInt())
            ext == "apk" -> {
                val d = apkCache.get(file.path) ?: loadApkIcon(ctx, file)?.also { apkCache.put(file.path, it) }
                if (d != null) h.icon.setImageDrawable(d) else tint(h, R.drawable.ic_file, 0xFF7FD67F.toInt())
            }
            ext in imgExt -> Glide.with(ctx).load(file).centerCrop()
                .transform(RoundedCorners(24)).placeholder(R.drawable.ic_file).into(h.icon)
            ext in setOf("zip","rar","7z","tar","gz") -> tint(h, R.drawable.ic_file, 0xFFFFB74D.toInt())
            ext in setOf("mp3","wav","ogg","flac","m4a") -> tint(h, R.drawable.ic_file, 0xFFBA8CFF.toInt())
            ext == "pdf" -> tint(h, R.drawable.ic_file, 0xFFFF6B6B.toInt())
            else -> tint(h, R.drawable.ic_file, 0xFF8FA8FF.toInt())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.name
        bindIcon(holder, file)

        if (file.isDirectory) {
            val count = file.listFiles()?.size ?: 0
            holder.subtitle.text = when {
                count == 0 -> "Vazio"
                count == 1 -> "1 item"
                else -> "$count itens"
            }
        } else {
            val size = Formatter.formatShortFileSize(holder.itemView.context, file.length())
            val date = android.text.format.DateFormat.format("dd/MM/yyyy", Date(file.lastModified()))
            holder.subtitle.text = "$size • $date"
        }

        val selected = selectedFiles.contains(file)
        holder.itemView.setBackgroundResource(if (selected) R.drawable.bg_item_selected else 0)
        holder.check.setImageDrawable(pathIcon(Icons.CHECK).apply { setTint(0xFF111111.toInt()) })
        holder.check.visibility = if (selected) View.VISIBLE else View.GONE
        holder.icon.alpha = if (selected) 0.2f else 1f

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
