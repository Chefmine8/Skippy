fun main() {
    val checkRunnable = object : Runnable {
        override fun run() {
            println(this.javaClass.name)
            val lambda: (String) -> Unit = { result ->
                println("Inside lambda: ${this.javaClass.name}")
            }
            lambda("test")
        }
    }
    checkRunnable.run()
}
