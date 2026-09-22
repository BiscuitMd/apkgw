package com.system.update

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin aktif", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Nonaktifkan admin akan menonaktifkan fitur keamanan sistem."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Admin nonaktif", Toast.LENGTH_SHORT).show()
    }
}
