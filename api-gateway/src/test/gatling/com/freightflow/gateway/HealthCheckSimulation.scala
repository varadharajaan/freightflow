package com.freightflow.gateway

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class HealthCheckSimulation extends Simulation {

  private val baseUrl = sys.props.getOrElse("gatling.baseUrl", "http://localhost:8080")
  private val users = Integer.getInteger("gatling.users", 20)
  private val durationSeconds = Integer.getInteger("gatling.durationSeconds", 30)

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .userAgentHeader("freightflow-gatling")

  private val healthScenario = scenario("Gateway Health Check")
    .exec(
      http("GET /actuator/health")
        .get("/actuator/health")
        .check(status.is(200))
    )
    .pause(250.millis, 750.millis)

  setUp(
    healthScenario.inject(
      rampUsers(users).during(10.seconds),
      constantUsersPerSec(users.toDouble / 2).during(durationSeconds.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile4.lt(200),
      global.successfulRequests.percent.gt(99)
    )
}
