package ru.lavrinenko.minio.cleaner

import ru.lavrinenko.minio.cleaner.conf.AppConfig
import ru.lavrinenko.minio.cleaner.conf.ssh.SshTunnel
import ru.lavrinenko.minio.cleaner.service.AttachmentDeleteService
import ru.lavrinenko.minio.cleaner.service.ProjectDeleteService
import ru.lavrinenko.minio.cleaner.service.UserDeleteService
import ru.lavrinenko.minio.cleaner.util.ResourceUtil
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val log = LoggerFactory.getLogger("Main")

    var direction = "asc" //if (args.isNotEmpty()) args[0].split("=")[1] else "asc"

    args.forEach { arg ->
        when {
            arg.startsWith("direction=") -> direction = arg.split("=")[1]
            arg.startsWith("ssh-user=") -> AppConfig.sshUsername = arg.split("=")[1]
            arg.startsWith("ssh-password=") -> AppConfig.sshPassword = arg.split("=")[1]
            arg.startsWith("ssh-enable=") -> AppConfig.sshEnabled = arg.split("=")[1].toBoolean()
        }
    }

    SshTunnel.establishTunnel()

//    val attachmentDeleteService = AttachmentDeleteService()
    val userDeleteService = UserDeleteService()
    val projectDeleteService = ProjectDeleteService()

    log.info("=======( APPLICATION STARTED )===========================================================================")
    try {
        log.info("Ignoring Jaga project ids: ${AppConfig.ignoringJagaProjectIds}")

//        log.info("=======( Start deleting files from MinIO... )======================================================")
//        attachmentDeleteService.deleteTasksFilesFromMinio(ResourceUtil.loadStringResource("projectsAttachmentsQuery.sql"))
//        attachmentDeleteService.deleteTasksFilesFromMinio(ResourceUtil.loadStringResource("usersAttachmentsQuery.sql"))
//        log.info("=======( End deleting files from MinIO... )==================================================")

        projectDeleteService.deleteProjectsFromDB(ResourceUtil.loadStringResource("projectsForDeleteQuery.sql"), direction)
        userDeleteService.deleteUsersFromDB(ResourceUtil.loadStringResource("usersForDeleteQuery.sql"))

    } catch (e: Exception) {
        log.error(e.message, e)
        log.error("=======( APPLICATION ENDED WITH ERRORS )=================================================================")
        exitProcess(1)
    } finally {
        SshTunnel.close()
    }

    log.info("=======( APPLICATION ENDED SUCCESSFULLY )================================================================")
    exitProcess(0)
}