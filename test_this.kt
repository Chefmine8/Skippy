class WebView {
    fun evaluateJavascript(script: String, cb: (String?) -> Unit) { cb("test") }
    var isAttachedToWindow = true
}

class Handler {
    fun postDelayed(r: Runnable, delay: Long) {}
}

fun test() {
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
    }
}
