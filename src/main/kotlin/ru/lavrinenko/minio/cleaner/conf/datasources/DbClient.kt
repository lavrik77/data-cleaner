package ru.lavrinenko.minio.cleaner.conf.datasources

import ru.lavrinenko.minio.cleaner.conf.AppConfig
import ru.lavrinenko.minio.cleaner.conf.ssh.SshTunnel
import java.sql.Connection
import java.sql.DriverManager

/**
 * Клиент для работы с базами данных PostgreSQL.
 *
 * Предоставляет методы для получения соединений с базами данных задач и статей.
 * Поддерживает подключение через SSH-туннель при необходимости.
 *
 * При использовании SSH-туннеля устанавливается локальное проброс порта,
 * и соединение осуществляется через localhost. После завершения работы туннель закрывается.
 *
 * Также содержит вспомогательный метод для разбора JDBC URL.
 */
object DbClient {

    private val log = org.slf4j.LoggerFactory.getLogger(DbClient::class.java)

    /**
     * Возвращает соединение с базой данных задач.
     *
     * Если в настройках включено использование SSH-туннеля ([AppConfig.sshEnabled]),
     * соединение устанавливается через локальный порт, проброшенный посредством SSH.
     * В противном случае — прямое подключение к базе данных по указанному хосту и порту.
     *
     * @return объект [Connection] для взаимодействия с базой данных задач.
     */
    fun getTasksConnection(): Connection =
        if (AppConfig.sshEnabled) {
            connectToBase(AppConfig.dbTasksName, AppConfig.dbTasksUser, AppConfig.dbTasksPassword)
        } else {
            DriverManager.getConnection(
                "jdbc:postgresql://${AppConfig.dbHost}:${AppConfig.dbPort}/${AppConfig.dbTasksName}",
                AppConfig.dbTasksUser,
                AppConfig.dbTasksPassword
            )
        }

//    /**
//     * Возвращает соединение с базой данных статей.
//     *
//     * Если в настройках включено использование SSH-туннеля ([AppConfig.sshEnabled]),
//     * соединение устанавливается через локальный порт, проброшенный посредством SSH.
//     * В противном случае — прямое подключение к базе данных по указанному хосту и порту.
//     *
//     * @return объект [Connection] для взаимодействия с базой данных статей.
//     */
//    fun getArticlesConnection(): Connection =
//        if (AppConfig.sshEnabled) {
//            connectToBase(AppConfig.dbArticlesName, AppConfig.dbArticlesUser, AppConfig.dbArticlesPassword)
//        } else {
//            DriverManager.getConnection(
//                "jdbc:postgresql://${AppConfig.dbHost}:${AppConfig.dbPort}/${AppConfig.dbArticlesName}",
//                AppConfig.dbArticlesUser,
//                AppConfig.dbArticlesPassword
//            )
//        }

    /**
     * Устанавливает соединение с базой данных через SSH-туннель.
     *
     * Метод настраивает SSH-туннель, если он еще не установлен, и создает подключение
     * к указанной базе данных через локальный порт, проброшенный посредством SSH.
     *
     * @param dbName Имя базы данных, к которой необходимо подключиться.
     * @param userName Имя пользователя для аутентификации в базе данных.
     * @param userPass Пароль пользователя для аутентификации в базе данных.
     * @return Объект [Connection], представляющий соединение с базой данных.
     */
    private fun connectToBase(dbName: String, userName: String, userPass: String): Connection {
        establishTunnel()
        val jdbcUrl = "jdbc:postgresql://localhost:${AppConfig.sshDBLocalPort}/$dbName?connectTimeout=3000&socketTimeout=6000"
        log.info("Connecting to $jdbcUrl")
        return DriverManager.getConnection(
            jdbcUrl,
            userName,
            userPass
        )
    }

    /**
     * Устанавливает SSH-туннель, если он еще не активен.
     *
     * Проверяет, включено ли использование SSH-туннеля в конфигурации ([AppConfig.sshEnabled]).
     * Если да — инициирует установку туннеля через [SshTunnel.establishTunnel] и настраивает
     * проброс локального порта [AppConfig.sshDBLocalPort] на удаленный хост базы данных
     * ([AppConfig.dbHost]) и порт ([AppConfig.dbPort]).
     *
     * Данный метод вызывается перед подключением к базе данных через SSH.
     */
    private fun establishTunnel() {
        if (AppConfig.sshEnabled) {
            SshTunnel.establishTunnel()
            SshTunnel.addPortForwardingL(
                AppConfig.sshDBLocalPort,
                AppConfig.dbHost,
                AppConfig.dbPort
            )
        }
    }
}