package org.specs2
package control

import io.*

/** specs2 logger support
  */
trait Logger:
  def warn(message: String, doIt: Boolean = true): Operation[Unit]
  def info(message: String, doIt: Boolean = true): Operation[Unit]
  def exception(t: Throwable, doIt: Boolean = true): Operation[Unit]

  // derived operations
  def warnAndFail[A](warnMessage: String, failureMessage: String, doIt: Boolean = true): Operation[A] =
    warn(warnMessage, doIt).flatMap { _ => Operation.fail[A](failureMessage) }

/** Logger implementation directing messages to the console
  */
case class ConsoleLogger() extends Logger:

  def exception(t: Throwable, verbose: Boolean = false): Operation[Unit] =
    Operation.delayed(println("[ERROR] " + t.getMessage)).flatMap { _ =>
      (if verbose then Operation.delayed(t.printStackTrace) else Operation.unit)
    }

  def warn(message: String, doIt: Boolean = true): Operation[Unit] =
    if doIt then Operation.delayed((println("[WARN] " + message)))
    else Operation.unit

  def info(message: String, doIt: Boolean = true): Operation[Unit] =
    if doIt then Operation.delayed((println("[INFO] " + message)))
    else Operation.unit

object NoLogger extends Logger:
  def warn(message: String, doIt: Boolean = true): Operation[Unit] = Operation.unit
  def info(message: String, doIt: Boolean = true): Operation[Unit] = Operation.unit
  def exception(t: Throwable, doIt: Boolean = true): Operation[Unit] = Operation.unit

case class StringOutputLogger(output: StringOutput) extends Logger:
  def warn(message: String, doIt: Boolean = true): Operation[Unit] =
    Operation.ok(if doIt then output.printf(message))

  def info(message: String, doIt: Boolean = true): Operation[Unit] =
    Operation.ok(if doIt then output.printf(message))

  def exception(t: Throwable, doIt: Boolean = true): Operation[Unit] =
    Operation.ok(if doIt then output.printf(t.getMessage))
