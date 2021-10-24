package org.specs2
package matcher

import org.specs2.matcher.describe.Diffable
import org.specs2.text.Quote._

import scala.reflect.ClassTag
import scala.reflect.Selectable.reflectiveSelectable

/**
 * This trait provides matchers which are applicable to any type of value
 */
trait AnyMatchers extends AnyBaseMatchers with AnyBeHaveMatchers
object AnyMatchers extends AnyMatchers

private[specs2]
trait AnyBaseMatchers {

  /** matches if a == true */
  def beTrue = new BeTrueMatcher
  /** matches if a == false */
  def beFalse = (new BeTrueMatcher).not

  /** matches if a eq b */
  def beTheSameAs[T <: AnyRef](t: =>T) = new BeTheSameAs(t)
  /** alias for beTheSameAs */
  def be[T <: AnyRef](t: =>T) = beTheSameAs(t)

  /** matches if a == b */
  def be_==[T](t: =>T) = beEqualTo(t)
  /** matches if a != b */
  def be_!=[T](t: =>T) = be_==(t).not
  /** matches if a == b */
  def be_===[T : Diffable](t: =>T) = beTypedEqualTo(t)
  /** matches if a == b */
  def ===[T : Diffable](t: =>T) = be_===(t)
  /** matches if a != b */
  def be_!==[T : Diffable](t: =>T) = be_===(t).not
  /** matches if a != b */
  def !==[T : Diffable](t: =>T) = be_!==(t)
  /** matches if a == b */
  def beEqualTo[T](t: =>T) = new BeEqualTo(t)
  /** matches if a == b */
  def equalTo[T](t: =>T) = beEqualTo(t)
  /** matches if a == b */
  def beTypedEqualTo[T : Diffable](t: =>T) =
    new EqualityMatcher(t)

  /** matches if a == b */
  def typedEqualTo[T](t: =>T) =
    beTypedEqualTo(t)

  /** matches if a == b after an implicit conversion */
  def be_==~[T : Diffable, S](s: =>S)(implicit convert: S => T): Matcher[T] = new EqualityMatcher(convert(s)).
    adapt(identity, identity, identity)
  /** matches if a == b after an implicit conversion */
  def ==~[T : Diffable, S](s: =>S)(implicit convert: S => T): Matcher[T] = be_==~(s)

  /** negate a matcher */
  def not[T](m: Matcher[T]) = m.not

  /** matches if a.isEmpty */
  def beEmpty[T](implicit convert: T => Any { def isEmpty: Boolean }) = new Matcher[T] {
    def apply[S <: T](iterable: Expectable[S]) = {
      // we need to pattern match on arrays otherwise we get a reflection exception
      iterable.value match {
        case a: Array[?] =>
          result(convert(a).isEmpty,
            iterable.description + " is empty",
            iterable.description + " is not empty", iterable)

        case _ =>
          result(convert(iterable.value).isEmpty,
            iterable.description + " is empty",
            iterable.description + " is not empty", iterable)
      }
    }
  }

  /** matches if the value is null */
  def beNull[T] = new BeNull[T]

  /** matches if a is null when v is null and a is not null when v is not null */
  def beAsNullAs[T](a: =>T) = new Matcher[T](){
    def apply[S <: T](y: Expectable[S]) = {
      val x = a
      result(x == null && y.value == null || x != null && y.value != null,
             "both values are null",
             if (x == null) y.description + " is not null" else q(x) + " is not null" +
             y.optionalDescription.map(" but " + _ + " is null").getOrElse(""),
             y)
    }
  }

  /** matches if t.toSeq.exists(_ == v) */
  def beOneOf[T](t: T*): Matcher[T] = BeOneOf(t)
  /** alias for beOneOf */
  def beAnyOf[T](t: T*): Matcher[T] = BeOneOf(t)

