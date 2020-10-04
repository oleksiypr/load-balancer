package op.op.assignment.iptiq.balancer

import java.util.UUID
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import org.scalatest.{Matchers, WordSpec}

class LoadBalancerSpec extends WordSpec with Matchers {

  import LoadBalancer._

  "LoadBalancer" must {
    "register a list of providers" in {
      val testKit   = BehaviorTestKit(idle(max = 2))
      val requester = TestInbox[String]()

      val providerInbox1 = TestInbox[ProviderProxy.Message]()
      val providerInbox2 = TestInbox[ProviderProxy.Message]()

      testKit.run(Register(Vector(providerInbox1.ref, providerInbox2.ref)))

      testKit.run(Request(requester.ref))
      providerInbox1.hasMessages shouldBe false
      providerInbox2.hasMessages shouldBe false

      testKit.run(ProviderUp(0))
      testKit.run(ProviderUp(1))

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      providerInbox1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
      providerInbox2.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
      providerInbox1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
    }

    "register not more than max providers" in {
      val testKit   = BehaviorTestKit(idle(max = 1))
      val requester = TestInbox[String]()

      val provider1 = TestInbox[ProviderProxy.Message]()
      val provider2 = TestInbox[ProviderProxy.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))
      testKit.run(ProviderUp(0))

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      provider1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
      provider1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
      provider2.hasMessages shouldBe false
    }

    "handle result from provider" in {
      val providers = State(Vector.empty)
      val testKit   = BehaviorTestKit(balancer(providers, roundRobin)(current = 0))

      val id        = UUID.randomUUID().toString
      val requester = TestInbox[String]()

      testKit.run(Response(id, requester.ref))
      requester.expectMessage(id)
    }

    "invoke available providers only" in {
      val providerInbox1 = TestInbox[ProviderProxy.Get]()
      val providerInbox2 = TestInbox[ProviderProxy.Get]()

      val available   = ProviderState(providerInbox1.ref, Available)
      val unavailable = ProviderState(providerInbox2.ref, Unavailable)
      val providers   = State(Vector(available, unavailable))

      val testKit   = BehaviorTestKit(balancer(providers, roundRobin)(current = 0))
      val requester = TestInbox[String]()

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      providerInbox1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
      providerInbox2.hasMessages shouldBe false
      providerInbox1.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
    }

    "create heart beat checkers" in {
      val testKit = BehaviorTestKit(idle(max = 2))

      val provider1 = TestInbox[ProviderProxy.Message]()
      val provider2 = TestInbox[ProviderProxy.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))
      testKit.expectEffectType[Spawned[HeartBeat.Message]]
    }

    "handle ProviderStatus messages" in {
      val providerInbox = TestInbox[ProviderProxy.Get]()

      val providers = State(
        Vector(
          ProviderState(
            providerInbox.ref,
            Unavailable
          )
        )
      )

      val requester = TestInbox[String]()
      val testKit   = BehaviorTestKit(balancer(providers, roundRobin)(current = 0))

      testKit.run(Request(requester.ref))
      providerInbox.hasMessages shouldBe false

      testKit.run(ProviderUp(0))
      testKit.run(Request(requester.ref))
      providerInbox.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))

      testKit.run(ProviderDown(0))
      testKit.run(Request(requester.ref))
      providerInbox.hasMessages shouldBe false
    }

    "include/exclude a specific provider into the balancer" in {
      val providerInbox = TestInbox[ProviderProxy.Get]()

      val providers = State(
        Vector(
          ProviderState(
            providerInbox.ref,
            Available
          )
        )
      )

      val requester = TestInbox[String]()
      val testKit   = BehaviorTestKit(balancer(providers, roundRobin)(current = 0))

      testKit.run(Request(requester.ref))
      providerInbox.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))

      testKit.run(Exclude(0))
      testKit.run(Request(requester.ref))
      providerInbox.hasMessages shouldBe false

      testKit.run(ProviderUp(0))
      testKit.run(Request(requester.ref))
      providerInbox.hasMessages shouldBe false

      testKit.run(Include(0))
      testKit.run(ProviderUp(0))
      testKit.run(Request(requester.ref))
      providerInbox.expectMessage(ProviderProxy.Get(requester.ref, testKit.ref))
    }
  }
}
