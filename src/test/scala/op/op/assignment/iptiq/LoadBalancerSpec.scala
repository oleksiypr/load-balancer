package op.op.assignment.iptiq

import java.util.UUID

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.event.Logging
import org.scalatest.{Matchers, WordSpec}

class LoadBalancerSpec extends WordSpec with Matchers {

  import LoadBalancer._

  "LoadBalancer" must {
    "register a list of providers" in {
      val testKit = BehaviorTestKit(balancer(Vector.empty)())

      val requester  = TestInbox[String]()

      val inbox1 = TestInbox[Query]()
      val inbox2 = TestInbox[Query]()

      testKit.run(Register(Vector(inbox1.ref, inbox2.ref)))

      testKit.run(Get(requester.ref))
      testKit.run(Get(requester.ref))
      testKit.run(Get(requester.ref))

      inbox1.expectMessage(Get(requester.ref))
      inbox2.expectMessage(Get(requester.ref))
      inbox1.expectMessage(Get(requester.ref))
    }

    "handle result from provider" in {
      val provider = TestInbox[Query]()
      val testKit = BehaviorTestKit(balancer(Vector(provider.ref))())

      val id = UUID.randomUUID().toString
      val requester  = TestInbox[String]()

      testKit.run(Response(id, requester.ref))
      requester.expectMessage(id)
    }
  }
}
