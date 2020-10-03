package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.Provider.Get

object LoadBalancer {

  private[iptiq] sealed trait Status
  private[iptiq] case object Available extends Status
  private[iptiq] case object Unavailable extends Status
  private[iptiq] case object Excluded extends Status

  private[iptiq] object Status {

    sealed trait Event
    case object Up extends Event
    case object Down extends Event
    case object Off extends Event
    case object On extends Event

    def transition(s: Status, e: Event): Status =
      (s, e) match {
        case (Excluded, On) => Unavailable
        case (Excluded, _)  => Excluded
        case (_, Off)       => Excluded
        case (_, Up)        => Available
        case (_, Down)      => Unavailable
        case (_, On)        => s
      }
  }

  private[iptiq] final case class State(providerRef: ActorRef[Provider.Get], status: Status)

  private[iptiq] final case class Providers(providers: Vector[State]) {

    private[this] val available = providers.filter(_.status == Available)

    val size: Int = available.size

    def apply(i: Int): Option[State] = {
      if (i < 0 || i >= size) None
      else Some(available(i))
    }

    def up(i: Int)  : Providers = updated(i, Status.Up)
    def down(i: Int): Providers = updated(i, Status.Down)

    def exclude(i: Index): Providers = updated(i, Status.Off)

    private def updated(i: Int, e: Status.Event): Providers =
      if (i < 0 || i >= providers.size) this
      else {
        val provider = providers(i)
        val updated  = Status.transition(provider.status, e)
        if (updated == provider.status) this
        else copy(providers.updated(i, provider.copy(status = updated)))
      }
  }

  sealed trait Message
  final case class Register(providerRefs: Vector[ActorRef[Provider.Get]]) extends Message
  final case class Request(replyTo: ActorRef[String]) extends Message
  final case class Response(id: String, requester: ActorRef[String]) extends Message
  final case class ProviderUp(index: Int) extends Message
  final case class ProviderDown(index: Int) extends Message
  final case class Exclude(index: Int) extends Message

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
        val providers = Providers(refs.map(State(_, Unavailable)))
        refs.foreach(_ => ctx.spawnAnonymous(HeartBeat.checker(ctx.self)))
        balancer(providers, strategy)(current = 0)

      case _ =>
        Behaviors.same
    }
  }

  def balancer(
    providers: Providers = Providers(Vector.empty),
    strategy: BalanceStrategy
  )(
    current: Int,
    next: Index => Next = strategy(providers.size)
  ): Behavior[Message] = Behaviors.setup[Message] { _ =>

    Behaviors.receiveMessage[Message] {
      case Request(replyTo) =>
        providers(current) match {
          case Some(p) =>
            p.providerRef ! Get(replyTo)
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

      case Register(_) => Behaviors.same
    }
  }
}

object Provider {

  sealed trait Message
  final case class Get(replyTo: ActorRef[String]) extends Message
}

object HeartBeat {

  sealed trait Message
  case object Check extends Message

  def checker(
    balancer: ActorRef[LoadBalancer.Message]
  ): Behavior[Message] =
    Behaviors.setup[Message] { _ =>
      Behaviors.receiveMessage[Message](_ => Behaviors.same)
    }

}
