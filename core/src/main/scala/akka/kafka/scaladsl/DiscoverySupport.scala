/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.kafka.scaladsl

import akka.actor.ActorSystem
import akka.discovery.Discovery
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.util.JavaDurationConverters._

/**
 * Scala API.
 *
 * Reads Kafka bootstrap servers from configured sources via [[akka.discovery.Discovery]] configuration.
 */
object DiscoverySupport {

  /**
   * Use Akka Discovery to read the addresses for `serviceName` within `lookupTimeout`.
   */
  def bootstrapServers(
      serviceName: String,
      lookupTimeout: FiniteDuration
  )(implicit system: ActorSystem): Future[String] = {
    import system.dispatcher
    val discovery = Discovery(system).discovery
    discovery.lookup(serviceName, lookupTimeout).map { resolved =>
      resolved.addresses
        .map { target =>
          // TODO is it allowed to not specify a port?
          val port = target.port
            .getOrElse(throw new IllegalArgumentException(s"port missing for $serviceName ${target.host}"))
          s"${target.host}:$port"
        }
        .mkString(",")
    }
  }

  /**
   * Expect a `service` section in Config and use Akka Discovery to read the addresses for `name` within `lookup-timeout`.
   */
  def bootstrapServers(config: Config)(implicit system: ActorSystem): Future[String] =
    if (config.hasPath("service")) {
      val serviceName = config.getString("service.name")
      val lookupTimeout = config.getDuration("service.lookup-timeout").asScala
      bootstrapServers(serviceName, lookupTimeout)
    } else throw new IllegalArgumentException(s"config $config does not contain `service` section")

  /**
   * Expects a `service` section in the given Config and reads the given service name's address
   * to be used as `bootstrapServers`.
   */
  def consumerBootstrapServers[K, V](
      config: Config
  )(implicit system: ActorSystem): ConsumerSettings[K, V] => Future[ConsumerSettings[K, V]] = {
    import system.dispatcher
    settings =>
      bootstrapServers(config)
        .map { bootstrapServers =>
          settings.withBootstrapServers(bootstrapServers)
        }
  }

  /**
   * Expects a `service` section in the given Config and reads the given service name's address
   * to be used as `bootstrapServers`.
   */
  def producerBootstrapServers[K, V](
      config: Config
  )(implicit system: ActorSystem): ProducerSettings[K, V] => Future[ProducerSettings[K, V]] = {
    import system.dispatcher
    settings =>
      bootstrapServers(config)
        .map { bootstrapServers =>
          settings.withBootstrapServers(bootstrapServers)
        }
  }

}