  /** matches if the value returns a successful result when applied to a PartialFunction */
  def beLike[T](pattern: PartialFunction[T, MatchResult[?]]): Matcher[T] = new Matcher[T] {
    def apply[S <: T](a: Expectable[S]) = {
      val r = if (pattern.isDefinedAt(a.value)) pattern.apply(a.value) else MatchFailure("", "", a)
      result(r.isSuccess,
             a.description + " is correct: " + r.message,
             a.description + " is incorrect: " + r.message,
             a)
    }
  }
  /** matches if v.getClass == c */
  def haveClass[T : ClassTag]: Matcher[AnyRef] = new Matcher[AnyRef] {
    def apply[S <: AnyRef](x: Expectable[S]) = {
      val c = implicitly[ClassTag[T]].runtimeClass
      val xClass = x.value.getClass
      result(xClass == c,
             x.description + " has class " + q(c.getName),
             x.description + " doesn't have class " + q(c.getName) + ". It has class " + q(xClass.getName),
             x)
    }
  }
  /** matches if c.isAssignableFrom(v.getClass.getSuperclass) */
  def haveSuperclass[T : ClassTag]: Matcher[AnyRef] = new Matcher[AnyRef] {
    def apply[S <: AnyRef](x: Expectable[S]) = {
      val c = implicitly[ClassTag[T]].runtimeClass
      val xClass = x.value.getClass
      result(c.isAssignableFrom(xClass.getSuperclass),
             x.description + " has super class " + q(c.getName),
             x.description + " doesn't have super class " + q(c.getName) + ". It has super class " + q(xClass.getSuperclass.getName),
             x)
    }
  }

  /** matches if x.getClass.getInterfaces.contains(T) */
  def haveInterface[T : ClassTag]: Matcher[AnyRef] = new Matcher[AnyRef] {
    def apply[S <: AnyRef](x: Expectable[S]) = {
      val c = implicitly[ClassTag[T]].runtimeClass
      val xClass = x.value.getClass
      result(xClass.getInterfaces.contains(c),
             x.description + " has interface " + q(c.getName),
             x.description + " doesn't have interface " + q(c.getName) + ". It has interface " + xClass.getInterfaces.mkString(", "),
             x)
    }
  }

  /** matches if v.isAssignableFrom(c) */
  def beAssignableFrom[T : ClassTag]: Matcher[Class[?]] = new Matcher[Class[?]] {
    def apply[S <: Class[?]](x: Expectable[S]) = {
      val c = implicitly[ClassTag[T]].runtimeClass
      result(x.value.isAssignableFrom(c),
             x.description + " is assignable from " + q(c.getName),
             x.description + " is not assignable from " + q(c.getName),
             x)
    }
  }

  def beAnInstanceOf[T: ClassTag]: Matcher[AnyRef] = new Matcher[AnyRef] {
    def apply[S <: AnyRef](x: Expectable[S]) = {
      val c = implicitly[ClassTag[T]].runtimeClass
      val xClass = x.value.getClass
      val xWithClass = x.mapDescription(d => s"'$d: ${xClass.getName}'")
      result(c.isAssignableFrom(xClass),
             xWithClass.description + " is an instance of " + q(c.getName),
             xWithClass.description + " is not an instance of " + q(c.getName),
             xWithClass)
    }
  }
}


/**
 * Matcher for a boolean value which must be true
 */
class BeTrueMatcher extends Matcher[Boolean] {
  def apply[S <: Boolean](v: Expectable[S]) = {
    result(v.value, v.description + " is true", v.description + " is false", v)
  }
}

/**
 * Equality Matcher
 */
class BeEqualTo(t: =>Any) extends EqualityMatcher[Any](t)
/**
 * This matcher always matches any value of type T
 */
case class AlwaysMatcher[T]() extends Matcher[T] {
  def apply[S <: T](e: Expectable[S]) = result(true, "ok", "ko", e)
}
/**
 * This matcher never matches any value of type T
 */
case class NeverMatcher[T]() extends Matcher[T] {
  def apply[S <: T](e: Expectable[S]) = result(false, "ok", "ko", e)
}
/**
 * This trait allows to write expressions like
 *
 *  `1 must be equalTo(1)`
 */
trait AnyBeHaveMatchers extends BeHaveMatchers { outer: AnyMatchers =>
  implicit def anyBeHaveMatcher[T](result: MatchResult[T]): AnyBeHaveMatchers[T] = new AnyBeHaveMatchers(result)
  class AnyBeHaveMatchers[T](result: MatchResult[T]) {
    def be_==(t: T) = result(outer.be_==(t))
    def be_!=(t: T) = result(outer.be_!=(t))
    def be_===(t: T)(implicit di: Diffable[T]) = result(outer.be_===(t))
    def be_!==(t: T)(implicit di: Diffable[T]) = result(outer.be_!==(t))
    def be_==~[S](s: =>S)(implicit convert: S => T, di: Diffable[T]) = result(outer.be_==~[T, S](s))
    def equalTo(t: T) = result(outer.be_==(t))
    def asNullAs(a: =>T) = result(outer.beAsNullAs(a))
    def beAnyOf(t: T*) = result(outer.beAnyOf(t:_*))
    def beOneOf(t: T*) = result(outer.beOneOf(t:_*))
    def anyOf(t: T*) = result(outer.beAnyOf(t:_*))
    def oneOf(t: T*) = result(outer.beOneOf(t:_*))
    def beNull = result(outer.beNull)
  }

