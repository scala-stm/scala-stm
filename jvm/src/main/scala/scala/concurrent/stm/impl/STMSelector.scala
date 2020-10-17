package scala.concurrent.stm.impl

import scala.concurrent.stm.impl.STMImpl.instance

// the JVM implementation of STMSelector
// allows a system property and class name based selection
trait STMSelector {
  // We duplicate the implementation of select() to avoid the need to
  // instantiate an STM that we won't end up using

  /** If no `STMImpl` instance has yet been selected, installs an instance of
   *  `Class.forName(implClassName)` as the system-wide STM implementation.
   *  Returns true if `implClassName` was newly or previously selected, or
   *  returns false if another STM implementation was chosen.
   */
  def trySelect(implClassName: String): Boolean = {
    explicitChoice = implClassName
    instance.getClass.getName == implClassName
  }

  /** Installs `Class.forName(implClassName)` as the system-wide STM
   *  implementation if no `STMImpl` has yet been chosen, or verifies that
   *  `implClassName` was previously selected, throwing
   *  `IllegalStateException` if a different STM implementation has already
   *  been selected
   */
  def select(implClassName: String): Unit = {
    if (!trySelect(implClassName)) {
      throw new IllegalStateException(
        "unable to select STMImpl class " + implClassName + ", " + instance + " already installed")
    }
  }

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


  /** May be a String class name, an STMImpl, or null */
  @volatile private var explicitChoice: AnyRef = null

  private[impl] def createInstance(): STMImpl = {
    var choice: AnyRef = System.getProperty("scala.stm.impl")

    if (choice == null)
      choice = explicitChoice

    if (choice == null) {
      choice = try {
        val fc = Class.forName("scala.concurrent.stm.impl.DefaultFactory")
        fc.newInstance().asInstanceOf[STMImpl.Factory].createInstance()
      } catch {
        case _: ClassNotFoundException => "scala.concurrent.stm.ccstm.CCSTM"
      }
    }

    choice match {
      case s: String => Class.forName(s).newInstance().asInstanceOf[STMImpl]
      case i: STMImpl => i
    }
  }
}
