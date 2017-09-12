/*                                                                                                *\
**      _____            __         ______           __                                           **
**     / ___/_________ _/ /___ _   / ____/__  ____  / /____  _____                                **
**     \__ \/ ___/ __ `/ / __ `/  / /   / _ \/ __ \/ __/ _ \/ ___/    Scala Center                **
**    ___/ / /__/ /_/ / / /_/ /  / /___/ /__/ / / / /_/ /__/ /        https://scala.epfl.ch       **
**   /____/\___/\__,_/_/\__,_/   \____/\___/_/ /_/\__/\___/_/         (c) 2017-2018, LAMP/EPFL    **
**                                                                                                **
\*                                                                                                */

package sbt.ch.epfl.scala

import java.lang.{Long => BoxedLong}
import sbt.{ExecuteProgress, Result, Task}

private[sbt] class SbtTaskTimer(taskToTime: Task[_]) extends ExecuteProgress[Task] {
  override type S = Unit
  import java.util.concurrent.ConcurrentHashMap
  val pending = new ConcurrentHashMap[Task[_], BoxedLong]()
  val timers = new ConcurrentHashMap[Task[_], BoxedLong]()

  override def initial: Unit = {
    timers.clear()
    pending.clear()
  }

  override def workStarting(task: Task[_]): Unit = {
    if (task != taskToTime) ()
    else pending.put(task, System.nanoTime())
  }

  override def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]): Unit = {
    if (task != taskToTime) ()
    else {
      pending.get(task) match {
        case startTime: BoxedLong =>
          pending.remove(task)
          val duration = System.nanoTime() - startTime
          timers.get(task) match {
            // We aggregate running time for those tasks that we target
            case currentDuration: BoxedLong => timers.put(task, currentDuration + duration)
            case null => timers.put(task, duration)
          }
        case null =>
          System.err.println(s"Error: ${task.info} finished, but its start wasn't recorded")
      }
    }
  }

  def allCompleted(state: Unit, results: sbt.RMap[sbt.Task, sbt.Result]): Unit = ()
  def completed[T](state: Unit, task: sbt.Task[T], result: sbt.Result[T]): Unit = ()
  def ready(state: Unit, task: sbt.Task[_]): Unit = ()
  def registered(
      state: Unit,
      task: sbt.Task[_],
      allDeps: Iterable[sbt.Task[_]],
      pendingDeps: Iterable[sbt.Task[_]]
  ): Unit = ()

}

object SbtTaskTimer {
  import sbt.{Setting, Compile}
  def createSetting: Setting[_] = {
    import sbt.Keys
    Keys.executeProgress :=
      (_ => new Keys.TaskProgress(new SbtTaskTimer((Keys.compile in Compile).taskValue)))
  }
}
