package dev.ibylich.mpclipboard;

import dev.ibylich.mpclipboard.IMPClipboardCallback;

/** Contract implemented by dev.ibylich.mpclipboard.MPClipboardService. */
oneway interface IMPClipboardService {
    void onNewLocalText(String text);
    void registerRemoteTextCallback(IMPClipboardCallback callback);
    void unregisterRemoteTextCallback(IMPClipboardCallback callback);
}
