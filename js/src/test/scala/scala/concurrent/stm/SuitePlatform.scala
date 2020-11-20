package scala.concurrent.stm

trait SuitePlatform {
  def isDotty   = false
  def isJVM     = false
  def isJS      = true

  type IndexOutOfBoundsException      = java.lang.VirtualMachineError
  type ArrayIndexOutOfBoundsException = java.lang.VirtualMachineError
}
