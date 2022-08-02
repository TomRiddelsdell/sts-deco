package com.example.stsdeco.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

object StsdecoService  {
  val TOPIC_NAME = "greetings"
  val EXECUTIONS_TOPIC = "executions"
}

/**
  * The sts-deco service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the StsdecoService.
  */
trait StsdecoService extends Service {

  /**
    * Example: curl http://localhost:9000/api/hello/Alice
    */
  def hello(id: String): ServiceCall[NotUsed, String]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
    * "Hi"}' http://localhost:9000/api/hello/Alice
    */
  def useGreeting(id: String): ServiceCall[GreetingMessage, Done]

  /**
    * This gets published to Kafka.
    */
  def greetingsTopic(): Topic[GreetingMessageChanged]


  def allGreetings(): ServiceCall[NotUsed, Seq[Greeting]]

  def addExecutions(strategyId: String): ServiceCall[ExecutionList, Done]
  def executionsTopic(): Topic[ExecutionsAdded]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("sts-deco")
      .withCalls(
        pathCall("/api/hello/:id", hello _),
        pathCall("/api/hello/:id", useGreeting _),
        pathCall("/api/:strategyId/addexecutions", addExecutions _)
      )
      .withTopics(
        topic(StsdecoService.TOPIC_NAME, greetingsTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[GreetingMessageChanged](_.name)
          ),
        topic(StsdecoService.EXECUTIONS_TOPIC, executionsTopic _)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[ExecutionsAdded](_.strategyId)
          )

      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
  * The greeting message class.
  */
case class GreetingMessage(message: String)

object GreetingMessage {
  /**
    * Format for converting greeting messages to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
}

case class ExecutionList(executions: List[Execution])

object ExecutionList {
  /**
    * Format for converting greeting messages to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[ExecutionList] = Json.format[ExecutionList]
}



/**
  * The greeting message class used by the topic stream.
  * Different than [[GreetingMessage]], this message includes the name (id).
  */
case class GreetingMessageChanged(name: String, message: String)

object GreetingMessageChanged {
  /**
    * Format for converting greeting messages to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[GreetingMessageChanged] = Json.format[GreetingMessageChanged]
}

/**
  * An event that represents a change in greeting message.
  */
case class ExecutionsAdded(strategyId: String, executions: List[Execution])

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
  * The Greeting is both the message and the person that message is meant for.
  */
case class Greeting(name: String, message: String)

object Greeting {
  implicit val format: Format[Greeting] = Json.format
}
