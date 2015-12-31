import java.io.{File, ByteArrayOutputStream}
import java.nio.charset.Charset
import java.nio.file.{StandardCopyOption, Files}

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import play.api.http.{Writeable, ContentTypeOf}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Result, MultipartFormData}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * The UploadHelper provides helper methods to test Play actions that use Play's multipart body parser.
 *
 * This code is based on Emre's gist "Sample upload testing in Play Framework 2.3.1"
 * Source: https://gist.github.com/emrecelikten/58ae4a4cb572cfaeb525
 */
trait UploadHelper {
  /**
   * An ugly way to create multipartFormData using Apache HTTP MultipartEntityBuilder.
   *
   * The reason for def instead of a implicit Writeable instance is because we need multipart boundary to pass into
   * ContentTypeOf constructor and creating a Writeable requires a ContentTypeOf instance.
   *
   * @param request fake request on which the Writeable instance will be created
   */
  def writeableOf_multipartFormData(request: FakeRequest[MultipartFormData[TemporaryFile]]) = {
    val builder = MultipartEntityBuilder.create()
    request.body.dataParts.foreach { case (k, vs) => builder.addTextBody(k, vs.mkString)}

    // ContentType part is necessary here because it gets parsed as a DataPart otherwise.
    request.body.files.foreach { case f =>
      builder.addBinaryBody(f.filename, f.ref.file, ContentType.create(f.contentType.get, null: Charset), f.filename)
    }

    val entity = builder.build()

    implicit val contentTypeOf_MultipartFormData: ContentTypeOf[MultipartFormData[TemporaryFile]] =
      ContentTypeOf[MultipartFormData[TemporaryFile]](Some(entity.getContentType.getValue))

    Writeable[MultipartFormData[TemporaryFile]] { (mfd: MultipartFormData[TemporaryFile]) =>
      val outputStream = new ByteArrayOutputStream()
      entity.writeTo(outputStream)
      outputStream.toByteArray
    }
  }

  /**
   * Creates a fake request to given URL and sends it.
   *
   * @param url url of the controller to send the request
   * @param parameters some parameters that you would like to pass as a data part
   */
  def sendUploadRequest(url: String, file: File, mimeType: String,
                        parameters: Map[String, String] = Map[String, String]()): Future[Result] = {
    // Your original file will be deleted after the controller executes if you don't do the copy part below
    val tempFile = TemporaryFile("TEST_REMOVE_")
    Files.copy(file.toPath, tempFile.file.toPath, StandardCopyOption.REPLACE_EXISTING)

    val params = parameters.map { case (k, v) => k -> Seq(v)}
    val part = FilePart[TemporaryFile](key = tempFile.file.getName, filename = tempFile.file.getName,
      contentType = Some(mimeType), ref = tempFile)
    val formData = MultipartFormData(dataParts = params, files = Seq(part), badParts = Nil, missingFileParts = Nil)
    val request = FakeRequest("POST", url, FakeHeaders(), formData)

    implicit val writeable = writeableOf_multipartFormData(request)

    route(request)(writeable).get
  }

}
