package io.fx.scheduler

import java.util.concurrent.{Executors, TimeUnit}

import io.fx.Scheduler

import scala.concurrent.duration.FiniteDuration

/**
  * Created by aronen on 22/06/2017.
  */
class JavaScheduler extends Scheduler {
  private val _scheduler = Executors.newSingleThreadScheduledExecutor()

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, task: => Unit): Cancellable =
    _scheduler.scheduleAtFixedRate(() => task, initialDelay.toNanos, interval.toNanos, TimeUnit.NANOSECONDS)

  override def scheduleOnce(delay: FiniteDuration, task: => Unit): Cancellable =
    _scheduler.schedule(new Runnable { def run(): Unit = task }, delay.toNanos, TimeUnit.NANOSECONDS)

}

object JavaScheduler {
  implicit val scheduler = new JavaScheduler
}
