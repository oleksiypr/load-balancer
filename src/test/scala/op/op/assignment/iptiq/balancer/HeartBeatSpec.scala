package op.op.assignment.iptiq.balancer

import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.PreRestart
import op.op.assignment.iptiq.provider.Provider
import org.scalatest.{Matchers, WordSpec}

class HeartBeatSpec extends WordSpec with Matchers {

  import HeartBeat._

  "HeartBeat" must {
    "send Check message to alive provider" in {
      val balancerInbox = TestInbox[LoadBalancer.Message]()
      val providerInbox = TestInbox[Provider.Message]()

      val heartBeat = BehaviorTestKit(
        checker(
          index = 0,
          balancerInbox.ref,
          providerInbox.ref,
        )
      )

      heartBeat.signal(PreRestart)
      heartBeat.runOne()

      providerInbox.expectMessage(Provider.Check(heartBeat.ref, Alive))

      heartBeat.run(Alive)
      balancerInbox.hasMessages shouldBe false
    }

    "send Check message to not alive provider" in {
      val balancerInbox = TestInbox[LoadBalancer.Message]()
      val providerInbox = TestInbox[Provider.Message]()

      val heartBeat = BehaviorTestKit(
        checker(
          index = 0,
          balancerInbox.ref,
          providerInbox.ref,
        )
      )

      heartBeat.signal(PreRestart)
      heartBeat.runOne()

      providerInbox.receiveMessage()
      heartBeat.run(NotAlive)

      balancerInbox.expectMessage(LoadBalancer.ProviderDown(0))
    }
  }
}
