/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package sbt.ch.epfl.scala

import sbt.{ExecuteProgress, Task}

private[sbt] object SbtTaskTimer extends ExecuteProgress[Task] {
  override type S = Unit
  import java.util.concurrent.ConcurrentHashMap
  val pending = new ConcurrentHashMap[Task[_], Long]()
  val timers = new ConcurrentHashMap[Task[_], Long]()

  override def initial: Unit = {
    timers.clear()
    pending.clear()
  }

  override def workStarting(task: Task[_]): Unit = pending.put(task, System.nanoTime())
  override def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]): Unit = {
    pending.get(task) match {
      case startTime: Long =>
        pending.remove(task)
        val duration = System.nanoTime() - startTime
        timers.get(task) match {
          case currentDuration: Long => timers.put(task, currentDuration + duration)
          case null => timers.put(task, duration)
        }
      case null =>
        // TODO(jvican): I wish I had access to a logger from here...
        System.err.println(s"Error: ${t.info} finished, but its start wasn't recorded")
    }
  }
}
