package scala.concurrent.stm

trait RefFactorySuitePlatform {
  def isDotty   = true
  def isJVM     = true
}
