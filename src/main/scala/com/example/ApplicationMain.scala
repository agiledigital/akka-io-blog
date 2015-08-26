package com.example

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Tcp}
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._
import scala.language.postfixOps

object ApplicationMain extends App {
  val system = ActorSystem("MyActorSystem")

  implicit val context = system.dispatcher


  implicit val timeout: Timeout = 2 seconds

  val registry = system.actorOf(Props[DeviceRegistry], "registryOne")

  (registry ? DeviceRegistryProtocol.RegisterDevice).mapTo[DeviceRegistryProtocol.FoundDevice].map {
    case DeviceRegistryProtocol.FoundDevice(Some(device)) =>
      device ! DeviceProtocol.SetValue(1, 100)
      (device ? DeviceProtocol.ReadValue(1)).mapTo[DeviceProtocol.Value].foreach(println)
    case DeviceRegistryProtocol.FoundDevice(None) =>
      throw new IllegalStateException("Didn't register new device.")
  }

  val server = system.actorOf(Props(new Server(registry)), "serverActor")

  system.awaitTermination()
}

class Server(registry: ActorRef) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 12000))

  override def receive = {
    case b@Bound(localAddress) =>
      log.info(s"Bound to [$localAddress].")

    case CommandFailed(_: Bind) => context stop self

    case c@Connected(remote, local) =>
      val connection = sender()
      val handler = context.actorOf(Props(new RequestHandler(registry, connection)))
      connection ! Register(handler)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit =
    context.system.shutdown()
}