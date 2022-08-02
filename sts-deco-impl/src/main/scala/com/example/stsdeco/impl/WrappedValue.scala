package com.example.stsdeco.api

import scala.util.{Try, Failure, Success}
import scala.util.control.NoStackTrace
import play.api.libs.json._

trait WrappedValue[T] extends Any 
{
    def value: T

    override def toString = this.getClass.getName + "(" + value.toString + ")"

    override def equals(other: Any): Boolean = 
    {
        if (this.getClass.isInstance(other)) 
        {
            value == other.asInstanceOf[WrappedValue[T]].value
        }
        else 
        {
            false
        }
    }

    override def hashCode: Int = value.hashCode
}

object WrappedValue {
    trait Companion {
        type InnerType
        type WrappedType <: WrappedValue[InnerType]

        protected def construct(value: InnerType): WrappedType
        protected def validate(value: InnerType): Option[String]

        def from(value: InnerType): Try[WrappedType] = {
            validate(value) match {
                case Some(message) => Failure(new IllegalArgumentException(message) with NoStackTrace)
                case None => Success(construct(value))
            }
        }

        def unapply(wrapped: WrappedType): Option[InnerType] = Some(wrapped.value)

            implicit def ordering(implicit ord: Ordering[InnerType]): Ordering[WrappedType] = new WrappedOrdering(ord)

        class WrappedOrdering(ord: Ordering[InnerType]) extends Ordering[WrappedType] {
            override def compare(x: WrappedType, y: WrappedType): Int = {
                ord.compare(x.value, y.value)
            }
        }
    }

}