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
import java.util.concurrent.ConcurrentHashMap
import sbt.{ExecuteProgress, Result, Task, ScopedKey, Logger, Def}

class SbtTaskTimer(timers: ConcurrentHashMap[ScopedKey[_], BoxedLong], logger: Logger)
    extends ExecuteProgress[Task] {

  override type S = Unit
  private val pending = new ConcurrentHashMap[ScopedKey[_], BoxedLong]()

  override def initial: Unit = {}

  private def getKey(task: Task[_]): Option[ScopedKey[_]] = task.info.get(Def.taskDefinitionKey)

  override def registered(
      state: Unit,
      task: sbt.Task[_],
      allDeps: Iterable[sbt.Task[_]],
      pendingDeps: Iterable[sbt.Task[_]]
  ): Unit = {
    getKey(task) match {
      case Some(key) => pending.put(key, System.currentTimeMillis())
      case None => ()
    }
  }

  override def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]): Unit = {
    def finishTiming(key: ScopedKey[_]): Unit = {
      pending.get(key) match {
        case startTime: BoxedLong =>
          pending.remove(key)
          val duration = System.currentTimeMillis() - startTime
          println(s"Adding $duration duration for $key")
          timers.get(key) match {
            // We aggregate running time for those tasks that we target
            case currentDuration: BoxedLong => timers.put(key, currentDuration + duration)
            case null => timers.put(key, duration)
          }
        case null => logger.warn(s"${task.info} finished, but its start wasn't recorded")
      }
    }

    getKey(task) match {
      case Some(key) => finishTiming(key)
      case None => () // Ignore tasks that do not have key information
    }
  }
  def workStarting(task: Task[_]): Unit = ()
  def allCompleted(state: Unit, results: sbt.RMap[sbt.Task, sbt.Result]): Unit = ()
  def completed[T](state: Unit, task: sbt.Task[T], result: sbt.Result[T]): Unit = ()
  def ready(state: Unit, task: sbt.Task[_]): Unit = ()
}
