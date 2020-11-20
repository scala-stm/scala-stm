package scala.concurrent.stm

trait SuitePlatform {
  def isDotty   = false
  def isJVM     = true
  def isJS      = false

  type IndexOutOfBoundsException      = java.lang.IndexOutOfBoundsException
  type ArrayIndexOutOfBoundsException = java.lang.ArrayIndexOutOfBoundsException
}
