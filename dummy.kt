fun test(fragment: String) {
    val fullToken = fragment.split("&").find { it.startsWith("id_token=") }?.substringAfter("=")
    val payload = fullToken?.split('.')?.getOrNull(1)
    println(payload)
}
