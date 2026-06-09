package com.rit.crossdev.jaga.minio.cleaner.conf.datasources

import com.rit.crossdev.jaga.minio.cleaner.conf.AppConfig
import com.rit.crossdev.jaga.minio.cleaner.conf.ssh.SshTunnel

/**
 * Клиент для работы с MinIO, настроенный в зависимости от использования SSH-туннеля.
 *
 * Если SSH-туннель включён (`AppConfig.sshEnabled = true`), то создаётся локальный порт-форвардинг
 * через SSH к удалённому серверу MinIO. В этом случае клиент подключается к `localhost` на указанном
 * локальном порту (`AppConfig.sshMinioLocalPort`).
 *
 * Если SSH отключён, клиент подключается напрямую к эндпоинту MinIO, указанному в конфигурации.
 *
 * При закрытии клиента (вызов `close()`) удаляется правило порт-форвардинга.
 */
object MinioClient {

    /**
     * Экземпляр клиента MinIO, настроенный в зависимости от режима подключения.
     *
     * Если включён SSH-туннель (`AppConfig.sshEnabled = true`), клиент будет подключаться через локальный порт,
     * проброшенный посредством SSH-туннеля к удалённому серверу MinIO. В этом случае используется `localhost`
     * и локальный порт, указанный в `AppConfig.sshMinioLocalPort`.
     *
     * Если SSH-туннель отключён, клиент подключается напрямую к эндпоинту, заданному в `AppConfig.minioEndpoint`.
     *
     * Клиент автоматически закрывается при вызове метода `close()`, где также удаляется правило проброса порта (при использовании SSH).
     */
    val client: io.minio.MinioClient = if (System.getProperty("SSH_ENABLED").toBoolean()) {
        SshTunnel.establishTunnel()
        val (extractedHost, extractedPort) = extractHostAndPort(AppConfig.minioEndpoint)
        SshTunnel.addPortForwardingL(AppConfig.sshMinioLocalPort, extractedHost, extractedPort)
        io.minio.MinioClient.builder()
            .endpoint("http://localhost:${AppConfig.sshMinioLocalPort}")
            .credentials(AppConfig.minioAccessKey, AppConfig.minioSecretKey)
            .build()
    } else {
        io.minio.MinioClient.builder()
            .endpoint(AppConfig.minioEndpoint)
            .credentials(AppConfig.minioAccessKey, AppConfig.minioSecretKey)
            .build()
    }

    /**
     * Извлекает хост и порт из строки URL эндпоинта MinIO.
     *
     * Ожидается, что URL имеет формат: http://host:port или https://host:port.
     * Метод использует регулярное выражение для парсинга строки и возвращает пару значений — имя хоста (String) и номер порта (Int).
     *
     * @param url Строка URL эндпоинта MinIO (например, "http://minio.example.com:9000").
     * @return Пара значений: хост (String) и порт (Int).
     * @throws IllegalArgumentException Если URL не соответствует ожидаемому формату.
     */
    private fun extractHostAndPort(url: String): Pair<String, Int> {
        val regex = """^https?://([^:/]+):(\d+).*$""".toRegex()
        val matchResult = requireNotNull(regex.find(url)) {
            "Неверный формат URL MinIO-эндпоинта: $url. Ожидается формат http(s)://host:port"
        }
        return matchResult.destructured.let { (host, port) ->
            host to port.toInt()
        }
    }
}