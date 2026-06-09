package ru.lavrinenko.minio.cleaner.service

import ru.lavrinenko.minio.cleaner.conf.AppConfig
import ru.lavrinenko.minio.cleaner.conf.datasources.DbClient
import ru.lavrinenko.minio.cleaner.conf.datasources.MinioClient
import ru.lavrinenko.minio.cleaner.util.toJson
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import java.sql.Statement

/**
 * Сервис для удаления файлов из MinIO на основе данных, полученных из различных модулей (например, Tasks, Articles и др.).
 *
 * Основная функциональность класса — выполнение SQL-запросов к соответствующим базам данных, извлечение путей к файлам
 * и последующее удаление этих файлов из хранилища MinIO при их наличии.
 *
 * Для каждого модуля предоставляется отдельный метод (например, [deleteTasksFilesFromMinio]), который:
 * - подставляет конфигурационные параметры в SQL-запрос;
 * - выполняет запрос к БД;
 * - обрабатывает результаты с помощью метода [processFiles].
 *
 * Метод [processFiles] отвечает за:
 * - пагинацию выборки данных из БД;
 * - проверку существования файла в MinIO;
 * - удаление файла при наличии;
 * - логирование всех этапов обработки.
 *
 * В случае, если файл не найден в MinIO (ошибка 404), исключение не пробрасывается, а логируется как предупреждение.
 * Другие ошибки MinIO пробрасываются дальше.
 *
 * @see deleteTasksFilesFromMinio
 * @see processFiles
 */
@Suppress("SqlSourceToSinkFlow")
class AttachmentDeleteService {
    private val log = LoggerFactory.getLogger(AttachmentDeleteService::class.java)

//    /**
//     * Удаляет файлы статей из MinIO на основе переданного SQL-запроса.
//     *
//     * Метод выполняет следующие шаги:
//     * 1. Логирует начало обработки модуля "Articles".
//     * 2. Подставляет конфигурационные параметры в SQL-запрос (такие, как хост, порт, логин, пароль и др.).
//     * 3. Создаёт Statement для соединения с базой данных статей.
//     * 4. Передаёт запрос на обработку файлов в метод `processFiles`, который проверяет наличие файлов в MinIO и удаляет их.
//     * 5. Логирует завершение обработки.
//     *
//     * В процессе обработки:
//     * - файлы сначала проверяются на существование в MinIO;
//     * - при наличии — удаляются;
//     * - все действия логируются.
//     *
//     * @param queryString SQL-запрос для выборки записей с путями к файлам.
//     *                    Должен возвращать столбец "path" с путём к файлу в MinIO.
//     *                    Может содержать плейсхолдеры, такие как `$dbHost`, `$dbUser`, `$dbPassword`,
//     *                    `$dbName`, `$dbPort`, `$ignoringJagaProjectIds`, которые будут заменены
//     *                    на значения из конфигурации.
//     * @see processFiles
//     */
//    fun deleteArticlesFilesFromMinio(queryString: String) {
//        log.info("=======( Start processing files from Articles module )====================================================")
//        if (AppConfig.showSql) log.info("The query used:\n$queryString")
//        val query = queryString
//            .replace("\$dbHost", AppConfig.dbHost)
//            .replace("\$dbUser", AppConfig.dbArticlesUser)
//            .replace("\$dbPassword", AppConfig.dbArticlesPassword)
//            .replace("\$dbName", AppConfig.dbStorageName)
//            .replace("\$dbPort", AppConfig.dbPort.toString())
//            .replace("\$ignoringJagaProjectIds", AppConfig.ignoringJagaProjectIds)
//        val stmt = DbClient.getArticlesConnection().createStatement()
//        processFiles(stmt, query)
//        log.info("=======( End processing files from Articles module )======================================================")
//    }

