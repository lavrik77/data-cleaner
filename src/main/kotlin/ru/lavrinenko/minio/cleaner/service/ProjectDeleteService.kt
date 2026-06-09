package com.rit.crossdev.jaga.minio.cleaner.service

import com.rit.crossdev.jaga.minio.cleaner.conf.AppConfig
import com.rit.crossdev.jaga.minio.cleaner.conf.HttpClientConfig
import com.rit.crossdev.jaga.minio.cleaner.conf.datasources.DbClient
import com.rit.crossdev.jaga.minio.cleaner.util.formatMillisWithDecimals
import com.rit.crossdev.jaga.minio.cleaner.util.toJson
import okhttp3.Credentials
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import kotlin.system.measureTimeMillis

/**
 * Сервис для удаления проектов из базы данных и связанных систем.
 *
 * Основная функциональность класса — выполнение безопасного удаления проектов на основе заданного SQL-запроса.
 * Удаление включает два этапа:
 * 1. Физическое удаление проекта из основной базы данных с помощью вызова функции `jgutil.f_del_project()`.
 * 2. Удаление соответствующего пространства (проекта) в системе Articles через HTTP API.
 *
 * Проекты обрабатываются пакетами согласно размеру страницы, указанному в конфигурации (`AppConfig.dbQueryPageSize`).
 * Некоторые проекты могут быть исключены из удаления — их идентификаторы задаются в конфигурации (`AppConfig.ignoringJagaProjectIds`).
 *
 * Для каждого удаляемого проекта в лог записываются:
 * - информация о проекте (ID и название),
 * - время выполнения операций удаления из БД и Articles,
 * - возможные ошибки и предупреждения.
 *
 * @see deleteProjectsFromDB
 * @see deleteProjectFromArticles
 *
 * @author GigaCode
 */
@Suppress("SqlSourceToSinkFlow")
class ProjectDeleteService {
    private val log = LoggerFactory.getLogger(ProjectDeleteService::class.java)

    /**
     * Удаляет проекты из базы данных и связанных систем на основе заданного SQL-запроса.
     *
     * Метод выполняет выборку проектов с использованием переданного SQL-запроса, подставляя
     * идентификаторы проектов, исключённых из удаления (`AppConfig.ignoringJagaProjectIds`).
     * Для каждого найденного проекта вызывается функция `jgutil.f_del_project()` в БД,
     * которая осуществляет физическое удаление проекта.
     *
     * После успешного удаления из основной базы данных, проект также удаляется из системы Articles
     * через HTTP-запрос к внешнему API с использованием идентификатора пространства (`articles_space_id`).
     *
     * Удаление происходит пакетами согласно размеру страницы, указанному в конфигурации
     * (`AppConfig.dbQueryPageSize`). Процесс повторяется до тех пор, пока все подходящие записи не будут обработаны.
     *
     * В лог выводится информация о каждом удаляемом проекте, времени выполнения операций удаления
     * из БД и Articles, а также возможные предупреждения и ошибки.
     *
     * @param queryString SQL-запрос для выборки удаляемых проектов. Должен включать `$ignoringJagaProjectIds`,
     *                    который будет заменён на список игнорируемых ID из конфигурации.
     *
     * @throws Exception при возникновении ошибки во время выполнения запроса к БД
     *
     * @see deleteProjectFromArticles
     * @see AppConfig.dbQueryPageSize
     * @see AppConfig.ignoringJagaProjectIds
     */
    fun deleteProjectsFromDB(queryString: String, direction: String) {
        log.info("=======( Start deleting projects from DB )===============================================================")
        if (AppConfig.showSql) log.info("The query used:\n$queryString")
        val query = queryString
            .replace("\$ignoringJagaProjectIds", AppConfig.ignoringJagaProjectIds)
            .replace("\$direction", direction)
        log.info("Selected direction = $direction")
        val statement = DbClient.getTasksConnection().createStatement()
        val delProjectStatement = DbClient.getTasksConnection().createStatement()
        delProjectStatement.connection.autoCommit = true
        var count = 0
        val pageSize = AppConfig.dbQueryPageSize
        do { // Получение данных из БД.
            var rows = 0
            try {
                val rs = statement.executeQuery("$query\noffset 0 limit $pageSize")
                val meta = rs.metaData
                val columnCount = meta.columnCount
                var setContinue: Boolean
                while (rs.next()) {
                    setContinue = false
                    val row = (1..columnCount).associate { i ->
                        meta.getColumnLabel(i) to rs.getObject(i)
                    }
                    val jagaProjectId = row["jaga_project_id"] as Long
                    val jagaProjectName = row["jaga_project_name"] as String
                    log.info("Project to delete ---> jagaProjectId: $jagaProjectId, name: $jagaProjectName")
                    var elapsedTime = measureTimeMillis {
                        val deleteProjectRs =
                            delProjectStatement.executeQuery("select * from jgutil.f_del_project($jagaProjectId, 0)")
                        if (deleteProjectRs.next()) {
                            val projectDeleted = deleteProjectRs.getBoolean(1)
                            if (projectDeleted) {
                                log.info("Project deleted ---> {}", row.toJson())
                            } else {
                                log.warn("Function jgutil.f_del_project() returned false, so the Project is not deleted")
                                log.warn("Project not deleted ---> {}", row.toJson())
                                setContinue = true
                            }
                        } else {
                            log.warn("Error executing function jgutil.f_del_project($jagaProjectId, 0)")
                            setContinue = true
                        }
                    }
                    log.info("Time spent on deletion from DB: ---> ${elapsedTime.formatMillisWithDecimals()}")
                    if (setContinue) {
                        log.warn("Processing the next project")
                        continue
                    }
                    // Удаление из Articles
                    val articlesSpaceId = row["articles_space_id"] as Long?
                    elapsedTime = measureTimeMillis {
                        if (articlesSpaceId != null) {
                            deleteProjectFromArticles(articlesSpaceId)
                        } else {
                            log.warn("articles_space_id is null")
                        }
                    }
                    log.info("Time spent on deletion from Articles: ---> ${elapsedTime.formatMillisWithDecimals()}")
                    rows++
                }
            } catch (e: Exception) {
                log.error("An error occurred while deleting.", e)
                continue
            }
            count += rows
        } while (rows == pageSize /*|| count < pageSize * 10*/)
        if (count == 0) log.warn("No records found in DB") else log.info("Deleted $count records from DB")
        log.info("=======( End deleting projects from DB )=================================================================")
    }

