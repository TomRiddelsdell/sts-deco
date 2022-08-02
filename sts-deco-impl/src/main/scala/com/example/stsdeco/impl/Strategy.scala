package com.example.stsdeco.impl

import com.example.stsdeco.api.Execution

import play.api.libs.json.Json
import play.api.libs.json.Format
import java.time.LocalDateTime

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.AkkaTaggerAdapter
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.example.stsdeco.api.WrappedValue

import play.api.libs.json._

import scala.collection.immutable.Seq

/**
  * This provides an event sourced behavior. It has a state, [[StrategyState]], which
  * stores what the greeting should be (eg, "Hello").
  *
  * Event sourced entities are interacted with by sending them commands. This
  * aggregate supports two commands, a [[AddExecutions]] command, which is
  * used to change the greeting, and a [[Hello]] command, which is a read
  * only command which returns a greeting to the name specified by the command.
  *
  * Commands get translated to events, and it's the events that get persisted.
  * Each event will have an event handler registered for it, and an
  * event handler simply applies an event to the current state. This will be done
  * when the event is first created, and it will also be done when the aggregate is
  * loaded from the database - each event will be replayed to recreate the state
  * of the aggregate.
  *
  * This aggregate defines one event, the [[GreetingMessageChanged]] event,
  * which is emitted when a [[AddExecutions]] command is received.
  */
object StrategyBehavior {

  /**
    * Given a sharding [[EntityContext]] this function produces an Akka [[Behavior]] for the aggregate.
    */ 
  def create(entityContext: EntityContext[StrategyCommand]): Behavior[StrategyCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId)
      .withTagger(
        // Using Akka Persistence Typed in Lagom requires tagging your events
        // in Lagom-compatible way so Lagom ReadSideProcessors and TopicProducers
        // can locate and follow the event streams.
        AkkaTaggerAdapter.fromLagom(entityContext, StrategyEvent.Tag)
      )

  }
  /*
   * This method is extracted to write unit tests that are completely independendant to Akka Cluster.
   */
  private[impl] def create(persistenceId: PersistenceId) = EventSourcedBehavior
      .withEnforcedReplies[StrategyCommand, StrategyEvent, StrategyState](
        persistenceId = persistenceId,
        emptyState = StrategyState.initial,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
}

/**
  * The current state of the Aggregate.
  */
case class StrategyState(executions: List[Execution], timestamp: String) {
  def applyCommand(cmd: StrategyCommand): ReplyEffect[StrategyEvent, StrategyState] =
    cmd match {
      case x: AddExecutions      => onAddExecutions(x)
    }

  def applyEvent(evt: StrategyEvent): StrategyState =
    evt match {
      case ExecutionsAdded(strategyId, execs) => updateExecutions(execs)
    }

  private def onAddExecutions(
    cmd: AddExecutions
  ): ReplyEffect[StrategyEvent, StrategyState] =
    Effect
      .persist(ExecutionsAdded(cmd.strategyId, cmd.executions))
      .thenReply(cmd.replyTo) { _ =>
        AcceptedExecutions
      }

  private def updateExecutions(newExecutions: List[Execution]) =
    copy(executions = executions ++ newExecutions, LocalDateTime.now().toString)
}

object StrategyState {

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  def initial: StrategyState = StrategyState(List[Execution](), LocalDateTime.now.toString)

  /**
    * The [[EventSourcedBehavior]] instances (aka Aggregates) run on sharded actors inside the Akka Cluster.
    * When sharding actors and distributing them across the cluster, each aggregate is
    * namespaced under a typekey that specifies a name and also the type of the commands
    * that sharded actor can receive.
    */
  val typeKey = EntityTypeKey[StrategyCommand]("StsdecoStrategy")

  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the aggregate gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[StrategyState] = Json.format
}

/**
  * This interface defines all the events that the StsdecoAggregate supports.
  */
sealed trait StrategyEvent extends AggregateEvent[StrategyEvent] {
  def aggregateTag: AggregateEventTag[StrategyEvent] = StrategyEvent.Tag
}

object StrategyEvent {
  val Tag: AggregateEventTag[StrategyEvent] = AggregateEventTag[StrategyEvent]
}

final case class StrategyId private (val value: String) extends AnyVal with WrappedValue[String]

object StrategyId {
  implicit val format: Format[StrategyId] = Json.format
}
/**
  * An event that represents a change in greeting message.
  */
case class ExecutionsAdded(strategyId: StrategyId, executions: List[Execution]) extends StrategyEvent

object ExecutionsAdded {

  /**
    * Format for the greeting message changed event.
    *
    * Events get stored and loaded from the database, hence a JSON format
    * needs to be declared so that they can be serialized and deserialized.
    */
  implicit val format: Format[ExecutionsAdded] = Json.format
}

/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
trait StrategyCommandSerializable

/**
  * This interface defines all the commands that the StsdecoAggregate supports.
  */
sealed trait StrategyCommand
    extends StrategyCommandSerializable

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[Confirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class AddExecutions(strategyId: StrategyId, executions: List[Execution], replyTo: ActorRef[ConfirmationOfExecutions])
    extends StrategyCommand

sealed trait ConfirmationOfExecutions

case object ConfirmationOfExecutions {
  implicit val format: Format[ConfirmationOfExecutions] = new Format[ConfirmationOfExecutions] {
    override def reads(json: JsValue): JsResult[ConfirmationOfExecutions] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[RejectedExecutions](json)
      else
        Json.fromJson[AcceptedExecutions](json)
    }

    override def writes(o: ConfirmationOfExecutions): JsValue = {
      o match {
        case acc: AcceptedExecutions => Json.toJson(acc)
        case rej: RejectedExecutions => Json.toJson(rej)
      }
    }
  }
}

sealed trait AcceptedExecutions extends ConfirmationOfExecutions

case object AcceptedExecutions extends AcceptedExecutions {
  implicit val format: Format[AcceptedExecutions] =
    Format(Reads(_ => JsSuccess(AcceptedExecutions)), Writes(_ => Json.obj()))
}

case class RejectedExecutions(reason: String) extends ConfirmationOfExecutions

object RejectedExecutions {
  implicit val format: Format[RejectedExecutions] = Json.format
}

