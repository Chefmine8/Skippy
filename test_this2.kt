import android.webkit.ValueCallback

class WebView {
    fun evaluateJavascript(script: String, cb: ValueCallback<String>) { cb.onReceiveValue("test") }
    var isAttachedToWindow = true
}

class Handler {
    fun postDelayed(r: Runnable, delay: Long) {
        println("postDelayed called with Runnable: " + r.javaClass.name)
    }
}

fun main() {
    val webView = WebView()
    webView.apply {
        var tokenDelivered = false
        val handler = Handler()
        val checkRunnable = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow || tokenDelivered) return
                evaluateJavascript("script") { result ->
                    if (!tokenDelivered && isAttachedToWindow) {
                        handler.postDelayed(this, 1000)
                    }
                }
            }
        }
        checkRunnable.run()
    }
}
