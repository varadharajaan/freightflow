package com.freightflow.booking

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BookingApiSimulation extends Simulation {

  private val baseUrl = sys.props.getOrElse("gatling.baseUrl", "http://localhost:8081")
  private val adminToken = sys.props.getOrElse("gatling.authToken",
    throw new IllegalArgumentException("gatling.authToken JVM property is required"))

  private val users = Integer.getInteger("gatling.users", 20)
  private val durationSeconds = Integer.getInteger("gatling.durationSeconds", 30)
  private val customerId = sys.props.getOrElse("gatling.customerId", "11111111-1111-1111-1111-111111111111")

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .authorizationHeader(s"Bearer $adminToken")
    .userAgentHeader("freightflow-gatling")

  private val readBookings = scenario("Booking API Read")
    .exec(
      http("GET /api/v1/bookings")
        .get(s"/api/v1/bookings?customerId=$customerId")
        .check(status.is(200))
    )
    .pause(250.millis, 750.millis)

  setUp(
    readBookings.inject(
      rampUsers(users).during(10.seconds),
      constantUsersPerSec(users.toDouble / 2).during(durationSeconds.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile4.lt(200),
      global.successfulRequests.percent.gt(99)
    )
}
