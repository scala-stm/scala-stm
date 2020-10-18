package scala.concurrent.stm

trait RefFactorySuitePlatform {
  def isDotty   = false
  def isJVM     = false
}
