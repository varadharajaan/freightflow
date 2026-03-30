package contracts.actuator

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Health endpoint returns UP")

    request {
        method GET()
        url "/actuator/health"
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
                status: "UP"
        )
    }
}
