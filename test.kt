fun main() {
    val a = if (true) {
        null
    } else null ?: "fallback"
    println("Result: $a")
}
