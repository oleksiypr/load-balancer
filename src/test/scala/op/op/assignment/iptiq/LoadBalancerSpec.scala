package op.op.assignment.iptiq

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

      val sender  = TestInbox[String]()

      val inbox1 = TestInbox[Query]()
      val inbox2 = TestInbox[Query]()

      testKit.run(Register(Vector(inbox1.ref, inbox2.ref)))

      testKit.run(Get(sender.ref))
      testKit.run(Get(sender.ref))
      testKit.run(Get(sender.ref))

      inbox1.expectMessage(Get(sender.ref))
      inbox2.expectMessage(Get(sender.ref))
      inbox1.expectMessage(Get(sender.ref))
    }
  }
}
