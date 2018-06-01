/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.kafka.javadsl

import java.util.concurrent.CompletionStage

import akka.{Done, NotUsed}
import akka.kafka.ProducerMessage._
import akka.kafka.{ConsumerMessage, ProducerSettings, scaladsl}
import akka.stream.javadsl.{Flow, Sink}
import org.apache.kafka.clients.producer.{ProducerRecord, Producer => KProducer}

import scala.compat.java8.FutureConverters.FutureOps

/**
 * Akka Stream connector for publishing messages to Kafka topics.
 */
object Producer {

  /**
   * Create a sink for publishing records to Kafka topics.
   *
   * The [[org.apache.kafka.clients.producer.ProducerRecord Kafka ProducerRecord]] contains the topic name to which the record is being sent, an optional
   * partition number, and an optional key and value.
   */
  def plainSink[K, V](settings: ProducerSettings[K, V]): Sink[ProducerRecord[K, V], CompletionStage[Done]] =
    scaladsl.Producer.plainSink(settings)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Create a sink for publishing records to Kafka topics.
   *
   * The [[org.apache.kafka.clients.producer.ProducerRecord Kafka ProducerRecord]] contains the topic name to which the record is being sent, an optional
   * partition number, and an optional key and value.
   *
   * Supports sharing a Kafka Producer instance.
   */
  def plainSink[K, V](
    settings: ProducerSettings[K, V],
    producer: KProducer[K, V]
  ): Sink[ProducerRecord[K, V], CompletionStage[Done]] =
    scaladsl.Producer.plainSink(settings, producer)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Create a sink that is aware of the [[ConsumerMessage.CommittableOffset committable offset]]
   * from a [[Consumer.committableSource]]. It will commit the consumer offset when the message has
   * been published successfully to the topic.
   *
   * It publishes records to Kafka topics conditionally:
   *
   * - [[akka.kafka.ProducerMessage.Message Message]] publishes a single message to its topic, and commits the offset
   *
   * - [[akka.kafka.ProducerMessage.MultiMessage MultiMessage]] publishes all messages in its `records` field, and commits the offset
   *
   * - [[akka.kafka.ProducerMessage.PassThroughMessage PassThroughMessage]] does not publish anything, but commits the offset
   *
   * Note that there is a risk that something fails after publishing but before
   * committing, so it is "at-least once delivery" semantics.
   */
  def commitableSink[K, V, IN <: Messages[K, V, ConsumerMessage.Committable]](settings: ProducerSettings[K, V]): Sink[IN, CompletionStage[Done]] =
    scaladsl.Producer.commitableSink(settings)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Create a sink that is aware of the [[ConsumerMessage.CommittableOffset committable offset]]
   * from a [[Consumer.committableSource]]. It will commit the consumer offset when the message has
   * been published successfully to the topic.
   *
   * It publishes records to Kafka topics conditionally:
   *
   * - [[akka.kafka.ProducerMessage.Message Message]] publishes a single message to its topic, and commits the offset
   *
   * - [[akka.kafka.ProducerMessage.MultiMessage MultiMessage]] publishes all messages in its `records` field, and commits the offset
   *
   * - [[akka.kafka.ProducerMessage.PassThroughMessage PassThroughMessage]] does not publish anything, but commits the offset
   *
   *
   * Note that there is always a risk that something fails after publishing but before
   * committing, so it is "at-least once delivery" semantics.
   *
   * Supports sharing a Kafka Producer instance.
   */
  def commitableSink[K, V](
    settings: ProducerSettings[K, V],
    producer: KProducer[K, V]
  ): Sink[Messages[K, V, ConsumerMessage.Committable], CompletionStage[Done]] =
    scaladsl.Producer.commitableSink(settings, producer)
      .mapMaterializedValue(_.toJava)
      .asJava

