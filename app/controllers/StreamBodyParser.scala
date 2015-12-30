package controllers

import java.io.OutputStream
import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

import play.api.Logger
import play.api.libs.iteratee.{Cont, Done, Input, Iteratee}
import play.api.mvc.BodyParsers.parse
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{BodyParser, RequestHeader}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.PartHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure, Try}

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

  def getHash = (new HexBinaryAdapter).marshal(md.digest())
}

case class StreamingSuccess(filename: String, output: Output)

/**
  * The StreamingBodyParser writes a Play Iteratee to an Output stream.  This is used so that an upload can be streamed
  * directly to its destination (i.e. HDFS).  This implementation was updated for Play! 2.4 and modified to help my own
  * learnings.
  * Source repository: https://github.com/mslinn/play21-file-upload-streaming
  */
object StreamingBodyParser {

  def streamingBodyParser(streamConstructor: String => Output) = BodyParser { request =>
    // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
    // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
    parse.multipartFormData(new StreamingBodyParser(streamConstructor).streamingFilePartHandler2(request),
      maxLength = 1024 * 1000000 /* 1GB */).apply(request)
  }
}

class StreamingBodyParser(streamConstructor: String => Output) {

  /** Custom implementation of a PartHandler, inspired by these Play mailing list threads:
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/WY548Je8VB0/dJkj3arlBigJ
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/n7yF6wNBL_s/wBPFHBBiKUwJ */
  def streamingFilePartHandler2(request: RequestHeader): PartHandler[FilePart[Try[StreamingSuccess]]] =
    Multipart.handleFilePart {
      case Multipart.FileInfo(partName, filename, contentType) =>

        val output: Try[Output] =
          Try(streamConstructor(filename))

        val iter: Iteratee[Array[Byte], Try[Output]] = Iteratee.fold(output) { (os: Try[Output], elDataChunk: Array[Byte]) =>
          os foreach { _.write(elDataChunk)}
          os
        }

        iter.map { os =>
          os foreach { _.close()}
          os match {
            case Failure(ex) =>
              Logger.error(s"Streaming the file $filename failed: ${ex.getMessage}")
              Failure(ex)
            case Success(out) =>
              Logger.info(s"$filename finished streaming.")
              Success(StreamingSuccess(filename, out))
          }
        }
    }

  /** Custom implementation of a PartHandler, inspired by these Play mailing list threads:
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/WY548Je8VB0/dJkj3arlBigJ
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/n7yF6wNBL_s/wBPFHBBiKUwJ */
  def streamingFilePartHandler(request: RequestHeader): PartHandler[FilePart[Try[StreamingSuccess]]] =
    Multipart.handleFilePart {
      case Multipart.FileInfo(partName, filename, contentType) =>

        val output: Try[Output] =
          Try(streamConstructor(filename))

        // The fold method that actually does the parsing of the multipart file part.
        // Type A is expected to be Try[Output]
        def fold[E, A](state: A)(elStep: (A, E) => A): Iteratee[E, A] = {
          def step(s: A)(i: Input[E]): Iteratee[E, A] = i match {
            case Input.EOF => Done(s, Input.EOF)
            case Input.Empty => Cont[E, A](i => step(s)(i))
            case Input.El(data) =>
              // write the chunk (data) to the OutputStream
              val s1 = elStep(s, data)
              // if an error occurred during output stream initialisation, set Iteratee to Done
              output match {
                case Failure(_) => Done(s, Input.EOF)
                case Success(_) => Cont[E, A](i => step(s1)(i))
              }
          }
          Cont[E, A](i => step(state)(i))
        }

        def elStepFun(os: Try[Output], elDataChunk: Array[Byte]): Try[Output] = {
          os foreach { _.write(elDataChunk) }
          os
        }

        val outputStreams = fold[Array[Byte], Try[Output]](state = output)(elStepFun)

        outputStreams.map { os =>
          os foreach { _.close()}
          os match {
            case Failure(ex) =>
              Logger.error(s"Streaming the file $filename failed: ${ex.getMessage}")
              Failure(ex)
            case Success(out) =>
              Logger.info(s"$filename finished streaming.")
              Success(StreamingSuccess(filename, out))
          }
        }
    }
}
