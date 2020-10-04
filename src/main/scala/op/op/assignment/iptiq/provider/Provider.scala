package op.op.assignment.iptiq.provider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.balancer.LoadBalancer


object Provider {

  type SelfRef = ActorRef[Message]

  sealed trait Message
  final case class Get(requester: ActorRef[String], balancer: LoadBalancer.SelfRef) extends Message
  final case class Check(replyTo: ActorRef[_], acknowledge: Any) extends Message

  def available(): Boolean = ???

  def provider(id: String): Behavior[Message] = Behaviors.receiveMessage {
    case Get(replyTo, _) =>
      ???
    case Check(replyTo, ack) if available() => ???
    case Check(_, _) => Behaviors.same
  }
}

