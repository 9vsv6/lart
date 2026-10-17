package app.lart

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : TauriActivity() {
  private var playerMode = false
  private lateinit var content: View
  private var webView: WebView? = null
  private var nativePlayer: NativePlayer? = null

  private fun native(): NativePlayer {
    nativePlayer?.let { return it }
    val created = NativePlayer(this, content as ViewGroup) { json ->
      webView?.evaluateJavascript("window.__lartNative&&window.__lartNative($json)", null)
    }
    nativePlayer = created
    return created
  }

  /** The page draws the controls; make it see-through while native video plays under it. */
  fun setNativeVideoVisible(on: Boolean) {
    webView?.setBackgroundColor(if (on) Color.TRANSPARENT else Color.rgb(0x11, 0x12, 0x14))
  }

  override fun onPause() {
    super.onPause()
    nativePlayer?.onHostPause()
  }

  override fun onDestroy() {
    nativePlayer?.release()
    nativePlayer = null
    super.onDestroy()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    // Dark UI: light status and navigation bar icons on transparent bars.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    window.decorView.setBackgroundColor(Color.rgb(0x11, 0x12, 0x14))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
    }
    // Android 15 forces edge-to-edge, and older WebViews report no
    // env(safe-area-inset-*), so the page would draw under the status bar,
    // the gesture bar and the keyboard. Inset the content view instead.
    // While the player is up the bars are hidden and the video goes edge to
    // edge; only the camera cutout keeps its padding so controls stay clear.
    content = findViewById(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
      val types = if (playerMode) {
        WindowInsetsCompat.Type.displayCutout()
      } else {
        WindowInsetsCompat.Type.systemBars() or
          WindowInsetsCompat.Type.displayCutout() or
          WindowInsetsCompat.Type.ime()
      }
      val bars = insets.getInsets(types)
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      WindowInsetsCompat.CONSUMED
    }
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    this.webView = webView
    webView.addJavascriptInterface(LartBridge(), "LartAndroid")
  }

  /** Called from the page (window.LartAndroid): window chrome and the native player. */
  inner class LartBridge {
    @JavascriptInterface
    fun setPlayerMode(on: Boolean) {
      runOnUiThread { applyPlayerMode(on) }
    }

    @JavascriptInterface
    fun nativeLoad(url: String, headersJson: String, startMs: Double) {
      runOnUiThread { native().load(url, headersJson, startMs.toLong()) }
    }

    @JavascriptInterface
    fun nativePlay() { runOnUiThread { nativePlayer?.play() } }

    @JavascriptInterface
    fun nativePause() { runOnUiThread { nativePlayer?.pause() } }

    @JavascriptInterface
    fun nativeSeek(ms: Double) { runOnUiThread { nativePlayer?.seek(ms.toLong()) } }

    @JavascriptInterface
    fun nativeSetVolume(v: Double) { runOnUiThread { nativePlayer?.setVolume(v.toFloat()) } }

    @JavascriptInterface
    fun nativeSetRate(r: Double) { runOnUiThread { nativePlayer?.setRate(r.toFloat()) } }

    @JavascriptInterface
    fun nativeSelectAudio(id: String) { runOnUiThread { nativePlayer?.selectAudio(id) } }

    @JavascriptInterface
    fun nativeStop() { runOnUiThread { nativePlayer?.stop() } }
  }

  private fun applyPlayerMode(on: Boolean) {
    if (playerMode == on) return
    playerMode = on
    requestedOrientation = if (on) {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (on) {
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
    ViewCompat.requestApplyInsets(content)
  }
}
