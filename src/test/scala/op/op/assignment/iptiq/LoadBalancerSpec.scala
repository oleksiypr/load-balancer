package op.op.assignment.iptiq

import java.util.UUID
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import org.scalatest.{Matchers, WordSpec}

class LoadBalancerSpec extends WordSpec with Matchers {

  import LoadBalancer._

  "LoadBalancer" must {
    "register a list of providers" in {
      val testKit = BehaviorTestKit(idle(max = 2))

      val requester  = TestInbox[String]()

      val provider1 = TestInbox[Provider.Message]()
      val provider2 = TestInbox[Provider.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      provider1.expectMessage(Provider.Get(requester.ref))
      provider2.expectMessage(Provider.Get(requester.ref))
      provider1.expectMessage(Provider.Get(requester.ref))
    }

    "register not more than max providers" in {
      val testKit   = BehaviorTestKit(idle(max = 1))
      val requester = TestInbox[String]()

      val provider1 = TestInbox[Provider.Message]()
      val provider2 = TestInbox[Provider.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      provider1.expectMessage(Provider.Get(requester.ref))
      provider1.expectMessage(Provider.Get(requester.ref))
      provider2.hasMessages shouldBe false
    }

    "handle result from provider" in {
      val provider = TestInbox[Provider.Get]()
      val testKit = BehaviorTestKit(balancer(Vector(provider.ref))())

      val id = UUID.randomUUID().toString
      val requester  = TestInbox[String]()

      testKit.run(Response(id, requester.ref))
      requester.expectMessage(id)
    }

    "create heart beat checkers" in {
      val testKit = BehaviorTestKit(idle(max = 2))

      val provider1 = TestInbox[Provider.Message]()
      val provider2 = TestInbox[Provider.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))

      testKit.expectEffectType[SpawnedAnonymous[HeartBeat.Message]]
      testKit.expectEffectType[SpawnedAnonymous[HeartBeat.Message]]
    }
  }
}
