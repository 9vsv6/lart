package app.lart

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
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
    webView.addJavascriptInterface(LartBridge(), "LartAndroid")
  }

  /** Called from the page (window.LartAndroid). Only touches window chrome. */
  inner class LartBridge {
    @JavascriptInterface
    fun setPlayerMode(on: Boolean) {
      runOnUiThread { applyPlayerMode(on) }
    }
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
