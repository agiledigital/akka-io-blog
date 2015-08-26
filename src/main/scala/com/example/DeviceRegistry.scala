package com.example

import akka.actor.{Props, ActorRef, ActorLogging, Actor}

class DeviceRegistry extends Actor with ActorLogging {

  import DeviceRegistryProtocol._

  var devices = Map[Int, ActorRef]()

  override def receive: Receive = {
    case RegisterDevice =>
      unusedId() match {
        case None =>
          log.warning(s"Couldn't assign id for new device.")
          sender() ! FoundDevice(None)
        case Some(id) =>
          val device = context.system.actorOf(Props[Device])
          devices = devices + (id -> device)
          sender() ! FoundDevice(Some(device))
      }
    case UnregisterDevice(id) =>
      devices.get(id) match {
        case None => sender() ! FoundDevice(None)
        case Some(device) =>
          devices = devices - id
          context.stop(device)
          sender() ! FoundDevice(Some(device))
      }
    case FindDevice(id) =>
      devices.get(id) match {
        case None => sender() ! FoundDevice(None)
        case Some(device) => sender() ! FoundDevice(Some(device))
      }
  }

  private def unusedId(): Option[Int] = (1 to 255).find(i => devices.get(i).isEmpty)
}

object DeviceRegistryProtocol {
  case object RegisterDevice
  case class UnregisterDevice(deviceId: Int)
  case class FindDevice(deviceId: Int)

  case class FoundDevice(maybeDevice: Option[ActorRef])
}