package com.example.stsdeco.impl

import com.example.stsdeco.api
import com.example.stsdeco.api.StsdecoService
import com.example.stsdeco.api.ExecutionList
import akka.Done
import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.transport.BadRequest

/**
  * Implementation of the StsdecoService.
  */
class StsdecoServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends StsdecoService {

  /**
    * Looks up the entity for the given ID.
    */
  private def entityRef(id: String): EntityRef[StsdecoCommand] =
    clusterSharding.entityRefFor(StsdecoState.typeKey, id)

  /**
    * Looks up the Strategy for the given ID.
    */
  private def strategyRef(id: String): EntityRef[StrategyCommand] =
    clusterSharding.entityRefFor(StrategyState.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def hello(id: String): ServiceCall[NotUsed, String] = ServiceCall {
    _ =>
      // Look up the sharded entity (aka the aggregate instance) for the given ID.
      val ref = entityRef(id)

      // Ask the aggregate instance the Hello command.
      ref
        .ask[Greeting](replyTo => Hello(id, replyTo))
        .map(greeting => greeting.message)
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the sharded entity (aka the aggregate instance) for the given ID.
    val ref = entityRef(id)

    // Tell the aggregate to use the greeting message specified.
    ref
      .ask[Confirmation](
        replyTo => UseGreetingMessage(request.message, replyTo)
      )
      .map {
        case Accepted => Done
        case _        => throw BadRequest("Can't upgrade the greeting message.")
      }
  }

  def addExecutions(strategyId: String): ServiceCall[ExecutionList, Done] = ServiceCall { request =>
    // Look up the sharded entity (aka the aggregate instance) for the given ID.
    val ref = strategyRef(strategyId)

    // Tell the aggregate to use the greeting message specified.
    ref
      .ask[ConfirmationOfExecutions](
        replyTo => AddExecutions(StrategyId(strategyId), request.executions, replyTo)
      )
      .map {
        case AcceptedExecutions => Done
        case _                  => throw BadRequest("Could not accept executions")
      }
  }
  override def greetingsTopic(): Topic[api.GreetingMessageChanged] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(StsdecoEvent.Tag, fromOffset)
        .map(ev => (convertGreetingsEvent(ev), ev.offset))
    }

  private def convertGreetingsEvent(helloEvent: EventStreamElement[StsdecoEvent]): api.GreetingMessageChanged = 
  {
    helloEvent.event match {
      case GreetingMessageChanged(msg) =>
        api.GreetingMessageChanged(helloEvent.entityId, msg)
    }
  }

  override def executionsTopic(): Topic[api.ExecutionsAdded] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(StrategyEvent.Tag, fromOffset)
        .map(ev => (convertStrategyEvent(ev), ev.offset))
    }

  private def convertStrategyEvent(strategyEvent: EventStreamElement[StrategyEvent]): api.ExecutionsAdded = 
  {
    strategyEvent.event match {
      case ExecutionsAdded(strategyId, execs) =>
        api.ExecutionsAdded(strategyEvent.entityId, execs)
    }
  }



  override def allGreetings(): ServiceCall[NotUsed, Seq[api.Greeting]] =
    ServiceCall { _ => /*
      greetingsRepository
        .getAll()
        .map(_.map(toApi).toSeq)*/
        Future(List[api.Greeting]())
    }

  /*private def toApi: ReadSideGreeting => api.Greeting = { readSideModel =>
    api.Greeting(readSideModel.name, readSideModel.message)
  }*/

}
