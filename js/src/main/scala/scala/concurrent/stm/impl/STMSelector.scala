package scala.concurrent.stm.impl

import scala.concurrent.stm.impl.STMImpl.instance

// the JS version support default and instance based selection
trait STMSelector {
  /** Installs `impl` as the system-wide STM implementation if no `STMImpl`
   *  has yet been chosen, or verifies that `impl` is equal to the previously
   *  selected instance, throwing `IllegalStateException` if an STM
   *  implementation has already been selected and `impl != instance`
   */
  def select(impl: STMImpl): Unit = {
    explicitChoice = impl
    if (impl != instance) {
      throw new IllegalStateException(
        "unable to select STMImpl " + impl + ", " + instance + " already installed")
    }
  }

  /** May be an STMImpl, or null */
  @volatile private var explicitChoice: STMImpl = null

  private[impl] def createInstance(): STMImpl = {
    var choice = explicitChoice

    if (choice == null) {
      choice = new scala.concurrent.stm.ccstm.CCSTM
    }

    choice
  }
}
