package controllers

import java.io.OutputStream

import org.scalactic.{Bad, Good, Or, ErrorMessage}
import play.api.Logger
import play.api.libs.iteratee.{Cont, Done, Input, Iteratee}
import play.api.mvc.BodyParsers.parse
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{BodyParser, RequestHeader}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.PartHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure, Try}

case class StreamingSuccess(filename: String, output: Output)

/**
  * The StreamingBodyParser writes a Play Iteratee to an Output stream.  This is used so that an upload can be streamed
  * directly to its destination (i.e. HDFS).  This implementation was updated for Play! 2.4 and modified for Scalatic
  * support.  Source repository: https://github.com/mslinn/play21-file-upload-streaming
  */
object StreamingBodyParser {

  // TODO: Move maxLength to a constant (another one exists in FormatterServiceImpl)
  def streamingBodyParser(streamConstructor: String => Output) = BodyParser { request =>
    // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
    // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
    parse.multipartFormData(new StreamingBodyParser(streamConstructor).streamingFilePartHandler(request),
      maxLength = 1024 * 1000000 /* 1GB */).apply(request)
  }
}

class StreamingBodyParser(streamConstructor: String => Output) {

  /** Custom implementation of a PartHandler, inspired by these Play mailing list threads:
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/WY548Je8VB0/dJkj3arlBigJ
    * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/n7yF6wNBL_s/wBPFHBBiKUwJ */
  def streamingFilePartHandler(request: RequestHeader): PartHandler[FilePart[StreamingSuccess Or ErrorMessage]] =
    Multipart.handleFilePart {
      case Multipart.FileInfo(partName, filename, contentType) =>

        val output: Output Or ErrorMessage =
          Try(streamConstructor(filename)) match {
            case Success(os: Output) => Good(os)
            case Failure(ex) => Bad(ex.getMessage)
          }

        // The fold method that actually does the parsing of the multipart file part.
        // Type A is expected to be Option[OutputStream]
        def fold[E, A](state: A)(elStep: (A, E) => A): Iteratee[E, A] = {
          def step(s: A)(i: Input[E]): Iteratee[E, A] = i match {
            case Input.EOF => Done(s, Input.EOF)
            case Input.Empty => Cont[E, A](i => step(s)(i))
            case Input.El(data: E) =>
              // write the chunk (e) to the output Stream
              val s1 = elStep(s, data)
              // if an error occurred during output stream initialisation, set Iteratee to Done
              output match {
                case Bad(msg) => Done(s, Input.EOF)
                case _ => Cont[E, A](i => step(s1)(i))
              }
          }
          Cont[E, A](i => step(state)(i))
        }

        def elStepFun(os: Output Or ErrorMessage, elDataChunk: Array[Byte]): Output Or ErrorMessage = {
          os foreach { _.write(elDataChunk) }
          os
        }

        val outputStreams = fold[Array[Byte], Output Or ErrorMessage](output)(elStepFun)

        outputStreams.map { os =>
          os foreach { _.close()}
          output match {
            case Bad(errorMessage) =>
              Logger.error(s"Streaming the file $filename failed: $errorMessage")
              Bad(errorMessage)
            case Good(out) =>
              Logger.info(s"$filename finished streaming.")
              Good(StreamingSuccess(filename, out))
          }
        }
    }
}
