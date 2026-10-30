package com.vsmanager

import android.content.Context
import android.os.Environment
import java.io.File

object AppSettings {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun showHidden(c: Context) = p(c).getBoolean("hidden", false)
    fun setShowHidden(c: Context, v: Boolean) = p(c).edit().putBoolean("hidden", v).apply()

    fun thumbs(c: Context) = p(c).getBoolean("thumbs", true)
    fun foldersFirst(c: Context) = p(c).getBoolean("foldersFirst", true)
    fun confirmDelete(c: Context) = p(c).getBoolean("confirmDelete", true)
    fun sortBy(c: Context) = p(c).getString("sortBy", "name") ?: "name"
    fun sortAsc(c: Context) = p(c).getBoolean("sortAsc", true)

    fun startDir(c: Context): File {
        val root = Environment.getExternalStorageDirectory()
        val path = p(c).getString("startDir", "") ?: ""
        if (path.isEmpty()) return root
        val f = File(path)
        return if (f.isDirectory) f else root
    }

    fun sort(c: Context, files: List<File>): List<File> {
        var cmp: Comparator<File> = when (sortBy(c)) {
            "size" -> compareBy<File> { if (it.isDirectory) 0L else it.length() }
            "date" -> compareBy<File> { it.lastModified() }
            else -> compareBy<File> { it.name.lowercase() }
        }
        if (!sortAsc(c)) cmp = cmp.reversed()
        return if (foldersFirst(c)) files.sortedWith(compareBy<File> { !it.isDirectory }.then(cmp))
        else files.sortedWith(cmp)
    }

    fun reset(c: Context) = p(c).edit().clear().apply()
}
