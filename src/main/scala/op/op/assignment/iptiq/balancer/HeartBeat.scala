package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart}
import op.op.assignment.iptiq.provider.Provider
import scala.concurrent.duration._

object HeartBeat {

  import Behaviors.{receiveMessage, setup, receiveSignal}

  sealed trait Message
  private case object Start extends Message
  case object Alive extends Message
  case object NotAlive extends Message

  def checker(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    timeout: FiniteDuration = 1.second
  ): Behavior[Message] = receiveSignal {

    case (ctx, PreRestart) =>
      ctx.self ! Start
      heartBeat(index, balancer, provider, timeout)
  }

  private def heartBeat(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    timeout: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Start =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        ctx.setReceiveTimeout(timeout, NotAlive)
        Behaviors.same

      case Alive =>
        ctx.log.info(s"Provider[$index] alive")
        Behaviors.same

      case NotAlive =>
        ctx.log.info(s"Provider[$index] not alive")
        balancer ! LoadBalancer.ProviderDown(0)
        Behaviors.same
    }
  }
}
