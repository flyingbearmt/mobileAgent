package com.example.mobileagent.entry

import android.service.quicksettings.TileService

class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()

        val intent = ClipboardEntryActivity.createIntent(
            context = this,
            clipboardText = null,
            sourceApp = "clipboard",
        )

        if (isLocked) {
            unlockAndRun {
                startActivityAndCollapse(intent)
            }
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
