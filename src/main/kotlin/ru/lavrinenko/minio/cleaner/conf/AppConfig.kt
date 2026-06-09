package com.rit.crossdev.jaga.minio.cleaner.conf

import java.util.Properties

/**
 * Конфигурационный класс для приложения, предоставляющий доступ к настройкам через переменные окружения или файл `application.properties`.
 *
 * Все параметры загружаются лениво при первом обращении. Если параметр не найден ни в переменных окружения,
 * ни в файле конфигурации, выбрасывается исключение.
 *
 * Поддерживает настройки:
 * - Подключения к базам данных (задачи, статьи, хранилище)
 * - Подключения к MinIO (объектное хранилище)
 * - SSH-туннелирование для безопасного доступа к базам данных и MinIO
 * - Список идентификаторов проектов Jaga, которые следует игнорировать
 * - Опцию отображения SQL-запросов
 */
class AppConfig private constructor() {
    companion object {
        /** Хост базы данных. */
        val dbHost: String by lazy { envOrProp("DB_HOST") }
        /** Порт базы данных. */
        val dbPort: Int by lazy { envOrProp("DB_PORT").toInt() }
        /** Размер страницы загружаемых данных. */
        val dbQueryPageSize: Int by lazy { envOrProp("DB_QUERY_PAGE_SIZE").toInt() }

        /** Имя базы данных задач. */
        val dbTasksName: String by lazy { envOrProp("TASKS_DB_NAME") }
        /** Имя пользователя для подключения к базе данных задач. */
        val dbTasksUser: String by lazy { envOrProp("TASKS_DB_USERNAME") }
        /** Пароль пользователя для подключения к базе данных задач. */
        val dbTasksPassword: String by lazy { envOrProp("TASKS_DB_PASSWORD") }

        /** URL-адрес MinIO (например, http://localhost:9000). */
        val minioEndpoint: String by lazy { envOrProp("MINIO_ENDPOINT") }
        /** Ключ доступа MinIO. */
        val minioAccessKey: String by lazy { envOrProp("MINIO_ACCESS_KEY") }
        /** Секретный ключ MinIO. */
        val minioSecretKey: String by lazy { envOrProp("MINIO_SECRET_KEY") }
        /** Название бакета в MinIO, с которым работает приложение. */
        val minioBucket: String by lazy { envOrProp("MINIO_BUCKET") }

        /** Включено ли SSH-туннелирование. */
        var sshEnabled: Boolean = envOrProp("SSH_ENABLED").toBoolean()
        /** Хост SSH-сервера. */
        val sshHost: String by lazy { envOrProp("SSH_HOST") }
        /** Порт SSH-сервера. */
        val sshPort: Int by lazy { envOrProp("SSH_PORT").toInt() }
        /** Имя пользователя для SSH-подключения. */
        var sshUsername: String = envOrProp("SSH_USERNAME")
        /** Пароль для SSH-подключения. */
        var sshPassword: String = envOrProp("SSH_PASSWORD")
        /** Локальный порт для проброса туннеля к базе данных через SSH. */
        val sshDBLocalPort: Int by lazy { envOrProp("SSH_DB_LOCAL_PORT").toInt() }
        /** Локальный порт для проброса туннеля к MinIO через SSH. */
        val sshMinioLocalPort: Int by lazy { envOrProp("SSH_MINIO_LOCAL_PORT").toInt() }

        /** Список идентификаторов проектов, которые следует игнорировать (в формате строки, обёрнутой в скобки). */
        val ignoringJagaProjectIds: String by lazy { "(${envOrProp("IGNORING_JAGA_PROJECT_IDS")})" }

        /** Включено ли логирование SQL-запросов. */
        val showSql: Boolean by lazy { envOrProp("SHOW_SQL").toBoolean() }

        /** Домен для доступа к API Статей. */
        val domain: String by lazy { envOrProp("ARTICLE_DOMAIN") }
        /** Логин для доступа к API Статей. */
        val username: String by lazy { envOrProp("ARTICLE_USERNAME") }
        /** Пароль для доступа к API Статей. */
        val password: String by lazy { envOrProp("ARTICLE_PASSWORD") }

        /** URL для получения токена доступа к Keycloak. */
        val keycloakUrl: String by lazy { envOrProp("KEYCLOAK_URL") }
        /** Логин для доступа к Keycloak. Используется для получения токена доступа к Keycloak. */
        val keycloakUsername: String by lazy { envOrProp("KEYCLOAK_USERNAME") }
        /** Пароль для доступа к Keycloak. Используется для получения токена доступа к Keycloak. */
        val keycloakPassword: String by lazy { envOrProp("KEYCLOAK_PASSWORD") }

        /**
         * Возвращает значение конфигурационного параметра по его ключу.
         * Сначала ищет значение в переменных окружения, затем — в файле `application.properties`.
         * Если значение не найдено, выбрасывает исключение [IllegalStateException].
         *
         * @param key название параметра (например, "DB_HOST", "MINIO_ENDPOINT")
         * @return значение параметра в виде строки
         * @throws IllegalStateException если параметр не найден ни в переменных окружения, ни в файле конфигурации
         */
        private fun envOrProp(key: String): String =
            System.getenv(key) ?: loadProperty(key) ?: error("$key is not set")

        /**
         * Загружает значение свойства из файла `application.properties`.
         *
         * Метод считывает файл конфигурации `application.properties` из classpath,
         * парсит его с помощью [Properties] и возвращает значение по указанному ключу.
         *
         * @param key ключ свойства, которое необходимо загрузить (например, "DB_HOST")
         * @return значение свойства или `null`, если свойство не найдено или файл отсутствует
         */
        private fun loadProperty(key: String): String? {
            val props = Properties()
            ClassLoader.getSystemResourceAsStream("application.properties")?.use { props.load(it) }
            return props.getProperty(key)
        }

    }
}
