package dev.patrickgold.florisboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.os.RemoteException
import android.util.Log
import dev.ibylich.mpclipboard.IMPClipboardCallback
import dev.ibylich.mpclipboard.IMPClipboardService

/**
 * Connects FlorisBoard's system clipboard to the separate MPClipboard application.
 *
 * A missing or dead service binding is retried every five seconds. Android may reconnect an
 * existing binding first; a successful connection cancels the pending retry.
 */
class MPClipboardClient(context: Context) {
    companion object {
        private const val TAG = "MPClipboardClient"
        private const val SERVICE_PACKAGE = "dev.ibylich.mpclipboard"
        private const val SERVICE_CLASS = "dev.ibylich.mpclipboard.MPClipboardService"
        private const val REMOTE_CLIP_MARKER = "dev.ibylich.mpclipboard.REMOTE_CLIP"
        private const val REMOTE_CLIP_LABEL = "MPClipboard"
        private const val REBIND_DELAY_MS = 5_000L
    }

    private val appContext = context.applicationContext
    private val clipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: IMPClipboardService? = null
    private var startAttempted = false
    private var bindingRegistered = false
    private var lastProcessedClipTimestamp: Long? = null

    private val rebindRunnable = Runnable {
        if (service == null) {
            unbindIfNeeded()
            bind()
        }
    }

    private val remoteTextCallback = object : IMPClipboardCallback.Stub() {
        override fun onNewRemoteText(text: String?) {
            if (text.isNullOrEmpty()) {
                Log.w(TAG, "Ignoring empty remote clipboard text")
                return
            }
            mainHandler.post { setRemoteClipboardText(text) }
        }
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        sendCurrentClipboardText()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connectedService = IMPClipboardService.Stub.asInterface(binder)
            service = connectedService
            try {
                connectedService.registerRemoteTextCallback(remoteTextCallback)
                mainHandler.removeCallbacks(rebindRunnable)
            } catch (e: RemoteException) {
                service = null
                Log.e(TAG, "Failed to register the remote clipboard callback", e)
                scheduleRebind()
            } catch (e: RuntimeException) {
                service = null
                Log.e(TAG, "MPClipboard exposed an incompatible service", e)
                scheduleRebind()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            Log.e(TAG, "MPClipboard service disconnected")
            scheduleRebind()
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            Log.e(TAG, "MPClipboard binding died")
            scheduleRebind()
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            Log.e(TAG, "MPClipboard service returned no binder")
            scheduleRebind()
        }
    }

    fun start() {
        if (startAttempted) return
        startAttempted = true

        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        bind()
    }

    private fun bind() {
        val intent = Intent().setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
        try {
            bindingRegistered = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bindingRegistered) {
                Log.e(TAG, "Could not bind to $SERVICE_CLASS")
            }
        } catch (e: Exception) {
            bindingRegistered = false
            Log.e(TAG, "Could not bind to $SERVICE_CLASS", e)
        }
        if (service == null) {
            scheduleRebind()
        }
    }

    private fun scheduleRebind() {
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, REBIND_DELAY_MS)
    }

    private fun unbindIfNeeded() {
        if (!bindingRegistered) return
        try {
            appContext.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not release the previous MPClipboard binding", e)
        } finally {
            bindingRegistered = false
            service = null
        }
    }

    private fun sendCurrentClipboardText() {
        val clip = try {
            clipboardManager.primaryClip
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the system clipboard", e)
            return
        } ?: return

        val clipTimestamp = clip.description.timestamp
        if (clipTimestamp == lastProcessedClipTimestamp) return
        lastProcessedClipTimestamp = clipTimestamp

        if (clip.description.extras?.getBoolean(REMOTE_CLIP_MARKER, false) == true) return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).coerceToText(appContext)?.toString()
            ?.takeIf { it.isNotEmpty() } ?: return
        val connectedService = service
        if (connectedService == null) {
            Log.e(TAG, "Dropping copied text because MPClipboard is not connected")
            return
        }

        try {
            connectedService.onNewLocalText(text)
        } catch (e: RemoteException) {
            service = null
            Log.e(TAG, "Could not send copied text to MPClipboard", e)
            scheduleRebind()
        } catch (e: RuntimeException) {
            service = null
            Log.e(TAG, "MPClipboard rejected copied text", e)
            scheduleRebind()
        }
    }

    private fun setRemoteClipboardText(text: String) {
        val clip = ClipData.newPlainText(REMOTE_CLIP_LABEL, text).also {
            it.description.extras = PersistableBundle().apply {
                putBoolean(REMOTE_CLIP_MARKER, true)
            }
        }
        try {
            clipboardManager.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Could not put remote text into the system clipboard", e)
        }
    }
}
