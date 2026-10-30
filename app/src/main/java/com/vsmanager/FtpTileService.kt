package com.vsmanager

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class FtpTileService : TileService() {

    override fun onStartListening() { update() }

    override fun onClick() {
        val i = Intent(this, FtpService::class.java)
        if (FtpService.running) stopService(i) else ContextCompat.startForegroundService(this, i)
    }

    private fun update() {
        val t = qsTile ?: return
        t.label = "Servidor FTP"
        t.state = if (FtpService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (FtpService.running) FtpService.address else null
        t.updateTile()
    }
}
