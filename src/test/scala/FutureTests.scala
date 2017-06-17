import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import org.scalatest.FlatSpec
import futures.FuturePatterns._

import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by aronen on 19/05/2017.
  */
class FutureTests extends FlatSpec {

  "schedule" should "schedule execution in the future" in {

    val res = schedule(1 second) {
      System.currentTimeMillis()
    }

    val t1 = System.currentTimeMillis()
    val t2 = Await.result(res, 2 second)
    val time = t2 - t1
    println(s"time passed is $time")

    assert (time >= 1000)
    assert (time <= 1100)
  }

  "withTimeout" should "throw exception after timeout" in {

    val res = schedule(2 second)("hello") withTimeout (1 second)
    assertThrows[TimeoutException] {
      Await.result(res, 2 second)
    }
  }

  "withTimeout" should "do nothing if result arrives on time" in {

    val res = schedule(1 second)("hello") withTimeout (2 second)
    val finalRes = Await.result(res, 2 second)
    println(s"got $finalRes")
    assert (finalRes === "hello")
  }

  "delay" should "delay result" in {

    val res = Future(System.currentTimeMillis()) delay (1 second) map { t1 =>
      val t2 = System.currentTimeMillis()
      t2 - t1
    }

    val finalRes = Await.result(res, 2 second)
    println(s"got diff of $finalRes")
    assert (finalRes >= 1000)
    assert (finalRes <= 1100)
  }

  "retry" should "be called 3 times" in {

    val res = retry(3, Pause(1 second)) {
      case 0 => println("failing once")
        Future.failed(new RuntimeException("not good enough..."))

      case 1 => println("failing twice")
        Future.failed(new RuntimeException("still not good enough..."))

      case 2 => println("succeeding")
        Future.successful("great success !")
    }

    val finalResult = Await.result(res, 5 second)
    println(s"got $finalResult")
    assert (finalResult === "great success !")
  }

  "doubleDispatch" should "return the short call" in {
    val switch = new AtomicBoolean()
    val res = doubleDispatch(1 second) {
      if (switch.compareAndSet(false, true)) {

        println("producing slow one...")
        Future ("slow response") delay (3 second)

      } else {

        println("producing fast one...")
        Future("fast response") delay (1 second)

      }
    }

    val finalRes = Await.result(res, 4 second)
    println(s"got $finalRes")
    assert (finalRes === "fast response")
  }

  "seq" should "collect all successful results" in {

    val f1 = Future("first")
    val f2 = Future("second")
    val f3 = Future failed new RuntimeException("failed result")
    val f4 = Future("third")

    val input = Map("1" -> f1, "2" -> f2, "3" -> f3, "4" -> f4)
    val res = map(input, ContinueOnError)

    val finalRes = Await.result(res, 1 second)
    println(s"got $finalRes")
    assert (finalRes.size === 3)
  }

  "seq" should "fail immediately if error occurs" in {

    val f1 = schedule(2 second)("first")
    val f2 = schedule(3 second)("second")
    val f3 = Future failed new RuntimeException("failed result")
    val f4 = schedule(4 second)("third")

    val input = Map("1" -> f1, "2" -> f2, "3" -> f3, "4" -> f4)
    val res = map(input, FailOnError)

    try {
      val finalRes = Await.result(res, 10 milli)
      fail("should throw exception.")
    } catch {
      case e: RuntimeException => assert (e.getMessage === "failed result")
      case _: Throwable => fail("wrong exception type")
    }

  }


  "seq" should "stop on first error" in {

    val f1 = schedule(1 second)("first")
    val f3 = Future failed new RuntimeException("failed result") delay (2 second)
    val f2 = schedule(3 second)("second")
    val f4 = schedule(4 second)("third")

    val input = Map("1" -> f1, "2" -> f2, "3" -> f3, "4" -> f4)
    val res = map(input, StopOnError)

    val finalRes = Await.result(res, 5 second)
    assert (finalRes.size === 1)
  }


  "batch" should "divide large batch into small batches" in {
    val res = batch(1 to 6, 2, FailOnError) { num =>
      println(s"producing future $num")
      Future(s"result $num") delay (1 second)
    }

    val finalRes = Await.result(res, 4 second)
  }
}