    /**
     * Удаляет проект из системы Articles по заданному идентификатору пространства.
     *
     * Выполняет HTTP DELETE запрос к API Articles для удаления проекта с указанным `spaceId`.
     * В заголовке авторизации используется базовая аутентификация, а также добавляется
     * заголовок с XSRF-токеном для валидации запроса. Таймаут запроса настраивается,
     * по умолчанию составляет 30 секунд.
     *
     * @param spaceId Идентификатор пространства (проекта) в системе Articles, который необходимо удалить.
     * @param timeout Время ожидания выполнения запроса. Значение по умолчанию — 30 секунд.
     *
     * @see HttpClientConfig.javaHttpClient
     * @see AppConfig.domain
     * @see AppConfig.username
     * @see AppConfig.password
     *
     * При успешном ответе (код 200–299) записывается информационное сообщение.
     * В случае ошибки или неудачного статуса — логируется ошибка вместе с телом ответа.
     * Также обрабатываются IOException и InterruptedException.
     */
    fun deleteProjectFromArticles(spaceId: Long, timeout: Duration = Duration.ofSeconds(300)) {
        val client = HttpClientConfig.javaHttpClient(timeout)
        val url = "${AppConfig.domain}/api/spaces/$spaceId"
        val encodedAuth = Credentials.basic(AppConfig.username, AppConfig.password)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", encodedAuth)
            .header("Cookie", "XSRF-TOKEN=${UUID.randomUUID()}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "AttachmentDeleteApplication/1.0.0 (Windows; Kotlin)")
            .timeout(timeout)
            .DELETE()
            .build()

        try {
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

            when (response.statusCode()) {
                in 200..299 -> {
                    log.info("Project with id $spaceId was deleted ---> status code \"${response.statusCode()}\", url: $url")
                }

                else -> {
                    log.error("Request: $request, headers: ${request.headers()}")
                    log.error("DELETE error ---> status code ${response.statusCode()}, url: $url, headers: ${response.headers()}, body: ${response.body()}")
                }
            }
        } catch (e: IOException) {
            log.error("IO error when deleting: ${e.message}")
        } catch (e: InterruptedException) {
            log.error("Deletion request aborted: ${e.message}")
        }
    }
}