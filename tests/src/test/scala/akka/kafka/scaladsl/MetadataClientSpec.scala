/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.kafka.scaladsl

import akka.kafka.KafkaConsumerActor
import akka.kafka.testkit.scaladsl.TestcontainersKafkaLike
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import org.apache.kafka.common.TopicPartition

import scala.language.postfixOps
import scala.concurrent.duration._

class MetadataClientSpec extends SpecBase with TestcontainersKafkaLike {

  "MetadataClient" must {
    "fetch beginning offsets for given partitions" in assertAllStagesStopped {
      val topic1 = createTopic(1)
      val group1 = createGroupId(1)
      val partition0 = new TopicPartition(topic1, 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      awaitProduce(produce(topic1, 1 to 10))

      val beginningOffsets = MetadataClient
        .getBeginningOffsets(consumerActor, Set(partition0), 1 seconds)
        .futureValue

      beginningOffsets(partition0) shouldBe 0

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fail in case of an exception during fetch beginning offsets for non-existing topics" in assertAllStagesStopped {
      val group1 = createGroupId(1)
      val nonExistingPartition = new TopicPartition("non-existing topic", 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      val beginningOffsetsFuture = MetadataClient
        .getBeginningOffsets(consumerActor, Set(nonExistingPartition), 1 seconds)

      beginningOffsetsFuture.failed.futureValue shouldBe a[org.apache.kafka.common.errors.InvalidTopicException]

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fetch beginning offset for given partition" in assertAllStagesStopped {
      val topic1 = createTopic(1)
      val group1 = createGroupId(1)
      val partition0 = new TopicPartition(topic1, 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      awaitProduce(produce(topic1, 1 to 10))

      val beginningOffset = MetadataClient
        .getBeginningOffsetForPartition(consumerActor, partition0, 1 seconds)
        .futureValue

      beginningOffset shouldBe 0

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fail in case of an exception during fetch beginning offset for non-existing topic" in assertAllStagesStopped {
      val group1 = createGroupId(1)
      val nonExistingPartition = new TopicPartition("non-existing topic", 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      val beginningOffsetFuture = MetadataClient
        .getBeginningOffsetForPartition(consumerActor, nonExistingPartition, 1 seconds)

      beginningOffsetFuture.failed.futureValue shouldBe a[org.apache.kafka.common.errors.InvalidTopicException]

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fetch end offsets for given partitions" in assertAllStagesStopped {
      val topic1 = createTopic(1)
      val group1 = createGroupId(1)
      val partition0 = new TopicPartition(topic1, 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      awaitProduce(produce(topic1, 1 to 10))

      val endOffsets = MetadataClient
        .getEndOffsets(consumerActor, Set(partition0), 1 seconds)
        .futureValue

      endOffsets(partition0) shouldBe 10

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fail in case of an exception during fetch end offsets for non-existing topics" in assertAllStagesStopped {
      val group1 = createGroupId(1)
      val nonExistingPartition = new TopicPartition("non-existing topic", 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      val endOffsetsFuture = MetadataClient
        .getEndOffsets(consumerActor, Set(nonExistingPartition), 1 seconds)

      endOffsetsFuture.failed.futureValue shouldBe a[org.apache.kafka.common.errors.InvalidTopicException]

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fetch end offset for given partition" in assertAllStagesStopped {
      val topic1 = createTopic(1)
      val group1 = createGroupId(1)
      val partition0 = new TopicPartition(topic1, 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      awaitProduce(produce(topic1, 1 to 10))

      val endOffset = MetadataClient
        .getEndOffsetForPartition(consumerActor, partition0, 1 seconds)
        .futureValue

      endOffset shouldBe 10

      consumerActor ! KafkaConsumerActor.Stop
    }

    "fail in case of an exception during fetch end offset for non-existing topic" in assertAllStagesStopped {
      val group1 = createGroupId(1)
      val nonExistingPartition = new TopicPartition("non-existing topic", 0)
      val consumerSettings = consumerDefaults.withGroupId(group1)
      val consumerActor = system.actorOf(KafkaConsumerActor.props(consumerSettings))

      val endOffsetFuture = MetadataClient
        .getEndOffsetForPartition(consumerActor, nonExistingPartition, 1 seconds)

      endOffsetFuture.failed.futureValue shouldBe a[org.apache.kafka.common.errors.InvalidTopicException]

      consumerActor ! KafkaConsumerActor.Stop
    }
  }
}