  implicit def toAnyRefMatcherResult[T <: AnyRef](result: MatchResult[T]): AnyRefMatcherResult[T] = new AnyRefMatcherResult(result)
  class AnyRefMatcherResult[T <: AnyRef](result: MatchResult[T]) {
    def beTheSameAs(t: T) = result(outer.beTheSameAs(t))
  }

  implicit def toAnyMatcherResult(result: MatchResult[AnyRef]): AnyMatcherResult = new AnyMatcherResult(result)
  class AnyMatcherResult(result: MatchResult[AnyRef]) {
    def haveClass[T : ClassTag] = result(outer.haveClass[T])
    def anInstanceOf[T : ClassTag] = result(beAnInstanceOf[T])
  }

  implicit def toClassMatcherResult[T : ClassTag](result: MatchResult[Class[?]]): ClassMatcherResult[T] = new ClassMatcherResult[T](result)
  class ClassMatcherResult[T : ClassTag](result: MatchResult[Class[?]]) {
    def assignableFrom = result(outer.beAssignableFrom[T])
  }

  implicit def anyWithEmpty[T](result: MatchResult[T])(implicit convert: T => Any { def isEmpty: Boolean }): AnyWithEmptyMatchers[T] =
    new AnyWithEmptyMatchers(result)

  class AnyWithEmptyMatchers[T](result: MatchResult[T])(implicit convert: T => Any { def isEmpty: Boolean }) {
    def empty = result(outer.beEmpty[T])
    def beEmpty = result(outer.beEmpty[T])
  }
  implicit def toBeLikeResultMatcher[T](result: MatchResult[T]): BeLikeResultMatcher[T] = new BeLikeResultMatcher(result)
  class BeLikeResultMatcher[T](result: MatchResult[T]) {
    def like(pattern: =>PartialFunction[T, MatchResult[?]]) = result(outer.beLike(pattern))
    def likeA(pattern: =>PartialFunction[T, MatchResult[?]]) = result(outer.beLike(pattern))
  }
  def asNullAs[T](a: =>T) = beAsNullAs(a)
  def like[T](pattern: =>PartialFunction[T, MatchResult[?]]) = beLike(pattern)
  def beLikeA[T](pattern: =>PartialFunction[T, MatchResult[?]]) = beLike(pattern)
  def likeA[T](pattern: =>PartialFunction[T, MatchResult[?]]) = beLike(pattern)
  def empty[T <: Any { def isEmpty: Boolean }] = beEmpty[T]
  def oneOf[T](t: T*) = beOneOf(t:_*)
  def anyOf[T](t: T*) = beAnyOf(t:_*)
  def klass[T : ClassTag]: Matcher[AnyRef] = outer.haveClass[T]
  def superClass[T : ClassTag]: Matcher[AnyRef] = outer.haveSuperclass[T]
  def interface[T : ClassTag]: Matcher[AnyRef] = outer.haveInterface[T]
  def assignableFrom[T : ClassTag] = outer.beAssignableFrom[T]
  def anInstanceOf[T : ClassTag] = outer.beAnInstanceOf[T]
}

class BeTheSameAs[T <: AnyRef](t: =>T) extends Matcher[T] {
  def apply[S <: T](a: Expectable[S]) = {
    val b = t
    result(a.value eq b, a.description + " is the same as " + q(b), a.description + " is not the same as " + q(b), a)
  }
}

class BeNull[T] extends Matcher[T] {
  def apply[S <: T](value: Expectable[S]) = {
    result(value.value == null,
           value.description + " is null",
           value.description + " is not null", value)
  }
}

case class BeOneOf[T](t: Seq[T]) extends Matcher[T] {
  def apply[S <: T](y: Expectable[S]) = {
    val x = t
    result(x.contains(y.value),
      s"${q(y.description)} is contained in ${q(x.mkString(", "))}",
      s"${q(y.description)} is not contained in ${q(x.mkString(", "))}",
      y)
  }
}
