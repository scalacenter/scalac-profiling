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
import sbt.{ExecuteProgress, Result, Task, ScopedKey, Def}

class SbtTaskTimer(timers: ConcurrentHashMap[ScopedKey[_], BoxedLong])
    extends ExecuteProgress[Task] {

  override type S = Unit
  override def initial: Unit = {}

  private def getKey(task: Task[_]): Option[ScopedKey[_]] =
    task.info.get(Def.taskDefinitionKey)

  private val pending = new ConcurrentHashMap[ScopedKey[_], BoxedLong]()
  def mkUniformRepr(scopedKey: ScopedKey[_]): ScopedKey[_] = scopedKey

  import sbt.Task
  type Tasks = Iterable[sbt.Task[_]]
  override def registered(state: Unit, task: Task[_], allDeps: Tasks, pendingDeps: Tasks): Unit = {
    getKey(task) match {
      case Some(key) => pending.put(key, System.currentTimeMillis())
      case None => ()
    }
  }

  override def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]): Unit = {
    def finishTiming(scopedKey: ScopedKey[_]): Unit = {
      pending.get(scopedKey) match {
        case startTime: BoxedLong =>
          pending.remove(scopedKey)
          val duration = System.currentTimeMillis() - startTime
          timers.get(scopedKey) match {
            // We aggregate running time for those tasks that we target
            case currentDuration: BoxedLong => timers.put(scopedKey, currentDuration + duration)
            case null => timers.put(scopedKey, duration)
          }
        case null =>
          println(
            s"[sbt-scalac-profiling] ${task.info} finished, but its start wasn't recorded"
          )
      }
    }

    getKey(task) match {
      case Some(key) => finishTiming(key)
      case None => () // Ignore tasks that do not have key information
    }
  }

  def workStarting(task: Task[_]): Unit = ()
  def allCompleted(state: Unit, results: sbt.RMap[Task, sbt.Result]): Unit = ()
  def completed[T](state: Unit, task: Task[T], result: sbt.Result[T]): Unit = ()
  def ready(state: Unit, task: Task[_]): Unit = ()
}
