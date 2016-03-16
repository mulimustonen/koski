package fi.oph.tor.log

import fi.oph.tor.toruser.{MockUsers, TorUser}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.{Assertions, FreeSpec, Matchers}
import org.slf4j.Logger
import scala.util.matching.Regex

class AuditLogSpec extends FreeSpec with Assertions with Matchers {
  val loggerMock = mock(classOf[Logger])
  val audit = new AuditLog(loggerMock)

  "AuditLog" - {
    "Logs in JSON format" in {
      verifyLogMessage(AuditLogMessage(TorOperation.OPISKELUOIKEUS_LISAYS, MockUsers.hiiri.asTorUser), """\{"timestamp":".*","serviceName":"koski","applicationType":"backend","clientIp":"192.168.0.10","operaatio":"OPISKELUOIKEUS_LISAYS"}""".r)
    }
  }

  private def verifyLogMessage(msg: AuditLogMessage, expectedMessage: Regex) {
    audit.log(msg)
    val infoCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    verify(loggerMock, times(1)).info(infoCapture.capture)
    val logMessage: String = infoCapture.getValue
    logMessage should fullyMatch regex(expectedMessage)
  }
}
