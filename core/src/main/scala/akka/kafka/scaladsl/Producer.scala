/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.kafka.scaladsl

import akka.kafka.ProducerMessage._
import akka.kafka.internal.ProducerStage
import akka.kafka.{ConsumerMessage, ProducerSettings}
import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.{Done, NotUsed}
import org.apache.kafka.clients.producer.{ProducerRecord, Producer => KProducer}

import scala.concurrent.Future

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
  def plainSink[K, V](settings: ProducerSettings[K, V]): Sink[ProducerRecord[K, V], Future[Done]] =
    Flow[ProducerRecord[K, V]].map(Message(_, NotUsed))
      .via(flow(settings))
      .toMat(Sink.ignore)(Keep.right)

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
  ): Sink[ProducerRecord[K, V], Future[Done]] =
    Flow[ProducerRecord[K, V]].map(Message(_, NotUsed))
      .via(flow(settings, producer))
      .toMat(Sink.ignore)(Keep.right)

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
  def commitableSink[K, V](settings: ProducerSettings[K, V]): Sink[Messages[K, V, ConsumerMessage.Committable], Future[Done]] =
    flow2[K, V, ConsumerMessage.Committable](settings)
      .mapAsync(settings.parallelism)(_.passThrough.commitScaladsl())
      .toMat(Sink.ignore)(Keep.right)

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
  ): Sink[Messages[K, V, ConsumerMessage.Committable], Future[Done]] =
    flow2[K, V, ConsumerMessage.Committable](settings, producer)
      .mapAsync(settings.parallelism)(_.passThrough.commitScaladsl())
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Create a flow to publish records to Kafka topics and then pass it on.
   *
   * The records must be wrapped in a [[akka.kafka.ProducerMessage.Message Message]] and continue in the stream as [[akka.kafka.ProducerMessage.Result Result]].
   *
   * The messages support the possibility to pass through arbitrary data, which can for example be a [[ConsumerMessage.CommittableOffset CommittableOffset]]
   * or [[ConsumerMessage.CommittableOffsetBatch CommittableOffsetBatch]] that can
   * be committed later in the flow.
   */
  def flow[K, V, PassThrough](settings: ProducerSettings[K, V]): Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed] = {
    val flow = Flow.fromGraph(new ProducerStage.DefaultProducerStage[K, V, PassThrough, Message[K, V, PassThrough], Result[K, V, PassThrough]](
      settings.closeTimeout,
      closeProducerOnStop = true,
      () => settings.createKafkaProducer()
    )).mapAsync(settings.parallelism)(identity)

    flowWithDispatcher(settings, flow)
  }

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
  def flow2[K, V, PassThrough](settings: ProducerSettings[K, V]): Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed] = {
    val flow = Flow.fromGraph(new ProducerStage.DefaultProducerStage[K, V, PassThrough, Messages[K, V, PassThrough], Results[K, V, PassThrough]](
      settings.closeTimeout,
      closeProducerOnStop = true,
      () => settings.createKafkaProducer()
    )).mapAsync(settings.parallelism)(identity)

    flowWithDispatcherMessages(settings, flow)
  }

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
  ): Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed] = {
    val flow = Flow.fromGraph(new ProducerStage.DefaultProducerStage[K, V, PassThrough, Message[K, V, PassThrough], Result[K, V, PassThrough]](
      closeTimeout = settings.closeTimeout,
      closeProducerOnStop = false,
      producerProvider = () => producer
    )).mapAsync(settings.parallelism)(identity)

    flowWithDispatcher(settings, flow)
  }

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
  ): Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed] = {
    val flow = Flow.fromGraph(new ProducerStage.DefaultProducerStage[K, V, PassThrough, Messages[K, V, PassThrough], Results[K, V, PassThrough]](
      closeTimeout = settings.closeTimeout,
      closeProducerOnStop = false,
      producerProvider = () => producer
    )).mapAsync(settings.parallelism)(identity)

    flowWithDispatcherMessages(settings, flow)
  }

  private def flowWithDispatcher[PassThrough, V, K](settings: ProducerSettings[K, V], flow: Flow[Message[K, V, PassThrough], Result[K, V, PassThrough], NotUsed]) = {
    if (settings.dispatcher.isEmpty) flow
    else flow.withAttributes(ActorAttributes.dispatcher(settings.dispatcher))
  }

  private def flowWithDispatcherMessages[PassThrough, V, K](settings: ProducerSettings[K, V], flow: Flow[Messages[K, V, PassThrough], Results[K, V, PassThrough], NotUsed]) = {
    if (settings.dispatcher.isEmpty) flow
    else flow.withAttributes(ActorAttributes.dispatcher(settings.dispatcher))
  }
}
