package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.provider.Provider

object LoadBalancer {

  sealed trait Message
  final case class Register(providerRefs: Vector[ActorRef[Provider.Get]]) extends Message
  final case class Request(replyTo: ActorRef[String]) extends Message
  final case class Response(id: String, requester: ActorRef[String]) extends Message
  final case class ProviderUp(index: Int) extends Message
  final case class ProviderDown(index: Int) extends Message
  final case class Exclude(index: Int) extends Message
  final case class Include(index: Int) extends Message

  type Max    = Int
  type Index  = Int
  type Next   = Int

  type BalanceStrategy = Max => Index => Next

  def roundRobin(n: Int)(i: Int): Int =  (i + 1) % n

  def idle(
    max: Int,
    strategy: BalanceStrategy = roundRobin
  ): Behavior[Message] = Behaviors.setup[Message] { ctx =>
    Behaviors.receiveMessage[Message] {

      case Register(providerRefs) =>
        val refs = providerRefs.take(max)
        val providers = State(refs.map(ProviderState(_, Unavailable)))
        refs.foreach(_ => ctx.spawnAnonymous(HeartBeat.checker(ctx.self)))
        balancer(providers, strategy)(current = 0)

      case _ =>
        Behaviors.same
    }
  }

  def balancer(
    providers: State = State(Vector.empty),
    strategy: BalanceStrategy
  )(
    current: Int,
    next: Index => Next = strategy(providers.size)
  ): Behavior[Message] = Behaviors.setup[Message] { _ =>
    Behaviors.receiveMessage[Message] {

      case Request(replyTo) =>
        providers(current) match {
          case Some(p) =>
            p.providerRef ! Provider.Get(replyTo)
            balancer(providers, strategy)(next(current))
          case None    =>
            replyTo ! "No providers available"
            Behaviors.same
        }

      case Response(id, requester) =>
        requester ! id
        Behaviors.same

      case ProviderUp(index) =>
        balancer(providers.up(index), strategy)(current)

      case ProviderDown(index) =>
        balancer(providers.down(index), strategy)(current)

      case Exclude(index) =>
        balancer(providers.exclude(index), strategy)(current)

      case Include(index) =>
        balancer(providers.include(index), strategy)(current)

      case Register(_) => Behaviors.same
    }
  }
}
