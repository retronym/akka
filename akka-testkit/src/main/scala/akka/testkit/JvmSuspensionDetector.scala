/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.testkit

import java.util.concurrent.atomic.AtomicLong

import akka.annotation.InternalApi

object JvmSuspensionDetector {

  /** A detector that is permanently disabled and always reports zero suspended nanos. */
  val Disabled: JvmSuspensionDetector = new JvmSuspensionDetector(isEnabled = false)
}

/**
 * Detects JVM suspension caused by debugger breakpoints by comparing the expected
 * heartbeat interval against the actual elapsed `System.nanoTime`. Any gap larger
 * than twice the interval is attributed to JVM suspension and accumulated so that
 * testkit timeout calculations can compensate.
 *
 * The `nanoTime` and `sleep` parameters exist for deterministic unit testing; production
 * code uses the defaults.
 *
 * INTERNAL API
 */
@InternalApi
private[akka] class JvmSuspensionDetector(
    val isEnabled: Boolean = true,
    val intervalMs: Long = 100L,
    nanoTime: () => Long = () => System.nanoTime(),
    sleep: Long => Unit = ms => Thread.sleep(ms)) {

  private val _totalSuspendedNanos = new AtomicLong(0L)

  // Mutable only from the detector thread (or tick() in tests); volatile so other
  // threads always read the most recently written value.
  @volatile private var lastNano: Long = nanoTime()

  /** Run one detection tick. Called by the background thread; also accessible for testing. */
  private[testkit] def tick(): Unit = {
    val now = nanoTime()
    val elapsed = now - lastNano
    val expectedNanos = intervalMs * 1_000_000L
    // A gap more than twice the expected interval is attributed to JVM suspension.
    // The threshold avoids false positives from scheduler jitter under load.
    if (elapsed > expectedNanos * 2L)
      _totalSuspendedNanos.addAndGet(elapsed - expectedNanos)
    lastNano = now
  }

  if (isEnabled) {
    val t = new Thread(new Runnable {
      override def run(): Unit =
        try while (!Thread.interrupted()) { sleep(intervalMs); tick() } catch {
          case _: InterruptedException => /* normal shutdown */
        }
    }, "akka-testkit-suspension-detector")
    t.setDaemon(true)
    t.start()
  }

  /** Total nanoseconds of JVM suspension detected since this instance was created. */
  def totalSuspendedNanos: Long = _totalSuspendedNanos.get()

  /**
   * Inject a simulated suspension for testing. Not for production use.
   * INTERNAL API
   */
  @InternalApi
  private[testkit] def simulateSuspension(nanos: Long): Unit =
    _totalSuspendedNanos.addAndGet(nanos)
}
