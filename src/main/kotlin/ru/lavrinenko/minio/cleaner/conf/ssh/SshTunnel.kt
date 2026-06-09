package ru.lavrinenko.minio.cleaner.conf.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import ru.lavrinenko.minio.cleaner.conf.AppConfig
import org.slf4j.LoggerFactory
import java.io.Closeable

object SshTunnel : Closeable {
    private var sshHost: String = ""
    private var sshPort: Int = 0
    private var sshUsername: String = ""
    private var sshPassword: String = ""
    private lateinit var session: Session
    private val log = LoggerFactory.getLogger(SshTunnel::class.java)

    init {
        sshHost = AppConfig.sshHost
        sshPort = AppConfig.sshPort
        sshUsername = AppConfig.sshUsername
        sshPassword = AppConfig.sshPassword
    }

    /**
     * Устанавливает SSH-туннель, если он еще не установлен.
     * Использует параметры подключения из конфигурации: хост, порт, имя пользователя и пароль.
     * При успешном подключении записывает информацию в лог.
     * В случае ошибки записывает сообщение об ошибке в лог и выбрасывает исключение.
     */
    fun establishTunnel() {
        if (!AppConfig.sshEnabled || isTunnelEstablished()) return
        try {
            val jsch = JSch()
            JSch.setConfig("StrictHostKeyChecking", "no")
            session = jsch.getSession(sshUsername, sshHost, sshPort)
            session.setPassword(sshPassword)
            session.connect()
            log.info("Established SSH tunnel to $sshHost:$sshPort")
        } catch (e: Exception) {
            log.error("Error establishing SSH tunnel: " + e.message)
            throw e
        }
    }

    /**
     * Проверяет, установлен ли SSH-туннель и активно ли соединение.
     *
     * @return true, если сессия инициализирована и соединение установлено, иначе false.
     */
    private fun isTunnelEstablished(): Boolean = ::session.isInitialized && session.isConnected

    /**
     * Проверяет, установлено ли пробрасывание указанного локального порта.
     *
     * @param session Активная SSH-сессия, в которой проверяется проброс портов.
     * @param localPort Локальный порт, который необходимо проверить на наличие проброса.
     * @return true, если для указанного порта настроено локальное пробрасывание (Local Port Forwarding), иначе false.
     */
    @Suppress("EqualsBetweenInconvertibleTypes")
    private fun isForwardingPort(session: Session, localPort: Int): Boolean =
        if (session.portForwardingL == null) false
        else session.portForwardingL.any { localPort.toString().equals(it[0]) }

    /**
     * Добавляет локальное пробрасывание портов (Local Port Forwarding) через установленный SSH-туннель.
     *
     * @param localPort Локальный порт на машине клиента, который будет прослушиваться.
     * @param remoteHost Адрес удалённого хоста, к которому необходимо подключиться с помощью сервера SSH.
     * @param remotePort Порт удалённого хоста, к которому необходимо подключиться.
     *
     * Если туннель не установлен, выводится предупреждение и операция прерывается.
     * При попытке пробросить уже используемый порт — регистрируется предупреждение.
     * В случае других ошибок JSch — записывается ошибка в лог.
     */
    fun addPortForwardingL(localPort: Int, remoteHost: String, remotePort: Int) {
        if (!isTunnelEstablished()) {
            log.warn("SSH tunnel is not established. Cannot add port forwarding")
            return
        }
        try {
            session.setPortForwardingL(localPort, remoteHost, remotePort)
            log.info("Added port forwarding for local port $localPort to $remoteHost:$remotePort")
        } catch (e: JSchException) {
            when {
                e.message?.contains("is already registered", ignoreCase = true) == true -> {
                    log.warn("Port $localPort is already in use (forwarding or another process)")
                }
                else -> {
                    log.error("JSch error: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Удаляет локальное пробрасывание порта (Local Port Forwarding) через установленный SSH-туннель.
     *
     * @param port Локальный порт, для которого необходимо удалить проброс.
     * Если туннель не установлен, выводится предупреждение и операция прерывается.
     * Если для указанного порта нет активного проброса, регистрируется предупреждение.
     * При успешном удалении записывает информацию в лог.
     */
    fun delPortForwardingL(port: Int) {
        if (!isTunnelEstablished()) {
            log.warn("SSH tunnel is not established. Cannot delete port forwarding")
            return
        }
        if (isForwardingPort(session, port)) {
            session.delPortForwardingL(port)
            log.info("Deleted port forwarding for local port $port")
        } else {
            log.warn("No active forwarding found for local port $port")
        }
    }

    /**
     * Закрывает SSH-туннель и освобождает все связанные ресурсы.
     * При закрытии автоматически удаляются все настроенные пробросы локальных портов.
     * Метод безопасен для многократного вызова — при отсутствии активной сессии не выполняет никаких действий.
     * Вызывается автоматически в блоке `use` (try-with-resources в Kotlin).
     *
     * Логирует успешное закрытие туннеля или ошибку при отключении.
     * В случае исключения при отключении записывает сообщение в лог и пробрасывает исключение дальше.
     */
    override fun close() {
        if (isTunnelEstablished()) {
            log.info("Closing SSH tunnel to $sshHost:$sshPort")
            try {
                session.portForwardingL.forEach {
                    session.delPortForwardingL(it.split(":")[0].toInt())
                    log.info("Deleted port forwarding -> $it")
                }
                session.disconnect()
                log.info("Closed SSH tunnel connection to $sshHost:$sshPort")
            } catch (e: Exception) {
                log.error("Error closing SSH tunnel: " + e.message)
                throw e
            }
        }
    }
}