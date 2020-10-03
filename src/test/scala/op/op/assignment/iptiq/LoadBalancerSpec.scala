package op.op.assignment.iptiq

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

      val providerInbox1 = TestInbox[Provider.Message]()
      val providerInbox2 = TestInbox[Provider.Message]()

      testKit.run(Register(Vector(providerInbox1.ref, providerInbox2.ref)))

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      providerInbox1.expectMessage(Provider.Get(requester.ref))
      providerInbox2.expectMessage(Provider.Get(requester.ref))
      providerInbox1.expectMessage(Provider.Get(requester.ref))
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
      val providers = Providers(Vector.empty)
      val testKit   = BehaviorTestKit(balancer(providers)(current = 0))

      val id        = UUID.randomUUID().toString
      val requester = TestInbox[String]()

      testKit.run(Response(id, requester.ref))
      requester.expectMessage(id)
    }

    "invoke available providers only" in {
      val providerInbox1 = TestInbox[Provider.Get]()
      val providerInbox2 = TestInbox[Provider.Get]()

      val available   = ProviderState(providerInbox1.ref, LoadBalancer.Available)
      val unavailable = ProviderState(providerInbox2.ref, LoadBalancer.Unavailable)
      val providers   = Providers(Vector(available, unavailable))

      val testKit   = BehaviorTestKit(balancer(providers)(current = 0))
      val requester = TestInbox[String]()

      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))
      testKit.run(Request(requester.ref))

      providerInbox1.expectMessage(Provider.Get(requester.ref))
      providerInbox2.hasMessages shouldBe false
      providerInbox1.expectMessage(Provider.Get(requester.ref))
    }

    "create heart beat checkers" in {
      val testKit = BehaviorTestKit(idle(max = 2))

      val provider1 = TestInbox[Provider.Message]()
      val provider2 = TestInbox[Provider.Message]()

      testKit.run(Register(Vector(provider1.ref, provider2.ref)))

      testKit.expectEffectType[SpawnedAnonymous[HeartBeat.Message]]
      testKit.expectEffectType[SpawnedAnonymous[HeartBeat.Message]]
    }

    "handle ProviderStatus messages" in {
      val providerInbox = TestInbox[Provider.Get]()

      val providers = Providers(
        Vector(
          ProviderState(
            providerInbox.ref,
            LoadBalancer.Unavailable
          )
        )
      )

      val requester = TestInbox[String]()
      val testKit   = BehaviorTestKit(balancer(providers)(current = 0))

      testKit.run(Request(requester.ref))
      providerInbox.hasMessages shouldBe false

      testKit.run(ProviderUp(0))
      testKit.run(Request(requester.ref))
      providerInbox.expectMessage(Provider.Get(requester.ref))
    }
  }
}
