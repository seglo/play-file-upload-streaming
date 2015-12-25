package controllers

import java.io.{FileOutputStream, File}

import org.scalactic.{Bad, Good}
import play.api._
import play.api.mvc._
import StreamingBodyParser._

class Application extends Controller {
  val welcomeMsg = "Demonstration of Streaming File Upload for Play 2.5"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /** Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
    * for the new file. This example streams to a file, but streaming to AWS S3 is also possible */
  def streamConstructor(filename: String) = {
    val dir = new File(sys.env("HOME"), "uploadedFiles")
    dir.mkdirs()
    new FileOutputStream(new File(dir, filename))
  }

  def upload = Action(streamingBodyParser(streamConstructor)) { request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files.head.ref
    result match {
      case Good(res) => Ok(s"File ${res.filename} successfully streamed.")
      case Bad(msg) => Ok(s"Streaming error occurred: $msg")
    }
  }
}
