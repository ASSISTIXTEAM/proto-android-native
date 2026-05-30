package org.assistix.proto.nativeapp.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallVideoRenderer(
    track: VideoTrack?,
    eglContext: EglBase.Context?,
    mirror: Boolean,
    modifier: Modifier = Modifier,
) {
    if (track == null || eglContext == null) return
    key(track.id()) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    setZOrderMediaOverlay(true)
                    init(eglContext, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                    setMirror(mirror)
                    track.addSink(this)
                }
            },
            update = { view -> view.setMirror(mirror) },
            onRelease = { view ->
                runCatching { track.removeSink(view) }
                runCatching { view.release() }
            },
        )
    }
}
