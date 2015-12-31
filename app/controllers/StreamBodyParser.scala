package controllers

import play.api.Logger
import play.api.libs.iteratee.Iteratee
import play.api.mvc.BodyParsers.parse
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, BodyParser, RequestHeader}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.PartHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

case class StreamingSuccess(filename: String, output: Output)

/**
  * The StreamingBodyParser writes a Play Iteratee to an OutputStream.  This is used so that an upload can be streamed
  * directly to its destination using a Java library that already supports writing to an OutputStream (i.e. AWS S3,
  * HDFS, DigestOutputStream, etc).
  *
  * This implementation was re-factored from Mike Slinn's play21-file-upload-streaming project.
  * Source repository: https://github.com/mslinn/play21-file-upload-streaming
  */
object StreamingBodyParser {

  def streamingBodyParser(streamProvider: String => Output): BodyParser[MultipartFormData[Try[StreamingSuccess]]] =
    BodyParser { request =>
      // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
      // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
      parse
        .multipartFormData(streamingFilePartHandler(request, streamProvider), maxLength = 1024 * 1000000 /* 1GB */)
        .apply(request)
    }

  def streamingFilePartHandler(request: RequestHeader,
                               streamProvider: String => Output): PartHandler[FilePart[Try[StreamingSuccess]]] =
    Multipart.handleFilePart {
      case Multipart.FileInfo(partName, filename, contentType) =>
        Try(streamProvider(filename)) match {
          case Success(output) =>
            val done: Iteratee[Array[Byte], Output] = Iteratee.fold(output) { (os: Output, elDataChunk: Array[Byte]) =>
              os.write(elDataChunk)
              os
            }
            done map { output =>
              output.close()
              Logger.info(s"$filename finished streaming.")
              Success(StreamingSuccess(filename, output))
            }
          case Failure(ex) =>
            Logger.error(s"Streaming the file $filename failed: ${ex.getMessage}")
            throw ex
      }
    }
}
