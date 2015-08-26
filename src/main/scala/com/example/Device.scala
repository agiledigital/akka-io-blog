package com.example

import akka.actor.{Actor, FSM}
import com.example.Device._

class Device extends Actor with FSM[State, Data] {

  import Device._
  import DeviceProtocol._

  startWith(Visible, RegisterData(Map()))

  when(Hidden) {
    case Event(ReadValue(_), _) =>
      // Device is hidden, do not respond.
      stay()
    case Event(ReadDeviceStatus, _) =>
      sender() ! DeviceStatus(false)
      stay()
    case Event(ShowDevice, _) =>
      sender() ! DeviceStatus(true)
      goto(Visible)
  }

  when(Visible) {
    case Event(ReadValue(register), RegisterData(registers)) =>
      sender() ! Value(register, registers.get(register))
      stay()
    case Event(ReadDeviceStatus, RegisterData(registers)) =>
      sender() ! DeviceStatus(true)
      stay()
    case Event(HideDevice, _) =>
      sender() ! DeviceStatus(false)
      goto(Hidden)
  }

  whenUnhandled {
    case Event(SetValue(register, value), RegisterData(registers)) =>
      val updatedRegisters = registers + (register -> value)
      sender() ! Value(register, updatedRegisters.get(register))
      goto(Visible) using RegisterData(updatedRegisters)
    case Event(UnsetValue(register), RegisterData(registers)) =>
      val updatedRegisters = registers - register
      sender() ! Value(register, updatedRegisters.get(register))
      goto(Visible) using RegisterData(updatedRegisters)
  }

  initialize()
}

object Device {

  sealed trait State

  case object Visible extends State

  case object Hidden extends State

  sealed trait Data

  final case class RegisterData(registers: Map[Int, Int]) extends Data

}

object DeviceProtocol {

  sealed trait IncomingDeviceMessages

  final case class ReadValue(register: Int) extends IncomingDeviceMessages

  final case class SetValue(register: Int, value: Int) extends IncomingDeviceMessages

  final case class UnsetValue(register: Int) extends IncomingDeviceMessages

  case object ReadDeviceStatus extends IncomingDeviceMessages

  case object HideDevice extends IncomingDeviceMessages

  case object ShowDevice extends IncomingDeviceMessages

  sealed trait OutgoingDeviceMessages

  final case class Value(register: Int, value: Option[Int]) extends OutgoingDeviceMessages

  final case class DeviceStatus(visible: Boolean) extends OutgoingDeviceMessages

}