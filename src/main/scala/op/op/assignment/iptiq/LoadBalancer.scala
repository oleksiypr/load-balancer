package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.Provider.Get

object LoadBalancer {

  sealed trait ProviderStatus
  case object Available extends ProviderStatus
  case object Unavailable extends ProviderStatus

  final case class ProviderState(providerRef: ActorRef[Provider.Get], status: ProviderStatus)

  final case class Providers(states: Vector[ProviderState]) {
    private[this] val available = states.filter(_.status == Available)

    val size: Int = available.size

    def apply(i: Int): Option[ProviderState] = {
      if (i < 0 || i >= size) None
      else Some(available(i))
    }

    def providerUp(i: Int)  : Providers = statusUpdated(i, Available)
    def providerDown(i: Int): Providers = statusUpdated(i, Unavailable)

    private def statusUpdated(i: Int, s: ProviderStatus): Providers =
      if (i < 0 || i >= states.size) this
      else {
        val state   = states(i).copy(status = s)
        val updated = states.updated(i, state)
        copy(updated)
      }
  }

  sealed trait Message
  final case class Register(providerRefs: Vector[ActorRef[Provider.Get]]) extends Message
  final case class Request(replyTo: ActorRef[String]) extends Message
  final case class Response(id: String, requester: ActorRef[String]) extends Message
  final case class ProviderUp(index: Int) extends Message
  final case class ProviderDown(index: Int) extends Message

  type BalanceStrategy = Int => Int

  def roundRobin(n: Int)(i: Int): Int =  (i + 1) % n

  def idle(max: Int): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.receiveMessage[Message] {
        case Register(providerRefs) =>
          val ps = providerRefs.take(max)
          val providers = Providers(ps.map(ProviderState(_, Available)))
          ps.foreach(_ => ctx.spawnAnonymous(HeartBeat.checker(ctx.self)))
          balancer(providers)(current = 0)
        case _ =>
          Behaviors.same
      }
    }

  def balancer(
    providers: Providers = Providers(Vector.empty)
  )(current: Int,
    next: BalanceStrategy = roundRobin(providers.size)
  ): Behavior[Message] =
    Behaviors.setup[Message] { _ =>
      Behaviors.receiveMessage[Message] {

        case Request(replyTo) =>
          providers(current) match {
            case Some(p) =>
              p.providerRef ! Get(replyTo)
              balancer(providers)(next(current))
            case None    =>
              replyTo ! "No providers available"
              Behaviors.same
          }

        case Response(id, requester) =>
          requester ! id
          Behaviors.same

        case ProviderUp(index) =>
          balancer(providers.providerUp(index))(current)

        case ProviderDown(index) =>
          balancer(providers.providerDown(index))(current)

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
