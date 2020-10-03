package op.op.assignment.iptiq.provider

import akka.actor.typed.ActorRef


object Provider {

  sealed trait Message
  final case class Get(replyTo: ActorRef[String]) extends Message
}
