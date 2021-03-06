package ch.epfl.scala.bsp.testkit.mock

import monix.eval.Task

import scala.meta.jsonrpc.Response

object mockServers {
  type ProtocolError = Response.Error
  type BspResponse[T] = Task[Either[ProtocolError, T]]
}
