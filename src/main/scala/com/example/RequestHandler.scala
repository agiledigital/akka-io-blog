package com.example

import akka.actor.{Actor, ActorLogging, ActorRef, FSM}
import akka.io.Tcp
import akka.util.ByteString

import scala.concurrent.duration._
import scala.language.postfixOps

import com.example.RequestHandler._

/**
 * The per-request handler that will unmarshall the message.
 */
class RequestHandler(registry: ActorRef, connection: ActorRef) extends Actor with ActorLogging with FSM[State, Data] {

  import Tcp._

  startWith(Receiving, Empty)

  when(Receiving) {
    case Event(Received(data), _) =>
      data.utf8String.trim match {
      case "quit" =>
        context.system.shutdown()
        stay()
      case s =>
        ReadRequest.decode(s) match {
          case Some(r) =>
            goto(WaitingForDevice) using Working(r)
          case None =>
            log.warning(s"Could not decode [$s]")
            stay()
        }
    }
  }

  when(WaitingForDevice, stateTimeout = 5 seconds) {
    case Event(DeviceRegistryProtocol.FoundDevice(None), Working(request)) =>
      goto(Receiving) using Done(request, ReadResponse.fromRequest(request))

    case Event(DeviceRegistryProtocol.FoundDevice(Some(device)), Working(request)) =>
      goto(WaitingForValue) using WithDevice(request, device)
  }

  when(WaitingForValue, stateTimeout = 5 seconds) {
    case Event(DeviceProtocol.Value(register, value), WithDevice(request, _)) =>
      val response = ReadResponse.fromRequest(request).copy(value = value.getOrElse(-1))
      goto(Receiving) using Done(request, response)
  }

  onTransition {
    case Receiving -> WaitingForDevice =>
      nextStateData match {
        case Working(request) => registry ! DeviceRegistryProtocol.FindDevice(request.deviceId)
        case _ => log.info(s"Didn't have working data on transition to waiting")
      }
    case WaitingForDevice -> WaitingForValue =>
      nextStateData match {
        case WithDevice(request, device) => device ! DeviceProtocol.ReadValue(request.register)
        case _ => log.info(s"Didn't have with device data on transition to waiting")
      }
    case _ -> Receiving =>
      nextStateData match {
        case Done(request, response) => connection ! Write(response.encoded)
        case _ => log.info(s"Didn't have done data on transition to waiting")
      }
  }

  whenUnhandled {
    case Event(PeerClosed, _) =>
      stop(FSM.Normal)
    case Event(StateTimeout, _) =>
      goto(Receiving) using Empty
  }

  initialize()
}

object RequestHandler {

  sealed trait State

  case object Receiving extends State

  case object WaitingForDevice extends State

  case object WaitingForValue extends State

  sealed trait Data

  case object Empty extends Data

  final case class Working(request: ReadRequest) extends Data

  final case class WithDevice(request: ReadRequest, device: ActorRef) extends Data

  final case class Done(request: ReadRequest, response: ReadResponse) extends Data

}

case class ReadRequest(txId: Int, deviceId: Int, register: Int)

object ReadRequest {
  def decode(data: String): Option[ReadRequest] = {
    val ids = """(\d+),(\d+),(\d+)""".r
    data match {
      case ids(txId, deviceId, register) => Some(ReadRequest(txId.toInt, deviceId.toInt, register.toInt))
      case _ => None
    }
  }
}

case class ReadResponse(txId: Int, deviceId: Int, register: Int, value: Int) {
  val encoded = ByteString(s"$txId,$deviceId,$register,$value\n")
}

object ReadResponse {
  def fromRequest(readRequest: ReadRequest) = ReadResponse(readRequest.txId, readRequest.deviceId, readRequest.register, -1)
}
