package controllers

import java.io.{ByteArrayOutputStream, OutputStream}
import java.security.{DigestOutputStream, MessageDigest}
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

import controllers.StreamingBodyParser._
import play.api.mvc._

import scala.util.{Failure, Success}

class Application extends Controller {
  val welcomeMsg = "Demonstration of Streaming File Upload for Play 2.4"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /**
   * Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
   * for the new file. This example streams to a file, but streaming to any OutputStream will work
   * @param filename The filename is provided by the Play! Multipart form handler
   * @return A type that implements an Output trait.
   **/
  def streamConstructorHashBytes(filename: String) = {
    val md5Digest = MessageDigest.getInstance("MD5")
    Md5StreamOutput(new DigestOutputStream(new ByteArrayOutputStream(), md5Digest), md5Digest)
  }

  /**
   * Return the MD5 hash of the uploaded file.
   *
   * The action takes our custom body parser (StreamingBodyParser) with the HOF that returns the OutputStream.  We
   * created an Md5StreamOutput that uses a DigestOutputStream calculate an MD5 digest.
   * @return An MD5 hash.
   */
  def uploadToHash = Action(streamingBodyParser(streamConstructorHashBytes)){ request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files.head.ref
    result match {
      case Success(StreamingSuccess(filename, out: Md5StreamOutput)) => Ok(out.getHash)
      case Failure(ex) => Ok(s"Streaming error occurred: ${ex.getMessage}")
    }
  }
}
