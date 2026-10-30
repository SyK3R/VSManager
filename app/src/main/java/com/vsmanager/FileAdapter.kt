package com.vsmanager

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class FileAdapter(
    private val files: List<File>,
    private val isSelectionMode: Boolean,
    private val selectedFiles: Set<File>,
    private val onItemClick: (File) -> Unit,
    private val onIconClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val iconBox: View = v.findViewById(R.id.iconBox)
        val icon: ImageView = v.findViewById(R.id.icon)
        val check: ImageView = v.findViewById(R.id.check)
        val name: TextView = v.findViewById(R.id.name)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
    }

    companion object {
        private val exec = Executors.newFixedThreadPool(3)
        private val main = Handler(Looper.getMainLooper())
        private val cache = LruCache<String, Drawable>(150)
        private val missing = ConcurrentHashMap.newKeySet<String>()
        private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private val imgExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "mp4", "mkv", "3gp", "webm")
        private val zipExt = setOf("zip", "rar", "7z", "tar", "gz")
        private val audioExt = setOf("mp3", "wav", "ogg", "flac", "m4a")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = files.size

    private fun tinted(h: ViewHolder, res: Int, color: Int) {
        h.icon.setImageResource(res)
        h.icon.imageTintList = ColorStateList.valueOf(color)
    }

    private fun loadLater(h: ViewHolder, key: String, loader: () -> Drawable?) {
        cache.get(key)?.let { h.icon.imageTintList = null; h.icon.setImageDrawable(it); return }
        if (missing.contains(key)) return
        exec.execute {
            val d = try { loader() } catch (e: Exception) { null }
            if (d == null) { missing.add(key); return@execute }
            cache.put(key, d)
            main.post {
                if (h.icon.tag == key) { h.icon.imageTintList = null; h.icon.setImageDrawable(d) }
            }
        }
    }

    private fun apkIcon(ctx: Context, f: File): Drawable? {
        val pm = ctx.packageManager
        val app = pm.getPackageArchiveInfo(f.absolutePath, 0)?.applicationInfo ?: return null
        app.sourceDir = f.absolutePath; app.publicSourceDir = f.absolutePath
        return app.loadIcon(pm)
    }

    private fun isPackageFolder(f: File): Boolean {
        val p = f.parentFile ?: return false
        return p.name in setOf("data", "obb", "media") && p.parentFile?.name == "Android"
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        val file = files[position]
        val ctx = h.itemView.context
        val key = file.path
        val ext = file.extension.lowercase()

        h.name.text = file.name
        h.icon.tag = key
        Glide.with(ctx).clear(h.icon)
        h.icon.imageTintList = null

        when {
            file.isDirectory -> {
                tinted(h, R.drawable.ic_folder, 0xFFD3D3D3.toInt())
                h.subtitle.text = ""
                exec.execute {
                    val n = file.listFiles()?.size
                    main.post {
                        if (h.icon.tag == key) h.subtitle.text = when {
                            n == null -> "Restrito"
                            n == 0 -> "Vazio"
                            n == 1 -> "1 item"
                            else -> "$n itens"
                        }
                    }
                }
                if (isPackageFolder(file)) {
                    loadLater(h, key) { ctx.packageManager.getApplicationIcon(file.name) }
                }
            }
            else -> {
                h.subtitle.text = "${Formatter.formatShortFileSize(ctx, file.length())} • ${dateFmt.format(Date(file.lastModified()))}"
                when {
                    ext == "apk" -> {
                        tinted(h, R.drawable.ic_file, 0xFF7FD67F.toInt())
                        loadLater(h, key) { apkIcon(ctx, file) }
                    }
                    ext in imgExt -> Glide.with(ctx).load(file)
                        .transform(CenterCrop(), RoundedCorners(24))
                        .placeholder(R.drawable.ic_file).into(h.icon)
                    ext in zipExt -> h.icon.setImageResource(R.drawable.ic_zip_file)
                    ext in audioExt -> tinted(h, R.drawable.ic_file, 0xFFBA8CFF.toInt())
                    ext == "pdf" -> tinted(h, R.drawable.ic_file, 0xFFFF6B6B.toInt())
                    else -> tinted(h, R.drawable.ic_file, 0xFF8FA8FF.toInt())
                }
            }
        }

        val sel = selectedFiles.contains(file)
        h.itemView.setBackgroundResource(if (sel) R.drawable.bg_item_selected else 0)
        h.check.setImageDrawable(pathIcon(Icons.CHECK).apply { setTint(0xFF111111.toInt()) })
        h.check.visibility = if (sel) View.VISIBLE else View.GONE
        h.icon.alpha = if (sel) 0.2f else 1f

        h.itemView.setOnClickListener { if (isSelectionMode) onIconClick(file) else onItemClick(file) }
        h.itemView.setOnLongClickListener { onLongClick(file); true }
        h.iconBox.setOnClickListener { onIconClick(file) }
        h.iconBox.setOnLongClickListener { onLongClick(file); true }
    }
}