  /**
   * Create a flow to publish records to Kafka topics and then pass it on.
   *
   * The records must be wrapped in a [[akka.kafka.ProducerMessage.Message Message]] and continue in the stream as [[akka.kafka.ProducerMessage.Result Result]].
   *
   * The messages support the possibility to pass through arbitrary data, which can for example be a [[ConsumerMessage.CommittableOffset CommittableOffset]]
   * or [[ConsumerMessage.CommittableOffsetBatch CommittableOffsetBatch]] that can
   * be committed later in the flow.
   */
  def flow[K, V, PassThrough](settings: ProducerSettings[K, V]): Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed] =
    scaladsl.Producer.flow(settings)
      .asJava
      .asInstanceOf[Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed]]

  /**
   * Create a flow to conditionally publish records to Kafka topics and then pass it on.
   *
   * It publishes records to Kafka topics conditionally:
   *
   * - [[akka.kafka.ProducerMessage.Message Message]] publishes a single message to its topic, and continues in the stream as [[akka.kafka.ProducerMessage.Result Result]]
   *
   * - [[akka.kafka.ProducerMessage.MultiMessage MultiMessage]] publishes all messages in its `records` field, and continues in the stream as [[akka.kafka.ProducerMessage.MultiResult MultiResult]]
   *
   * - [[akka.kafka.ProducerMessage.PassThroughMessage PassThroughMessage]] does not publish anything, and continues in the stream as [[akka.kafka.ProducerMessage.PassThroughResult PassThroughResult]]
   *
   * The messages support the possibility to pass through arbitrary data, which can for example be a [[ConsumerMessage.CommittableOffset CommittableOffset]]
   * or [[ConsumerMessage.CommittableOffsetBatch CommittableOffsetBatch]] that can
   * be committed later in the flow.
   */
  def flow2[K, V, PassThrough](settings: ProducerSettings[K, V]): Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed] =
    scaladsl.Producer.flow2(settings)
      .asJava
      .asInstanceOf[Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed]]

  /**
   * Create a flow to publish records to Kafka topics and then pass it on.
   *
   * The records must be wrapped in a [[akka.kafka.ProducerMessage.Message Message]] and continue in the stream as [[akka.kafka.ProducerMessage.Result Result]].
   *
   * The messages support the possibility to pass through arbitrary data, which can for example be a [[ConsumerMessage.CommittableOffset CommittableOffset]]
   * or [[ConsumerMessage.CommittableOffsetBatch CommittableOffsetBatch]] that can
   * be committed later in the flow.
   *
   * Supports sharing a Kafka Producer instance.
   */
  def flow[K, V, PassThrough](
    settings: ProducerSettings[K, V],
    producer: KProducer[K, V]
  ): Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed] =
    scaladsl.Producer.flow(settings, producer)
      .asJava
      .asInstanceOf[Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed]]

  /**
   * Create a flow to conditionally publish records to Kafka topics and then pass it on.
   *
   * It publishes records to Kafka topics conditionally:
   *
   * - [[akka.kafka.ProducerMessage.Message Message]] publishes a single message to its topic, and continues in the stream as [[akka.kafka.ProducerMessage.Result Result]]
   *
   * - [[akka.kafka.ProducerMessage.MultiMessage MultiMessage]] publishes all messages in its `records` field, and continues in the stream as [[akka.kafka.ProducerMessage.MultiResult MultiResult]]
   *
   * - [[akka.kafka.ProducerMessage.PassThroughMessage PassThroughMessage]] does not publish anything, and continues in the stream as [[akka.kafka.ProducerMessage.PassThroughResult PassThroughResult]]
   *
   * The messages support the possibility to pass through arbitrary data, which can for example be a [[ConsumerMessage.CommittableOffset CommittableOffset]]
   * or [[ConsumerMessage.CommittableOffsetBatch CommittableOffsetBatch]] that can
   * be committed later in the flow.
   *
   * Supports sharing a Kafka Producer instance.
   */
  def flow2[K, V, PassThrough](
    settings: ProducerSettings[K, V],
    producer: KProducer[K, V]
  ): Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed] =
    scaladsl.Producer.flow2(settings, producer)
      .asJava
      .asInstanceOf[Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed]]
}
