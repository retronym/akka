/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.testkit

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JvmSuspensionDetectorSpec extends AnyWordSpec with Matchers {
  "JvmSuspensionDetector" must {
    "accumulate nothing for normal ticks" in {
      var time = 1000L
      val interval = 100L
      val detector = new JvmSuspensionDetector(
        isEnabled = false, // Don't start the thread
        intervalMs = interval,
        nanoTime = () => time,
        sleep = _ => ())

      time += interval * 1_000_000L
      detector.tick()
      detector.totalSuspendedNanos should ===(0L)

      time += (interval * 1.5).toLong * 1_000_000L
      detector.tick()
      detector.totalSuspendedNanos should ===(0L)
    }

    "accumulate gaps larger than twice the interval" in {
      var time = 1000L
      val interval = 100L
      val detector =
        new JvmSuspensionDetector(isEnabled = false, intervalMs = interval, nanoTime = () => time, sleep = _ => ())

      // 3x interval gap
      time += interval * 3 * 1_000_000L
      detector.tick()
      // elapsed = 300ms, expected = 100ms. Gap = 200ms
      detector.totalSuspendedNanos should ===(200L * 1_000_000L)

      // Another 4x gap
      time += interval * 4 * 1_000_000L
      detector.tick()
      // elapsed = 400ms, expected = 100ms. Gap = 300ms. Total = 500ms
      detector.totalSuspendedNanos should ===(500L * 1_000_000L)
    }

    "pin the threshold behaviour (exactly 2x interval)" in {
      var time = 1000L
      val interval = 100L
      val detector =
        new JvmSuspensionDetector(isEnabled = false, intervalMs = interval, nanoTime = () => time, sleep = _ => ())

      // Exactly 2x interval: should NOT accumulate according to `elapsed > expectedNanos * 2L`
      time += interval * 2 * 1_000_000L
      detector.tick()
      detector.totalSuspendedNanos should ===(0L)

      // Just over 2x interval: should accumulate
      time += (interval * 2 * 1_000_000L) + 1
      detector.tick()
      detector.totalSuspendedNanos should ===((interval * 1_000_000L) + 1)
    }
  }
}
