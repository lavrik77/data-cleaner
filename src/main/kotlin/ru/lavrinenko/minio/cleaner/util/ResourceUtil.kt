package com.rit.crossdev.jaga.minio.cleaner.util


object ResourceUtil {

    /**
     * Загружает содержимое SQL-запроса из ресурсов приложения.
     *
     * @param sourceName имя файла с SQL-запросом, расположенного в classpath (например, `articleProjectsQuery.sql`).
     * @return строка с содержимым файла.
     * @throws IllegalStateException если файл не найден в ресурсах приложения.
     */
    fun loadStringResource(sourceName: String): String =
        Thread.currentThread().contextClassLoader.getResource(sourceName)?.readText()
            ?: throw IllegalStateException("File $sourceName.sql not found in resources")
}

fun Long.formatMillisWithDecimals(): String {
    val minutes = this / 60_000
    val remainingMillis = this % 60_000
    val seconds = remainingMillis / 1_000
    val decimals = (remainingMillis % 1_000) / 100  // десятые доли
    return String.format("%02dmin %02d.%02dsec", minutes, seconds, decimals)
}