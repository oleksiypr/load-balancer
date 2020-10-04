package op.op.assignment.iptiq.provider

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.balancer.{HeartBeat, LoadBalancer}


object Provider {

  type SelfRef = ActorRef[Message]

  sealed trait Message
  final case class Get(requester: ActorRef[String], balancer: LoadBalancer.SelfRef) extends Message
  final case class Check(replyTo: HeartBeat.SelfRef, acknowledge: HeartBeat.Message) extends Message

  private[this] val rnd       = scala.util.Random
  private[this] val threshold = 0.0

  def available(): Boolean = rnd.nextDouble() >= threshold

  def provider(id: String): Behavior[Message] = Behaviors.receiveMessage {
    case Get(requester, balancer) =>
      balancer ! LoadBalancer.Response(id, requester)
      Behaviors.same

    case Check(replyTo, ack) /*if available()*/ =>
      replyTo ! ack
      Behaviors.same

    //case Check(_, _) => Behaviors.same
  }
}

