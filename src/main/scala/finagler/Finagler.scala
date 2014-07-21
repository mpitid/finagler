
package finagler

import scala.util.control.NonFatal
import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future}
import java.nio.file.Path
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}


object service {
  def main(args: Array[String]) {
    args.toList match {
      case path :: host :: _ =>
        Await.ready(Http.serve(host, new FileService(simpleResolver(path))))
      case host :: Nil =>
        Await.ready(Http.serve(host, new EchoService))
      case Nil =>
        System.err.println("usage: service <root> [host=localhost:8080]")
        System.exit(1)
    }
  }

  def simpleResolver(root: String): (String) => Option[Path] =
    (uri: String) => {
      val file = new java.io.File(root + uri)
      if (file.isFile()) Some(file.toPath) else None
    }
}


class EchoService extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest): Future[HttpResponse] =
    Future {
      val response = new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.OK)
      response.setContent(req.getContent)
      Option(req.headers().get("content-type")).foreach { c =>
        response.headers.add("content-type", c)
      }
      response
    }
}

class FileService(resolver: (String => Option[Path])) extends Service[HttpRequest, HttpResponse] {

  def apply(req: HttpRequest): Future[HttpResponse] = {
    Future {
      try {
        resolver(req.getUri) match {
          case None =>
            new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.NOT_FOUND)
          case Some(path) =>
            val response = new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.OK)
            response.headers.add(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream")
            response.setContent(load(path))
            response
        }
      } catch {
        case NonFatal(e) =>
          new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.BAD_REQUEST)
      }
    }
  }

  def load(path: Path): ChannelBuffer = {
    val channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ)
    try {
      val size = channel.size()
      if (size > Int.MaxValue)
        throw new IllegalArgumentException(s"cannot serve files larger than ${Int.MaxValue} bytes")
      val buffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size.toInt)
      channel.close()
      ChannelBuffers.wrappedBuffer(buffer)
    } finally
      channel.close()
  }
}

// vim: set ts=2 sw=2 et:
