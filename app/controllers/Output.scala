package controllers

import java.io.OutputStream
import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

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