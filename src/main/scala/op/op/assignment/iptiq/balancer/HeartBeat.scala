package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart}
import op.op.assignment.iptiq.provider.Provider

object HeartBeat {

  import Behaviors.{receiveMessage, setup, receiveSignal}

  sealed trait Message
  case object Beat extends Message
  case object Alive extends Message

  def checker(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = receiveSignal {

    case (ctx, PreRestart) =>
      ctx.self ! Beat
      heartBeat(index, balancer, provider)
  }

  private def heartBeat(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Beat =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        Behaviors.same
      case Alive =>
        ctx.log.info(s"Provider[$index] alive")
        Behaviors.same
    }
  }
}
