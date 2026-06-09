package ru.lavrinenko.minio.cleaner.service

import ru.lavrinenko.minio.cleaner.conf.AppConfig
import ru.lavrinenko.minio.cleaner.conf.datasources.DbClient
import ru.lavrinenko.minio.cleaner.conf.datasources.MinioClient
import ru.lavrinenko.minio.cleaner.util.formatMillisWithDecimals
import ru.lavrinenko.minio.cleaner.util.toJson
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import java.sql.Statement
import kotlin.system.measureTimeMillis

/**
 * Сервис для удаления пользователей из базы данных.
 *
 * Данный класс предоставляет функциональность для массового удаления пользователей
 * на основе заданного SQL-запроса. Удаление выполняется постранично, с размером страницы,
 * определённым в конфигурации приложения (`AppConfig.dbQueryPageSize`).
 *
 * Для каждого пользователя из результата запроса:
 * - извлекается электронная почта;
 * - вызывается хранимая функция базы данных `jgutil.f_delete_user`, передающая email как параметр;
 * - при успешном удалении производится логирование информации о процессе;
 * - при возникновении ошибки — логируется сообщение, и исключение пробрасывается выше.
 *
 * Поддерживается подстановка параметров в SQL-запрос (например, `$ignoringJagaProjectIds`)
 * на основе значений из конфигурации.
 *
 * Логирование включает:
 * - начало и завершение процесса удаления;
 * - используемый SQL-запрос (если включено `AppConfig.showSql`);
 * - детали обработки каждой страницы;
 * - время, затраченное на удаление каждого пользователя.
 *
 * @see deleteUsersFromDB
 */
@Suppress("SqlSourceToSinkFlow")
class UserDeleteService {
    private val log = LoggerFactory.getLogger(UserDeleteService::class.java)

    /**
     * Удаляет пользователей из базы данных на основе переданного SQL-запроса.
     *
     * Метод выполняет постраничное выполнение SQL-запроса для получения списка пользователей,
     * подлежащих удалению. Для каждого пользователя из результата запроса:
     * - извлекается email;
     * - вызывается функция удаления пользователя `jgutil.f_delete_user` с передачей email;
     * - при успешном удалении логируется информация об этом;
     * - при возникновении ошибки логируется сообщение об ошибке, и исключение пробрасывается дальше.
     *
     * Выборка данных происходит с пагинацией, размер страницы определяется параметром `AppConfig.dbQueryPageSize`.
     * Процесс продолжается до тех пор, пока количество возвращённых строк равно размеру страницы.
     *
     * @param queryString SQL-запрос для выборки пользователей, подлежащих удалению.
     *                    Может содержать плейсхолдер `$ignoringJagaProjectIds`, который будет заменён
     *                    на соответствующее значение из конфигурации.
     */
    fun deleteUsersFromDB(queryString: String) {
        log.info("=======( Start deleting users from DB )==================================================================")
        if (AppConfig.showSql) log.info("The query used:\n$queryString")
        val query = queryString
            .replace("\$ignoringJagaProjectIds", AppConfig.ignoringJagaProjectIds)
        val statement = DbClient.getTasksConnection().createStatement()
        val delUserConnection = DbClient.getTasksConnection()
        delUserConnection.autoCommit = true
        val delUserStatement = delUserConnection.createStatement()
        var count = 0
        val pageSize = AppConfig.dbQueryPageSize
        do { // Получение данных из БД.
            var rows = 0
            var row = emptyMap<String, Any>()
            val rs = statement.executeQuery("$query\noffset $count limit $pageSize")
            val meta = rs.metaData
            val columnCount = meta.columnCount
            log.info("Page processing ---> start")
            while (rs.next()) {
                try {
                    row = (1..columnCount).associate { i ->
                        meta.getColumnLabel(i) to rs.getObject(i)
                    }
                    val email = row["email"] as String
                    val elapsedTime = measureTimeMillis {
                        delUserStatement.executeQuery("select * from jgutil.f_delete_user('$email')")
                        log.info("User deleted ---> {}", row.toJson())
                    }
                    log.info("Time spent on deletion ---> ${elapsedTime.formatMillisWithDecimals()}")
                } catch (e: Exception) {
                    log.error("An error occurred while deleting the user ---> {}", row.toJson())
                    continue
                }
                rows++
                log.info("Row number: ${count + rows}")
            }
            log.info("Page processing ---> end.")
            log.info("Processed $rows rows.")
            count += rows
        } while (rows == pageSize)
        if (count == 0) log.warn("No users found in DB") else log.info("Deleted $count users from DB")
        log.info("=======( End deleting users from DB )====================================================================")
    }
}