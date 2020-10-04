package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PreRestart}
import op.op.assignment.iptiq.provider.Provider
import scala.concurrent.duration._

object HeartBeat {

  import Behaviors.{receiveMessage, receiveSignal, setup}

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
      starting(index, balancer, provider, timeout)
  }

  private def starting(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    timeout: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Start =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        ctx.setReceiveTimeout(timeout, NotAlive)
        heartBeat(index, balancer, provider)

      case other =>
        ctx.log.error(s"Start expected in this state, but received: $other")
        Behaviors.same
    }
  }

  private[balancer] def heartBeat(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        ctx.log.info(s"Provider[$index] alive")
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        Behaviors.same

      case NotAlive =>
        ctx.log.info(s"Provider[$index] not alive")
        balancer ! LoadBalancer.ProviderDown(0)
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        notAlive(index, balancer, provider)

      case other =>
        ctx.log.error(s"Alive or NotAlive expected in this state, but received: $other")
        Behaviors.same
    }
  }

  private[balancer] def notAlive(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        semiAlive(index, balancer, provider)

      case NotAlive =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        Behaviors.same

      case other =>
        ctx.log.error(s"Alive or NotAlive expected in this state, but received: $other")
        Behaviors.same
    }
  }

  private def semiAlive(
    index: LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        balancer ! LoadBalancer.ProviderUp(0)
        heartBeat(index, balancer, provider)

      case NotAlive =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        notAlive(index, balancer, provider)

      case other =>
        ctx.log.error(s"Alive or NotAlive expected in this state, but received: $other")
        Behaviors.same
    }
  }
}
