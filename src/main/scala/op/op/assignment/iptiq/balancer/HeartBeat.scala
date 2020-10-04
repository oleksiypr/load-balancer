package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.provider.Provider
import scala.concurrent.duration._

object HeartBeat {

  type SelfRef = ActorRef[Message]

  import Behaviors.{receiveMessage, setup}

  sealed trait Message
  case object Start extends Message
  case object Alive extends Message
  case object NotAlive extends Message

  def default(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef
  ): Behavior[Message] = checker(index, balancer, provider, repeat = 1.second)

  def checker(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    repeat: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Start =>
        ctx.log.info(s"HeartBeat started for provider[$index]")
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        val timeout = repeat + repeat/10
        ctx.setReceiveTimeout(timeout, NotAlive)
        heartBeat(index, balancer, provider, repeat)

      case other =>
        ctx.log.error(s"Start expected in this state, but received: $other")
        Behaviors.same
    }
  }

  private[balancer] def heartBeat(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    repeat: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        ctx.log.info(s"Provider[$index] alive")
        balancer ! LoadBalancer.ProviderUp(index)
        ctx.scheduleOnce(repeat, provider, Provider.Check(replyTo = ctx.self, Alive))
        Behaviors.same

      case NotAlive =>
        ctx.log.info(s"Provider[$index] not alive")
        balancer ! LoadBalancer.ProviderDown(index)
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        notAlive(index, balancer, provider, repeat)

      case other =>
        ctx.log.error(s"Alive or NotAlive expected in this state, but received: $other")
        Behaviors.same
    }
  }

  private[balancer] def notAlive(
    index:  LoadBalancer.Index,
    balancer: LoadBalancer.SelfRef,
    provider: Provider.SelfRef,
    repeat: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        ctx.scheduleOnce(repeat, provider, Provider.Check(replyTo = ctx.self, Alive))
        semiAlive(index, balancer, provider, repeat)

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
    provider: Provider.SelfRef,
    repeat: FiniteDuration
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Alive =>
        balancer ! LoadBalancer.ProviderUp(index)
        ctx.scheduleOnce(repeat, provider, Provider.Check(replyTo = ctx.self, Alive))
        heartBeat(index, balancer, provider, repeat)

      case NotAlive =>
        provider ! Provider.Check(replyTo = ctx.self, Alive)
        notAlive(index, balancer, provider, repeat)

      case other =>
        ctx.log.error(s"Alive or NotAlive expected in this state, but received: $other")
        Behaviors.same
    }
  }
}