    /**
     * Удаляет файлы задач из MinIO на основе переданного SQL-запроса.
     *
     * Метод выполняет следующие шаги:
     * 1. Логирует начало обработки модуля "Tasks".
     * 2. Подставляет конфигурационные параметры в SQL-запрос (в данном случае только `$ignoringJagaProjectIds`).
     * 3. Создаёт Statement для соединения с базой данных задач.
     * 4. Передаёт запрос на обработку файлов в метод `processFiles`, который проверяет наличие файлов в MinIO и удаляет их.
     * 5. Логирует завершение обработки.
     *
     * В процессе обработки:
     * - файлы сначала проверяются на существование в MinIO;
     * - при наличии — удаляются;
     * - все действия логируются.
     *
     * @param queryString SQL-запрос для выборки записей с путями к файлам.
     *                    Должен возвращать столбец "path" с путём к файлу в MinIO.
     *                    Может содержать плейсхолдер `$ignoringJagaProjectIds`, который будет заменён
     *                    на соответствующее значение из конфигурации.
     * @see processFiles
     */
    fun deleteTasksFilesFromMinio(queryString: String) {
        log.info("=======( Start processing files from Tasks module )=======================================================")
        if (AppConfig.showSql) log.info("The query used:\n$queryString")
        val query = queryString
            .replace("\$ignoringJagaProjectIds", AppConfig.ignoringJagaProjectIds)
        val stmt = DbClient.getTasksConnection().createStatement()
        processFiles(stmt, query)
        log.info("=======( End processing files from Tasks module )=========================================================")

    }

    /**
     * Обрабатывает файлы, полученные из базы данных, проверяя их наличие в MinIO и удаляя при наличии.
     *
     * Метод выполняет выборку данных из БД с использованием указанного SQL-запроса с пагинацией.
     * Для каждой строки результата:
     * - извлекается путь к файлу (поле "path");
     * - проверяется существование файла в хранилище MinIO;
     * - если файл существует, он удаляется;
     * - логируются соответствующие события (существование, удаление или отсутствие файла).
     *
     * В случае ошибки, отличной от отсутствия файла (404), исключение пробрасывается дальше.
     *
     * @param stmt Объект Statement для выполнения SQL-запроса.
     * @param query SQL-запрос для получения данных из базы данных.
     * @return Количество обработанных записей.
     */
    private fun processFiles(stmt: Statement, query: String, secondQuery: String? = null): Int {
        var count = 0
        do { // Получение данных из БД.
            val pageSize = AppConfig.dbQueryPageSize
            var rows = 0
            val rs = stmt.executeQuery("$query\noffset $count limit $pageSize")
            val meta = rs.metaData
            val columnCount = meta.columnCount
            while (rs.next()) {
                val row = (1..columnCount).associate { i ->
                    meta.getColumnLabel(i) to rs.getObject(i)
                }
                try {
                    val filePath = row["path"]?.toString()
                    val attachmentId = row["attachment_id"]?.toString()
                    // Проверка на существование файла в MinIO
                    MinioClient.client.statObject(
                        StatObjectArgs.builder()
                            .bucket(AppConfig.minioBucket)
                            .`object`(filePath)
                            .build()
                    )
                    log.info("File exists ---> {}", row.toJson())
                    // Удаление файла из MinIO
                    MinioClient.client.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket(AppConfig.minioBucket)
                            .`object`(filePath)
                            .build()
                    )
                    if (attachmentId != null) {
                        stmt.execute(secondQuery?.replace("\$attachmentId", attachmentId) ?: "")
                        log.info("Attachment with id $attachmentId was deleted from DB")
                    }
                    log.info("File deleted ---> {}", row.toJson())
                } catch (e: ErrorResponseException) {
                    // Если файл не найден (404), логируем отсутствие файла
                    if (e.response().code == 404)
                        log.error("File not found ---> {}", row.toJson())
                    else throw e
                }
                rows++
            }
            count += rows
        } while (rows == pageSize)
        if (count == 0) log.warn("No files found in MinIO") else log.info("Processed $count files from MinIO")
        return count
    }
}