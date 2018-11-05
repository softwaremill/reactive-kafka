/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package docs.javadsl;

import akka.Done;
import akka.actor.ActorSystem;
import akka.kafka.ProducerMessage;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.Config;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;

abstract class ProducerExample {
  protected final ActorSystem system = ActorSystem.create("example");

  protected final Materializer materializer = ActorMaterializer.create(system);

  // #producer
  // #settings
  final Config config = system.settings().config().getConfig("akka.kafka.producer");
  final ProducerSettings<String, String> producerSettings =
      ProducerSettings.create(config, new StringSerializer(), new StringSerializer())
          .withBootstrapServers("localhost:9092");
  // #settings
  final org.apache.kafka.clients.producer.Producer<String, String> kafkaProducer =
      producerSettings.createKafkaProducer();
  // #producer

  protected void terminateWhenDone(CompletionStage<Done> result) {
    result
        .exceptionally(
            e -> {
              system.log().error(e, e.getMessage());
              return Done.getInstance();
            })
        .thenAccept(d -> system.terminate());
  }
}

class PlainSinkExample extends ProducerExample {
  public static void main(String[] args) {
    new PlainSinkExample().demo();
  }

  public void demo() {
    // #plainSink
    CompletionStage<Done> done =
        Source.range(1, 100)
            .map(number -> number.toString())
            .map(value -> new ProducerRecord<String, String>("topic1", value))
            .runWith(Producer.plainSink(producerSettings), materializer);
    // #plainSink

    terminateWhenDone(done);
  }
}

class PlainSinkWithProducerExample extends ProducerExample {
  public static void main(String[] args) {
    new PlainSinkExample().demo();
  }

  public void demo() {
    // #plainSinkWithProducer
    CompletionStage<Done> done =
        Source.range(1, 100)
            .map(number -> number.toString())
            .map(value -> new ProducerRecord<String, String>("topic1", value))
            .runWith(Producer.plainSink(producerSettings, kafkaProducer), materializer);
    // #plainSinkWithProducer

    terminateWhenDone(done);
  }
}

class ObserveMetricsExample extends ProducerExample {
  public static void main(String[] args) {
    new PlainSinkExample().demo();
  }

  public void demo() {
    // #producerMetrics
    Map<org.apache.kafka.common.MetricName, ? extends org.apache.kafka.common.Metric> metrics =
        kafkaProducer.metrics(); // observe metrics
    // #producerMetrics
  }
}

class ProducerFlowExample extends ProducerExample {
  public static void main(String[] args) {
    new ProducerFlowExample().demo();
  }

  <KeyType, ValueType, PassThroughType>
      ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> createMessage(
          KeyType key, ValueType value, PassThroughType passThrough) {
    // #singleMessage
    ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> message =
        ProducerMessage.single(new ProducerRecord<>("topicName", key, value), passThrough);
    // #singleMessage
    return message;
  }

  <KeyType, ValueType, PassThroughType>
      ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> createMultiMessage(
          KeyType key, ValueType value, PassThroughType passThrough) {
    // #multiMessage
    ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> multiMessage =
        ProducerMessage.multi(
            Arrays.asList(
                new ProducerRecord<>("topicName", key, value),
                new ProducerRecord<>("anotherTopic", key, value)),
            passThrough);
    // #multiMessage
    return multiMessage;
  }

  <KeyType, ValueType, PassThroughType>
      ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> createPassThroughMessage(
          KeyType key, ValueType value, PassThroughType passThrough) {
    // #passThroughMessage
    ProducerMessage.Envelope<KeyType, ValueType, PassThroughType> ptm =
        ProducerMessage.passThrough(passThrough);
    // #passThroughMessage
    return ptm;
  }

  public void demo() {
    // #flow
    CompletionStage<Done> done =
        Source.range(1, 100)
            .map(
                number -> {
                  int partition = 0;
                  String value = String.valueOf(number);
                  ProducerMessage.Envelope<String, String, Integer> msg =
                      ProducerMessage.single(
                          new ProducerRecord<>("topic1", partition, "key", value), number);
                  return msg;
                })
            .via(Producer.flexiFlow(producerSettings))
            .map(
                result -> {
                  if (result instanceof ProducerMessage.Result) {
                    ProducerMessage.Result<String, String, Integer> res =
                        (ProducerMessage.Result<String, String, Integer>) result;
                    ProducerRecord<String, String> record = res.message().record();
                    RecordMetadata meta = res.metadata();
                    return meta.topic()
                        + "/"
                        + meta.partition()
                        + " "
                        + res.offset()
                        + ": "
                        + record.value();
                  } else if (result instanceof ProducerMessage.MultiResult) {
                    ProducerMessage.MultiResult<String, String, Integer> res =
                        (ProducerMessage.MultiResult<String, String, Integer>) result;
                    return res.getParts()
                        .stream()
                        .map(
                            part -> {
                              RecordMetadata meta = part.metadata();
                              return meta.topic()
                                  + "/"
                                  + meta.partition()
                                  + " "
                                  + part.metadata().offset()
                                  + ": "
                                  + part.record().value();
                            })
                        .reduce((acc, s) -> acc + ", " + s);
                  } else {
                    return "passed through";
                  }
                })
            .runWith(Sink.foreach(System.out::println), materializer);
    // #flow

    terminateWhenDone(done);
  }
}
