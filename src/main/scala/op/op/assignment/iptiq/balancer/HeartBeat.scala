package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart}
import op.op.assignment.iptiq.provider.Provider

object HeartBeat {

  import Behaviors.{receiveMessage, setup}

  sealed trait Message
  case object Beat extends Message

  def checker(
     balancer: LoadBalancer.SelfRef,
     provider: Provider.SelfRef
   ): Behavior[Message] =
    Behaviors.receiveSignal {
      case (ctx, PreRestart) =>
        ctx.self ! Beat
        heartBeat(balancer, provider)
    }

  private def heartBeat(
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Beat =>
        provider ! Provider.Check(replyTo = ctx.self)
        Behaviors.same
    }
  }
}
