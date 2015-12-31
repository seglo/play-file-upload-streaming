import java.nio.file.{Files, StandardOpenOption}

import org.junit.runner._
import org.specs2.mutable.Specification
import org.specs2.runner._
import play.api.libs.Files.TemporaryFile
import play.api.test.Helpers._
import play.api.test.WithApplication

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification with UploadHelper {
  "Application" should {
    "upload a test file successfully" in new WithApplication {
      val tempFile = TemporaryFile("TEST_")
      val fileRef = tempFile.file

      Files.write(fileRef.toPath, """{"hello":"world"}""".getBytes, StandardOpenOption.WRITE)

      val future = sendUploadRequest(controllers.routes.Application.uploadToHash().url, fileRef, "application/json")

      status(future) ==== OK
      contentAsString(future) ==== "FBC24BCC7A1794758FC1327FCFEBDAF6"
    }
  }
}
