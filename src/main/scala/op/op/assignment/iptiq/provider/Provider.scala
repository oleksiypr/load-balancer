package op.op.assignment.iptiq.provider

import akka.actor.typed.ActorRef


object Provider {

  type SelfRef = ActorRef[Message]

  sealed trait Message
  final case class Get(replyTo: ActorRef[String]) extends Message
  final case class Check(replyTo: ActorRef[_], acknowledge: Any) extends Message
}

