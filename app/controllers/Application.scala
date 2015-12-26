package controllers

import java.io.{OutputStream, ByteArrayOutputStream, FileOutputStream, File}
import java.math.BigInteger
import java.nio.channels.Channels
import java.security.{DigestOutputStream, MessageDigest}

import org.scalactic.{Bad, Good}
import play.api._
import play.api.mvc._
import StreamingBodyParser._

trait Output {
  def write(bytes: Array[Byte]): Unit
  def close(): Unit
}

case class SimpleOutput(os: OutputStream) extends Output {
  def write(bytes: Array[Byte]): Unit = os.write(bytes)
  def close() = os.close()
}

case class Md5StreamOutput(os: OutputStream, md: MessageDigest) extends Output {
  def write(bytes: Array[Byte]): Unit = os.write(bytes)
  def close() = os.close()
  def getHash = new BigInteger(1, md.digest())
}

class Application extends Controller {
  val welcomeMsg = "Demonstration of Streaming File Upload for Play 2.4"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /** Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
    * for the new file. This example streams to a file, but streaming to any OutputStream will work */
  def streamConstructorFile(filename: String): Output = {
    val dir = new File(sys.env("HOME"), "uploadedFiles")
    dir.mkdirs()
    SimpleOutput(new FileOutputStream(new File(dir, filename)))
  }

  def uploadToFile = Action(streamingBodyParser(streamConstructorFile)){ request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files.head.ref
    result match {
      case Good(res) => Ok(s"File ${res.filename} successfully streamed.")
      case Bad(msg) => Ok(s"Streaming error occurred: $msg")
    }
  }

  def streamConstructorBytes(filename: String) = SimpleOutput(new ByteArrayOutputStream())

  def uploadToBytes = Action(streamingBodyParser(streamConstructorBytes)){ request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files.head.ref
    result match {
      case Good(res) => Ok(s"File ${res.filename} successfully streamed.")
      case Bad(msg) => Ok(s"Streaming error occurred: $msg")
    }
  }

  def streamConstructorHashBytes(filename: String) = {
    val md5Digest = MessageDigest.getInstance("MD5")
    Md5StreamOutput(new DigestOutputStream(new ByteArrayOutputStream(), md5Digest), md5Digest)
  }

  def uploadToHash = Action(streamingBodyParser(streamConstructorHashBytes)){ request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files.head.ref
    result match {
      case Good(StreamingSuccess(filename, out: Md5StreamOutput)) =>
        Ok(s"File $filename successfully streamed.  Hash: ${out.getHash}")
      case Bad(msg) => Ok(s"Streaming error occurred: $msg")
    }
  }
}
