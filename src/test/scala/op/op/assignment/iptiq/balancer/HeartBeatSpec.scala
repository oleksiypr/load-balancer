package op.op.assignment.iptiq.balancer

import akka.actor.testkit.typed.Effect.{NoEffects, ReceiveTimeoutSet}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.PreRestart
import op.op.assignment.iptiq.provider.Provider
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.duration._

class HeartBeatSpec extends WordSpec with Matchers {

  import HeartBeat._

  "HeartBeat" must {
    "start sending Check message to provider" in {
      val balancerInbox = TestInbox[LoadBalancer.Message]()
      val providerInbox = TestInbox[Provider.Message]()

      val testKit = BehaviorTestKit(
        checker(
          index = 0,
          balancerInbox.ref,
          providerInbox.ref,
        )
      )

      testKit.signal(PreRestart)
      testKit.runOne()

      testKit.expectEffect(ReceiveTimeoutSet(1.second, NotAlive))
      providerInbox.receiveMessage()

      testKit.run(Alive)
      balancerInbox.hasMessages shouldBe false
    }

    "send Check message to alive provider" in {
      val balancerInbox = TestInbox[LoadBalancer.Message]()
      val providerInbox = TestInbox[Provider.Message]()

      val testKit = BehaviorTestKit(
        heartBeat(
          index = 0,
          balancerInbox.ref,
          providerInbox.ref
        )
      )

      testKit.run(Alive)
      providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))

      balancerInbox.hasMessages shouldBe false
    }

    "send Check message to not alive provider" in {
      val balancerInbox = TestInbox[LoadBalancer.Message]()
      val providerInbox = TestInbox[Provider.Message]()

      val testKit = BehaviorTestKit(
        heartBeat(
          index = 0,
          balancerInbox.ref,
          providerInbox.ref
        )
      )

      testKit.run(NotAlive)
      balancerInbox.expectMessage(LoadBalancer.ProviderDown(0))
      providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))

      testKit.run(NotAlive)
      providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))
      balancerInbox.hasMessages shouldBe false
    }

    "become alive again" when {
      "it has successfully been `heartbeat checked` for 2 consecutive times" in {
        val balancerInbox = TestInbox[LoadBalancer.Message]()
        val providerInbox = TestInbox[Provider.Message]()

        val testKit = BehaviorTestKit(
          notAlive(
            index = 0,
            balancerInbox.ref,
            providerInbox.ref
          )
        )

        testKit.run(Alive)
        providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))
        testKit.expectEffect(NoEffects)

        testKit.run(NotAlive)
        providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))
        testKit.expectEffect(NoEffects)

        testKit.run(Alive)
        providerInbox.expectMessage(Provider.Check(testKit.ref, Alive))
        testKit.expectEffect(NoEffects)
        balancerInbox.hasMessages shouldBe false

        testKit.run(Alive)
        providerInbox.receiveMessage()
        testKit.expectEffect(NoEffects)

        testKit.run(Alive)
        providerInbox.receiveMessage()
        testKit.expectEffect(NoEffects)

        balancerInbox.expectMessage(LoadBalancer.ProviderUp(0))
      }
    }
  }
}
