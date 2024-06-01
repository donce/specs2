package org.specs2
package execute

import ResultExecution.*
import Results.{given}
import org.specs2.fp.Monoid
import org.specs2.fp.syntax.foldMap

/** Typeclass trait for anything that can be transformed to a Result
  */
trait AsResult[T]:
  def asResult(t: =>T): Result

object AsResult extends AsResultLowImplicits:
  /** implicit typeclass instance to create results from Booleans */
  given booleanAsResult: AsResult[Boolean] with
    def asResult(t: =>Boolean): Result =
      summon[Conversion[Boolean, Result]](t)

  /** typeclass instance for types which are convertible to Result */
  given asResult[R](using convert: R => Result): AsResult[R] with
    def asResult(r: =>R): Result =
      ResultExecution.execute(convert(r))

  /** typeclass instance for lists of results */
  given resultSeq[R: AsResult]: AsResult[List[R]] with
    def asResult(rs: =>List[R]): Result =
      given Monoid[Result] = Result.ResultFailureMonoid
      rs.foldMap(r => AsResult[R](r))

trait AsResultLowImplicits:
  /** typeclass instance for types which are convertible to Result */
  given AsResult[Unit] with
    def asResult(r: =>Unit): Result =
      Result.resultOrSuccess(r)

  /** nicer syntax to use the AsResult syntax: AsResult(r) */
  def apply[R: AsResult](r: =>R): Result =
    summon[AsResult[R]].asResult(r)

  /** @return a Result but throw exceptions if it is not a success */
  def effectively[R: AsResult](r: =>R): Result =
    ResultExecution.effectively(AsResult(r))

  /** @return a Result always, even when there are specs2 exceptions (when using ThrownExpectations) */
  def safely[R: AsResult](r: =>R): Result =
    ResultExecution.execute(AsResult(r))

/** Type class to transform any value to a Result
  */
class AnyValueAsResult[T] extends AsResult[T]:
  def asResult(t: =>T) =
    executeEither(t)(using _.toString) match
      case Left(e)  => new DecoratedResult((), e)
      case Right(v) => new DecoratedResult(v, Success())
