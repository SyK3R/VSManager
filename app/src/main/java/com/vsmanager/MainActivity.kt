package com.vsmanager

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var fab: FloatingActionButton
    private var isMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val folders = listOf(
            "Alarms", "Android", "Audiobooks", "DCIM", "Documents", 
            "Download", "MIUI", "Movies", "Music", "Notifications", 
            "Pictures", "Podcasts", "Recordings", "Ringtones", "Videos"
        )
        recyclerView.adapter = FileAdapter(folders)

        fab = findViewById(R.id.fab)
        setupFabAnimation()
    }

    private fun setupFabAnimation() {
        fab.setOnClickListener { view ->
            val rotation = if (isMenuOpen) -360f else 360f
            val animator = ObjectAnimator.ofFloat(view, "rotation", rotation)
            animator.duration = 1000
            animator.interpolator = LinearInterpolator()
            animator.start()

            isMenuOpen = !isMenuOpen
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Nova pasta")
        popup.menu.add("Novo arquivo")
        popup.menu.add("Mostrar ocultos")
        popup.menu.add("Multiseleção")
        popup.menu.add("Terminal")
        popup.show()
    }
}
