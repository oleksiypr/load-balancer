package op.op.assignment.iptiq.balancer

import akka.actor.typed.ActorRef
import LoadBalancer._
import op.op.assignment.iptiq.provider.Provider

private[balancer] sealed trait Status
private[balancer] case object Available extends Status
private[balancer] case object Unavailable extends Status
private[balancer] case object Excluded extends Status

private[balancer] object Status {

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

private[balancer] final case class ProviderState(providerRef: ActorRef[Provider.Get], status: Status)

private[balancer] final case class State(providers: Vector[ProviderState]) {

  private[this] val available = providers.filter(_.status == Available)

  val size: Int = available.size

  def apply(i: Index): Option[ProviderState] = {
    if (i < 0 || i >= size) None
    else Some(available(i))
  }

  def up  (i: Index): State = updated(i, Status.Up)
  def down(i: Index): State = updated(i, Status.Down)

  def exclude(i: Index): State = updated(i, Status.Off)
  def include(i: Index): State = updated(i, Status.On)

  private def updated(i: Int, e: Status.Event): State =
    if (i < 0 || i >= providers.size) this
    else {
      val provider = providers(i)
      val updated  = Status.transition(provider.status, e)
      if (updated == provider.status) this
      else copy(providers.updated(i, provider.copy(status = updated)))
    }
}
