/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.testkit

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem

class TestKitDebuggerCompensationSpec
    extends AkkaSpec(ConfigFactory.parseString("""
  akka.test.debugger-compensation = on
  akka.test.debugger-detection-interval = 100ms
""")) {

  private var mockNanoTime: Long = System.nanoTime()

  override def now: FiniteDuration =
    (mockNanoTime - testKitSettings.suspensionDetector.totalSuspendedNanos).nanos

  private def step(d: FiniteDuration): Unit = {
    mockNanoTime += d.toNanos
  }

  private def simulateSuspension(d: FiniteDuration): Unit = {
    step(d)
    testKitSettings.suspensionDetector.simulateSuspension(d.toNanos)
  }

  "TestKit debugger compensation" must {

    "adjust 'now' based on detector" in {
      val start = now
      step(1.second)
      val middle = now
      middle should ===(start + 1.second)

      simulateSuspension(2.seconds)
      val end = now
      // 'now' should not have advanced during suspension
      end should ===(middle)
    }

    "adjust 'remaining' inside 'within'" in {
      within(2.seconds) {
        val r1 = remaining
        r1 should be <= 2.seconds
        simulateSuspension(3.seconds)
        val r2 = remaining
        // remaining should stay roughly the same because logical time didn't advance,
        // even though 3s of real time passed.
        r2.toMillis should be(r1.toMillis +- 100)
        r2 should be > 0.seconds
      }
    }

    "compensate for suspension during receiveOne" in {
      val timeout = 1.second

      spawn() {
        Thread.sleep(200) // real time to let the loop start
        simulateSuspension(2.seconds)
        step(100.millis)
        testActor ! "hello"
      }

      within(timeout) {
        // Without compensation, this would timeout because 2.3s of real time passed.
        expectMsg("hello")
      }
    }

    "still timeout eventually" in {
      intercept[AssertionError] {
        within(1.second) {
          simulateSuspension(1.second)
          // logical time is still 0.
          // we step 1.1s logical time, it should timeout.
          step(1100.millis)
          expectMsg(remaining, "never-going-to-happen")
        }
      }
    }

    "respect debugger-compensation = off" in {
      val sys2 = ActorSystem("DisabledComp", ConfigFactory.parseString("akka.test.debugger-compensation = off"))
      try {
        val settings = TestKitExtension(sys2)
        settings.DebuggerCompensation should ===(false)
        settings.suspensionDetector should ===(JvmSuspensionDetector.Disabled)
      } finally {
        shutdown(sys2)
      }
    }
  }
}
